package com.catatuang.app.ai

import com.catatuang.app.data.Category
import com.catatuang.app.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class CategoryDecision(
    val category: Category,
    /** 0..1, as reported by Jev. */
    val confidence: Double,
    /** Probability per category name, for showing runner-ups. */
    val probabilities: Map<String, Double>,
    val costUsd: Double?,
)

class JevException(message: String) : Exception(message)

/**
 * Asks Jev (TypeSafe's decision model on OpenRouter) which category a payment belongs to.
 *
 * Jev doesn't write text; it answers typed questions. We send the receipt facts as `state`
 * and one `choice` question whose options are the user's own categories, described by
 * their hints. See https://openrouter.ai/docs/guides/community/jev-tutorial
 */
class JevCategorizer(private val settings: Settings) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    val isConfigured: Boolean get() = settings.openRouterKey.isNotBlank()

    suspend fun categorize(
        categories: List<Category>,
        amount: Long?,
        counterparty: String?,
        note: String?,
        kind: String?,
        source: String?,
        receiptText: String?,
    ): CategoryDecision = withContext(Dispatchers.IO) {
        if (!isConfigured) throw JevException("Add your OpenRouter API key in Settings first.")
        require(categories.isNotEmpty()) { "No categories to choose from" }

        val state = JSONObject().apply {
            put("task", "A personal expense from an Indonesian banking or e-wallet app. Decide which spending category it belongs to.")
            counterparty?.let { put("paid_to", it) }
            amount?.let { put("amount_idr", it) }
            note?.let { put("note_written_by_user", it) }
            kind?.let { put("transaction_type", it) }
            source?.let { put("app", it) }
            // A little raw OCR text helps with merchant names the parser didn't catch.
            receiptText?.takeIf { it.isNotBlank() }?.let { put("receipt_text", it.take(1200)) }
        }

        val criteria = JSONObject()
        categories.forEach { criteria.put(it.name, it.hint.ifBlank { it.name }) }

        val body = JSONObject().apply {
            put("model", settings.jevModel)
            put("state", state)
            put(
                "questions",
                JSONObject().put(
                    "category",
                    JSONObject()
                        .put("type", "choice")
                        .put("instructions", "Which spending category should this payment be recorded under?")
                        .put("criteria", criteria),
                ),
            )
        }

        val request = Request.Builder()
            .url(ENDPOINT)
            .header("Authorization", "Bearer ${settings.openRouterKey}")
            .header("X-Title", "Catat Uang")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val msg = runCatching { JSONObject(text).getJSONObject("error").getString("message") }.getOrNull()
                throw JevException("Jev request failed (${response.code}): ${msg ?: text.take(200)}")
            }
            val json = JSONObject(text)
            val answer = json.getJSONObject("answers").getJSONObject("category")
            val chosen = answer.getString("choice")
            val probs = answer.optJSONObject("probabilities")?.let { p ->
                p.keys().asSequence().associateWith { p.optDouble(it, 0.0) }
            }.orEmpty()
            val category = categories.firstOrNull { it.name == chosen }
                ?: throw JevException("Jev picked an unknown category: $chosen")
            CategoryDecision(
                category = category,
                confidence = answer.optDouble("confidence", probs[chosen] ?: 0.0),
                probabilities = probs,
                costUsd = json.optJSONObject("usage")?.optDouble("cost")?.takeUnless { it.isNaN() },
            )
        }
    }

    companion object {
        const val ENDPOINT = "https://openrouter.ai/api/alpha/decisions"
    }
}
