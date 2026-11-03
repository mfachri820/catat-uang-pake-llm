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
        incoming: Boolean = false,
    ): CategoryDecision = withContext(Dispatchers.IO) {
        if (!isConfigured) throw JevException("Add your OpenRouter API key in Settings first.")
        require(categories.isNotEmpty()) { "No categories to choose from" }

        val state = JSONObject().apply {
            put(
                "task",
                if (incoming) "Money received, from an Indonesian banking or e-wallet app. Decide what kind of income it is."
                else "Money paid out from an Indonesian banking or e-wallet app: spending, or putting money into an investment. Decide its category.",
            )
            counterparty?.let { put(if (incoming) "received_from" else "paid_to", it) }
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
                        .put("instructions", "Which category should this transaction be recorded under?")
                        .put("criteria", criteria),
                ),
            )
        }

        val json = post(body)
        val cost = json.optJSONObject("usage")?.optDouble("cost")?.takeUnless { it.isNaN() }
        decisionFrom(json.getJSONObject("answers").getJSONObject("category"), categories, cost)
            ?: throw JevException("Jev picked an unknown category")
    }

    data class BatchItem(
        val key: String,
        val description: String,
        val amount: Long,
        val counterparty: String?,
        val date: String,
    )

    /**
     * Categorises many statement rows. Each row becomes its own `choice` question in a request,
     * [chunk] rows per request, so a month of transactions takes a handful of calls.
     * [onProgress] gets the number of rows done so far.
     */
    suspend fun categorizeBatch(
        categories: List<Category>,
        items: List<BatchItem>,
        incoming: Boolean = false,
        chunk: Int = 8,
        onProgress: (Int) -> Unit = {},
    ): Map<String, CategoryDecision> = withContext(Dispatchers.IO) {
        if (!isConfigured) throw JevException("Add your OpenRouter API key in Settings first.")
        val criteria = JSONObject()
        categories.forEach { criteria.put(it.name, it.hint.ifBlank { it.name }) }
        val out = mutableMapOf<String, CategoryDecision>()
        var done = 0
        for (group in items.chunked(chunk)) {
            val txns = JSONObject()
            val questions = JSONObject()
            group.forEach { item ->
                txns.put(item.key, JSONObject().apply {
                    put("bank_description", item.description)
                    put("amount_idr", item.amount)
                    put("date", item.date)
                    item.counterparty?.let { put(if (incoming) "probably_from" else "probably_paid_to", it) }
                })
                questions.put(
                    item.key,
                    JSONObject()
                        .put("type", "choice")
                        .put("instructions", "Which category fits transaction ${item.key} in transactions.${item.key}?")
                        .put("criteria", criteria),
                )
            }
            val state = JSONObject()
                .put("task", if (incoming) INCOMING_TASK else OUTGOING_TASK)
                .put("transactions", txns)
            val json = post(JSONObject().put("model", settings.jevModel).put("state", state).put("questions", questions))
            val answers = json.getJSONObject("answers")
            group.forEach { item ->
                answers.optJSONObject(item.key)?.let { decisionFrom(it, categories, null) }?.let { out[item.key] = it }
            }
            done += group.size
            onProgress(done)
        }
        out
    }

    private fun decisionFrom(answer: JSONObject, categories: List<Category>, cost: Double?): CategoryDecision? {
        val chosen = answer.optString("choice")
        val probs = answer.optJSONObject("probabilities")?.let { p ->
            p.keys().asSequence().associateWith { p.optDouble(it, 0.0) }
        }.orEmpty()
        val category = categories.firstOrNull { it.name == chosen } ?: return null
        return CategoryDecision(
            category = category,
            confidence = answer.optDouble("confidence", probs[chosen] ?: 0.0),
            probabilities = probs,
            costUsd = cost,
        )
    }

    private fun post(body: JSONObject): JSONObject {
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
            return JSONObject(text)
        }
    }

    companion object {
        private const val OUTGOING_TASK =
            "Money-out lines from an Indonesian bank statement (rekening koran), e.g. BRI. " +
                "Descriptions use bank codes: NBMB/ESB/BI-FAST = transfer to the person after 'TO'; " +
                "QRIS = paying a shop by QR; BRIVA = virtual account bill (e-commerce, PLN, BPJS, school, investment apps). " +
                "Some lines are investments (money into stocks, mutual funds, gold, crypto, deposits), not spending. " +
                "Decide the category of each transaction."
        private const val INCOMING_TASK =
            "Money-in lines from an Indonesian bank statement (rekening koran), e.g. BRI. " +
                "Descriptions use bank codes: NBMB/ESB/BI-FAST = transfer from the person named; 'GAJI'/'PAYROLL' = salary. " +
                "Decide what kind of income each transaction is."
        const val ENDPOINT = "https://openrouter.ai/api/alpha/decisions"
    }
}
