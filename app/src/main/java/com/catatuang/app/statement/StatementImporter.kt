package com.catatuang.app.statement

import android.content.Context
import android.net.Uri
import com.catatuang.app.ai.JevCategorizer
import com.catatuang.app.data.Category
import com.catatuang.app.data.Repository
import com.catatuang.app.data.Txn
import com.catatuang.app.data.TxnType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ImportRow(
    val key: String,
    val timestamp: Long,
    val description: String,
    val counterparty: String,
    val amount: Long,
    /** Money in (kredit) rather than money out (debet). */
    val incoming: Boolean,
    val selected: Boolean,
    val categoryId: Long? = null,
    /** "rule", "history", "jev", "manual" or null while undecided. */
    val categorisedBy: String? = null,
    val confidence: Double? = null,
    val duplicateNote: String? = null,
    val reference: String,
)

data class StatementImportState(
    val busy: String? = null,
    /** 0..1 while Jev is working through the rows. */
    val progress: Float? = null,
    val rows: List<ImportRow> = emptyList(),
    val error: String? = null,
    val notice: String? = null,
    val needsPassword: Boolean = false,
    val wrongPassword: Boolean = false,
    val rawText: String? = null,
)

/** Drives the "Import rekening koran" flow. */
class StatementImporter(
    private val context: Context,
    private val repo: Repository,
    private val jev: JevCategorizer,
    private val scope: CoroutineScope,
) {
    val state = MutableStateFlow(StatementImportState())
    private var uri: Uri? = null

    fun start(uri: Uri) {
        this.uri = uri
        state.value = StatementImportState(busy = "Reading statement…")
        load(null)
    }

    fun submitPassword(password: String) {
        state.update { it.copy(needsPassword = false, busy = "Reading statement…") }
        load(password)
    }

    /**
     * For PDFs arriving from the share sheet: returns true and starts the statement flow if the
     * PDF looks like a statement, false if it's probably a single receipt.
     */
    suspend fun tryStatement(uri: Uri): Boolean {
        val text = try {
            StatementReader.readText(context, uri)
        } catch (e: PasswordRequiredException) {
            this.uri = uri
            state.value = StatementImportState(needsPassword = true)
            return true
        } catch (e: Exception) {
            return false
        }
        if (StatementParser.parse(text).rows.size < 2) return false
        this.uri = uri
        state.value = StatementImportState(busy = "Reading statement…")
        scope.launch { process(text) }
        return true
    }

    private fun load(password: String?) = scope.launch {
        val u = uri ?: return@launch
        val text = try {
            StatementReader.readText(context, u, password)
        } catch (e: PasswordRequiredException) {
            state.update { it.copy(busy = null, needsPassword = true, wrongPassword = e.wrongPassword) }
            return@launch
        } catch (e: Exception) {
            state.update { it.copy(busy = null, error = "Couldn't open the PDF: ${e.message}") }
            return@launch
        }
        process(text)
    }

    private suspend fun process(text: String) {
        val parsed = StatementParser.parse(text)
        if (parsed.rows.isEmpty()) {
            state.update {
                it.copy(
                    busy = null, rawText = text,
                    error = "No transactions found in this file. If it is a rekening koran, tap " +
                        "\"Show extracted text\" and send a few lines to whoever maintains the app so the parser can learn the format.",
                )
            }
            return
        }

        val stamp = SimpleDateFormat("yyyyMMddHHmm", Locale.US)
        val rows = parsed.rows.mapIndexed { i, r ->
            val incoming = !r.isDebit
            val ref = "stmt:${if (incoming) "in" else "out"}:${stamp.format(Date(r.timestamp))}:${r.amount}:${r.description.hashCode()}"
            val types = if (incoming) listOf(TxnType.INCOME) else TxnType.OUTGOING
            val duplicate = repo.txns.findByReference(ref)?.let { "Already imported" }
                ?: repo.txns.findSimilar(r.amount, types, r.timestamp - DAY, r.timestamp + DAY)?.let { t ->
                    "Probably already recorded (${t.source}: ${t.counterparty.ifBlank { "same amount" }})"
                }
            ImportRow(
                key = "t${i + 1}", timestamp = r.timestamp, description = r.description,
                counterparty = StatementParser.guessCounterparty(r.description, incoming), amount = r.amount,
                incoming = incoming, selected = duplicate == null, duplicateNote = duplicate, reference = ref,
            )
        }
        state.update { it.copy(busy = "Choosing categories…", rows = rows, rawText = text) }
        categorise()
    }

    private suspend fun categorise() {
        // 1) Fixed rules: cash, fees, top-ups, investment platforms, salary, interest.
        val ruled = state.value.rows.map { row ->
            val rule = (if (row.incoming) StatementRules.matchIncoming(row.description) else StatementRules.matchOutgoing(row.description))
                ?: return@map row
            row.copy(categoryId = repo.ensureCategory(rule.name, rule.icon, rule.hint, rule.kind), categorisedBy = "rule")
        }
        // 2) Same person or merchant as before, in the same direction? Reuse that category.
        val withHistory = ruled.map { row ->
            if (row.categoryId != null) return@map row
            val types = if (row.incoming) listOf(TxnType.INCOME) else TxnType.OUTGOING
            val known = repo.txns.lastCategoryFor(row.counterparty, types)
            if (known != null) row.copy(categoryId = known, categorisedBy = "history") else row
        }
        state.update { it.copy(rows = withHistory) }

        // 3) Jev for the rest (skipping likely duplicates to save calls).
        val todo = withHistory.filter { it.categoryId == null && it.duplicateNote == null }
        if (todo.isEmpty()) { state.update { it.copy(busy = null) }; return }
        if (!jev.isConfigured) {
            state.update {
                it.copy(busy = null, notice = "${todo.size} rows need a category. Add an OpenRouter key in Settings to let Jev pick them.")
            }
            return
        }
        val outCats = repo.categories.getByKinds(TxnType.OUTGOING)
        val inCats = repo.categories.getByKinds(listOf(TxnType.INCOME))
        val dateFmt = SimpleDateFormat("EEE d MMM yyyy HH:mm", Locale.forLanguageTag("id-ID"))
        fun items(rows: List<ImportRow>) = rows.map {
            JevCategorizer.BatchItem(it.key, it.description, it.amount, it.counterparty, dateFmt.format(Date(it.timestamp)))
        }
        val (inRows, outRows) = todo.partition { it.incoming }
        state.update { it.copy(progress = 0f) }
        runCatching {
            val progress = { done: Int, offset: Int -> state.update { s -> s.copy(progress = (offset + done).toFloat() / todo.size) } }
            val out = if (outRows.isEmpty() || outCats.isEmpty()) emptyMap()
            else jev.categorizeBatch(outCats, items(outRows), incoming = false, onProgress = { progress(it, 0) })
            val inc = if (inRows.isEmpty() || inCats.isEmpty()) emptyMap()
            else jev.categorizeBatch(inCats, items(inRows), incoming = true, onProgress = { progress(it, outRows.size) })
            out + inc
        }.onSuccess { decisions ->
            state.update { s ->
                s.copy(
                    busy = null, progress = null,
                    rows = s.rows.map { row ->
                        val d = decisions[row.key] ?: return@map row
                        if (row.categorisedBy == "manual") row
                        else row.copy(categoryId = d.category.id, categorisedBy = "jev", confidence = d.confidence)
                    },
                )
            }
        }.onFailure { e ->
            state.update { it.copy(busy = null, progress = null, notice = e.message) }
        }
    }

    fun toggle(key: String) = state.update { s ->
        s.copy(rows = s.rows.map { if (it.key == key) it.copy(selected = !it.selected) else it })
    }

    /** Tick/untick everything, or only money in / money out when [incoming] is set. */
    fun setAll(selected: Boolean, incoming: Boolean? = null) = state.update { s ->
        s.copy(
            rows = s.rows.map {
                val inScope = incoming == null || it.incoming == incoming
                if (inScope && (it.duplicateNote == null || !selected)) it.copy(selected = selected) else it
            },
        )
    }

    fun setCategory(key: String, categoryId: Long) = state.update { s ->
        s.copy(rows = s.rows.map { if (it.key == key) it.copy(categoryId = categoryId, categorisedBy = "manual", confidence = null) else it })
    }

    /** Saves the ticked rows. Returns how many were saved. */
    suspend fun confirm(categories: List<Category>): Int {
        val kindOf = categories.associate { it.id to it.kind }
        val chosen = state.value.rows.filter { it.selected }
        repo.saveAll(
            chosen.map {
                val type = when {
                    it.incoming -> TxnType.INCOME
                    kindOf[it.categoryId] == TxnType.INVEST -> TxnType.INVEST
                    else -> TxnType.EXPENSE
                }
                Txn(
                    amount = it.amount, categoryId = it.categoryId, counterparty = it.counterparty,
                    note = it.description, timestamp = it.timestamp, source = SOURCE,
                    reference = it.reference, categorisedBy = it.categorisedBy ?: "manual", type = type,
                )
            },
        )
        state.value = StatementImportState()
        return chosen.size
    }

    companion object {
        const val SOURCE = "Rekening koran"
        private const val DAY = 24 * 60 * 60 * 1000L
    }
}
