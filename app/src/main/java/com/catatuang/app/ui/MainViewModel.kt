package com.catatuang.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.catatuang.app.CatatUangApp
import com.catatuang.app.data.Category
import com.catatuang.app.data.CategoryTotal
import com.catatuang.app.data.Txn
import com.catatuang.app.data.TxnWithCategory
import com.catatuang.app.data.monthRange
import com.catatuang.app.receipt.ReceiptOcr
import com.catatuang.app.receipt.ReceiptParser
import com.catatuang.app.statement.StatementImporter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Home : Screen
    data object Editor : Screen
    data object Categories : Screen
    data object Settings : Screen
    data object StatementImport : Screen
}

/** State of the add / edit / review-import form. */
data class EditorState(
    val id: Long = 0,
    val amountText: String = "",
    val categoryId: Long? = null,
    val counterparty: String = "",
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val source: String = "Manual",
    val reference: String? = null,
    val categorisedBy: String = "manual",
    // Import feedback
    val fromReceipt: Boolean = false,
    val busy: String? = null,
    val aiMessage: String? = null,
    val aiUncertain: Boolean = false,
    val duplicateWarning: String? = null,
    val error: String? = null,
    val rawText: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val container = app as CatatUangApp
    private val repo = container.repository
    val settings = container.settings
    private val jev = container.categorizer

    val screen = MutableStateFlow<Screen>(Screen.Home)
    val monthOffset = MutableStateFlow(0)

    val categories: StateFlow<List<Category>> = repo.categories.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val monthTxns: StateFlow<List<TxnWithCategory>> = monthOffset.flatMapLatest { off ->
        val (from, to) = monthRange(offset = off)
        repo.txns.observeBetween(from, to)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val monthTotals: StateFlow<List<CategoryTotal>> = monthOffset.flatMapLatest { off ->
        val (from, to) = monthRange(offset = off)
        repo.txns.observeTotalsBetween(from, to)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val editor = MutableStateFlow(EditorState())

    val statement = StatementImporter(app, repo, jev, viewModelScope)

    /** One-off message for a snackbar, e.g. "Imported 34 expenses". */
    val toast = MutableStateFlow<String?>(null)

    fun navigate(to: Screen) { screen.value = to }

    fun back(): Boolean {
        if (screen.value == Screen.Home) return false
        screen.value = Screen.Home
        return true
    }

    // ------------------------------------------------------------ manual entry

    fun newExpense() {
        editor.value = EditorState()
        screen.value = Screen.Editor
    }

    fun edit(id: Long) = viewModelScope.launch {
        val t = repo.txns.get(id) ?: return@launch
        editor.value = EditorState(
            id = t.id, amountText = t.amount.toString(), categoryId = t.categoryId,
            counterparty = t.counterparty, note = t.note, timestamp = t.timestamp,
            source = t.source, reference = t.reference, categorisedBy = t.categorisedBy,
        )
        screen.value = Screen.Editor
    }

    fun updateEditor(f: (EditorState) -> EditorState) = editor.update(f)

    fun pickCategory(id: Long) = editor.update {
        it.copy(categoryId = id, categorisedBy = "manual", aiUncertain = false)
    }

    fun save() = viewModelScope.launch {
        val e = editor.value
        val amount = e.amountText.filter { it.isDigit() }.toLongOrNull()
        if (amount == null || amount <= 0) {
            editor.update { it.copy(error = "Enter an amount") }
            return@launch
        }
        repo.save(
            Txn(
                id = e.id, amount = amount, categoryId = e.categoryId,
                counterparty = e.counterparty.trim(), note = e.note.trim(), timestamp = e.timestamp,
                source = e.source, reference = e.reference, categorisedBy = e.categorisedBy,
            ),
        )
        screen.value = Screen.Home
    }

    fun delete() = viewModelScope.launch {
        val id = editor.value.id
        if (id != 0L) repo.delete(id)
        screen.value = Screen.Home
    }

    // ------------------------------------------------------------ receipt import

    /** Screenshot shared from BRImo (or picked from the gallery) → pre-filled review form. */
    fun importReceipt(uri: Uri, mimeType: String? = null) {
        editor.value = EditorState(fromReceipt = true, busy = "Reading receipt…")
        screen.value = Screen.Editor
        viewModelScope.launch {
            val text = runCatching { ReceiptOcr.read(getApplication(), uri, mimeType) }.getOrElse { e ->
                editor.update { it.copy(busy = null, error = "Couldn't read the receipt: ${e.message}") }
                return@launch
            }
            fillFromText(text)
        }
    }

    /** Some apps share the receipt as text instead of an image. */
    fun importReceiptText(text: String) {
        editor.value = EditorState(fromReceipt = true, busy = "Reading receipt…")
        screen.value = Screen.Editor
        viewModelScope.launch { fillFromText(text) }
    }

    private suspend fun fillFromText(text: String) {
        val p = ReceiptParser.parse(text)
        val dup = p.reference?.let { repo.txns.findByReference(it) }
        editor.update {
            it.copy(
                amountText = p.amount?.toString().orEmpty(),
                counterparty = p.counterparty.orEmpty(),
                note = p.note.orEmpty(),
                timestamp = p.timestamp ?: System.currentTimeMillis(),
                source = p.source,
                reference = p.reference,
                rawText = text,
                duplicateWarning = dup?.let { "This receipt (ref ${p.reference}) is already saved." },
                error = if (p.amount == null) "Couldn't find the amount — please type it in." else null,
                busy = "Choosing a category…",
            )
        }
        decideCategory(p.counterparty, p.amount, p.note, p.kind, p.source, text)
    }

    private suspend fun decideCategory(
        counterparty: String?, amount: Long?, note: String?, kind: String?, source: String?, text: String?,
    ) {
        val cats = repo.categories.getAll()

        // 1) Paid this person/merchant before? Reuse that category — instant and free.
        if (!counterparty.isNullOrBlank()) {
            val known = repo.txns.lastCategoryFor(counterparty)?.let { id -> cats.firstOrNull { it.id == id } }
            if (known != null) {
                editor.update {
                    it.copy(
                        categoryId = known.id, categorisedBy = "history", busy = null,
                        aiMessage = "Same category as your last payment to $counterparty.",
                    )
                }
                return
            }
        }

        // 2) Ask Jev.
        if (!jev.isConfigured) {
            editor.update {
                it.copy(busy = null, aiMessage = "Add an OpenRouter key in Settings to auto-pick categories.")
            }
            return
        }
        runCatching { jev.categorize(cats, amount, counterparty, note, kind, source, text) }
            .onSuccess { d ->
                val pct = (d.confidence * 100).toInt()
                val uncertain = d.confidence < settings.confidenceThreshold
                val runnerUp = d.probabilities.entries.filter { it.key != d.category.name }
                    .maxByOrNull { it.value }?.takeIf { uncertain && it.value > 0.1 }
                editor.update {
                    it.copy(
                        categoryId = d.category.id, categorisedBy = "jev", busy = null, aiUncertain = uncertain,
                        aiMessage = buildString {
                            append("Jev picked ${d.category.emoji} ${d.category.name} ($pct% sure)")
                            if (runnerUp != null) append(" — maybe ${runnerUp.key}?")
                        },
                    )
                }
            }
            .onFailure { e -> editor.update { it.copy(busy = null, aiMessage = e.message, aiUncertain = true) } }
    }

    /** Re-run Jev on a manual entry, using what the user typed. */
    fun suggestCategory() = viewModelScope.launch {
        val e = editor.value
        editor.update { it.copy(busy = "Choosing a category…", aiMessage = null) }
        decideCategory(
            e.counterparty.ifBlank { null }, e.amountText.filter { it.isDigit() }.toLongOrNull(),
            e.note.ifBlank { null }, null, e.source, e.rawText,
        )
    }

    // ------------------------------------------------------------ rekening koran

    fun importStatement(uri: Uri) {
        statement.start(uri)
        screen.value = Screen.StatementImport
    }

    /** A PDF from the share sheet: a statement if it has many dated rows, otherwise a receipt. */
    fun importPdf(uri: Uri) {
        editor.value = EditorState(fromReceipt = true, busy = "Reading file…")
        screen.value = Screen.Editor
        viewModelScope.launch {
            if (statement.tryStatement(uri)) screen.value = Screen.StatementImport
            else importReceipt(uri, "application/pdf")
        }
    }

    fun confirmStatement() = viewModelScope.launch {
        val n = statement.confirm()
        toast.value = "Imported $n expenses"
        screen.value = Screen.Home
    }

    // ------------------------------------------------------------ categories & settings

    fun saveCategory(c: Category) = viewModelScope.launch {
        if (c.id == 0L) repo.categories.insert(c) else repo.categories.update(c)
    }

    fun deleteCategory(c: Category) = viewModelScope.launch {
        repo.categories.delete(c)
        repo.refreshWidget()
    }

    fun saveSettings(key: String, model: String, threshold: Float, budget: Long) {
        settings.openRouterKey = key
        settings.jevModel = model
        settings.confidenceThreshold = threshold
        settings.monthlyBudget = budget
        viewModelScope.launch { repo.refreshWidget() }
        screen.value = Screen.Home
    }
}
