package com.catatuang.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.catatuang.app.CatatUangApp
import com.catatuang.app.data.Category
import com.catatuang.app.data.CategoryTotal
import com.catatuang.app.data.Txn
import com.catatuang.app.data.TxnType
import com.catatuang.app.data.TxnWithCategory
import com.catatuang.app.data.TypeTotal
import com.catatuang.app.data.monthRange
import com.catatuang.app.receipt.ReceiptOcr
import com.catatuang.app.receipt.ReceiptParser
import com.catatuang.app.statement.StatementImporter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Home : Screen
    data object Details : Screen
    data object Editor : Screen
    data object Categories : Screen
    data object Settings : Screen
    data object StatementImport : Screen
}

/** State of the add / edit / review-import form. */
data class EditorState(
    val id: Long = 0,
    val type: String = TxnType.EXPENSE,
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

    // ------------------------------------------------------------ navigation

    val screen = MutableStateFlow<Screen>(Screen.Home)
    private val backStack = ArrayDeque<Screen>()

    fun navigate(to: Screen) {
        if (screen.value != to) backStack.addLast(screen.value)
        screen.value = to
    }

    fun back(): Boolean {
        if (screen.value == Screen.Home) return false
        screen.value = backStack.removeLastOrNull() ?: Screen.Home
        return true
    }

    /** Jump to a screen from outside (widget, share sheet) with Home underneath. */
    fun openFresh(to: Screen) {
        backStack.clear()
        if (to != Screen.Home) backStack.addLast(Screen.Home)
        screen.value = to
    }

    // ------------------------------------------------------------ month data

    val monthOffset = MutableStateFlow(0)
    /** Home list filter: null = everything, else one of [TxnType]. */
    val listFilter = MutableStateFlow<String?>(null)

    val categories: StateFlow<List<Category>> = repo.categories.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun <T> perMonth(initial: T, query: (Long, Long) -> Flow<T>): StateFlow<T> =
        monthOffset.flatMapLatest { off ->
            val (from, to) = monthRange(offset = off)
            query(from, to)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, initial)

    val monthTxns: StateFlow<List<TxnWithCategory>> = perMonth(emptyList()) { f, t -> repo.txns.observeBetween(f, t) }
    val typeTotals: StateFlow<List<TypeTotal>> = perMonth(emptyList()) { f, t -> repo.txns.observeTypeTotals(f, t) }
    val expenseTotals: StateFlow<List<CategoryTotal>> = perMonth(emptyList()) { f, t -> repo.txns.observeTotalsBetween(f, t, TxnType.EXPENSE) }
    val incomeTotals: StateFlow<List<CategoryTotal>> = perMonth(emptyList()) { f, t -> repo.txns.observeTotalsBetween(f, t, TxnType.INCOME) }
    val investTotals: StateFlow<List<CategoryTotal>> = perMonth(emptyList()) { f, t -> repo.txns.observeTotalsBetween(f, t, TxnType.INVEST) }

    /** Previous month's totals, for "vs last month" on the details screen. */
    val prevTypeTotals: StateFlow<List<TypeTotal>> = monthOffset.flatMapLatest { off ->
        val (from, to) = monthRange(offset = off - 1)
        repo.txns.observeTypeTotals(from, to)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val editor = MutableStateFlow(EditorState())

    val statement = StatementImporter(app, repo, jev, viewModelScope)

    /** One-off message for a snackbar, e.g. "Imported 34 transactions". */
    val toast = MutableStateFlow<String?>(null)

    // ------------------------------------------------------------ multi-select

    /** Ids of transactions selected on the home list (long-press to start). */
    val selection = MutableStateFlow<Set<Long>>(emptySet())
    /** The last batch deleted, kept so the snackbar can undo it. */
    val lastDeleted = MutableStateFlow<List<Txn>?>(null)

    fun toggleSelect(id: Long) = selection.update { if (id in it) it - id else it + id }

    fun selectAll(ids: Collection<Long>) = selection.update { it + ids }

    fun clearSelection() { selection.value = emptySet() }

    fun deleteSelected() = viewModelScope.launch {
        val ids = selection.value
        if (ids.isEmpty()) return@launch
        selection.value = emptySet()
        lastDeleted.value = repo.deleteMany(ids)
    }

    fun undoDelete() = viewModelScope.launch {
        lastDeleted.value?.let { repo.restore(it) }
        lastDeleted.value = null
    }

    // ------------------------------------------------------------ manual entry

    fun newEntry(type: String = TxnType.EXPENSE) {
        editor.value = EditorState(type = type)
        navigate(Screen.Editor)
    }

    fun edit(id: Long) = viewModelScope.launch {
        val t = repo.txns.get(id) ?: return@launch
        editor.value = EditorState(
            id = t.id, type = t.type, amountText = t.amount.toString(), categoryId = t.categoryId,
            counterparty = t.counterparty, note = t.note, timestamp = t.timestamp,
            source = t.source, reference = t.reference, categorisedBy = t.categorisedBy,
        )
        navigate(Screen.Editor)
    }

    fun updateEditor(f: (EditorState) -> EditorState) = editor.update(f)

    /** Switch between spending / income / investing; drops a category of the wrong kind. */
    fun setType(type: String) = editor.update { e ->
        val keep = categories.value.firstOrNull { it.id == e.categoryId }?.kind == type
        e.copy(type = type, categoryId = if (keep) e.categoryId else null, aiMessage = if (keep) e.aiMessage else null)
    }

    fun pickCategory(id: Long) = editor.update { e ->
        val kind = categories.value.firstOrNull { it.id == id }?.kind ?: e.type
        e.copy(categoryId = id, type = kind, categorisedBy = "manual", aiUncertain = false)
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
                source = e.source, reference = e.reference, categorisedBy = e.categorisedBy, type = e.type,
            ),
        )
        back()
    }

    fun delete() = viewModelScope.launch {
        val id = editor.value.id
        if (id != 0L) repo.delete(id)
        back()
    }

    // ------------------------------------------------------------ receipt import

    /** Screenshot shared from BRImo (or picked from the gallery) → pre-filled review form. */
    fun importReceipt(uri: Uri, mimeType: String? = null) {
        editor.value = EditorState(fromReceipt = true, busy = "Reading receipt…")
        openFresh(Screen.Editor)
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
        openFresh(Screen.Editor)
        viewModelScope.launch { fillFromText(text) }
    }

    private suspend fun fillFromText(text: String) {
        val p = ReceiptParser.parse(text)
        val dup = p.reference?.let { repo.txns.findByReference(it) }
        editor.update {
            it.copy(
                type = if (p.incoming) TxnType.INCOME else TxnType.EXPENSE,
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
        decideCategory(p.incoming, p.counterparty, p.amount, p.note, p.kind, p.source, text)
    }

    private suspend fun decideCategory(
        incoming: Boolean, counterparty: String?, amount: Long?, note: String?, kind: String?, source: String?, text: String?,
    ) {
        // Money out can be spending or investing; money in is income.
        val kinds = if (incoming) listOf(TxnType.INCOME) else TxnType.OUTGOING
        val cats = repo.categories.getByKinds(kinds)

        // 1) Same person/merchant before? Reuse that category — instant and free.
        if (!counterparty.isNullOrBlank()) {
            val known = repo.txns.lastCategoryFor(counterparty, kinds)?.let { id -> cats.firstOrNull { it.id == id } }
            if (known != null) {
                editor.update {
                    it.copy(
                        categoryId = known.id, type = known.kind, categorisedBy = "history", busy = null,
                        aiMessage = "Same category as last time with $counterparty.",
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
        runCatching { jev.categorize(cats, amount, counterparty, note, kind, source, text, incoming) }
            .onSuccess { d ->
                val pct = (d.confidence * 100).toInt()
                val uncertain = d.confidence < settings.confidenceThreshold
                val runnerUp = d.probabilities.entries.filter { it.key != d.category.name }
                    .maxByOrNull { it.value }?.takeIf { uncertain && it.value > 0.1 }
                editor.update {
                    it.copy(
                        categoryId = d.category.id, type = d.category.kind, categorisedBy = "jev", busy = null, aiUncertain = uncertain,
                        aiMessage = buildString {
                            append("Jev picked ${d.category.name} ($pct% sure)")
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
            e.type == TxnType.INCOME,
            e.counterparty.ifBlank { null }, e.amountText.filter { it.isDigit() }.toLongOrNull(),
            e.note.ifBlank { null }, null, e.source, e.rawText,
        )
    }

    // ------------------------------------------------------------ rekening koran

    fun importStatement(uri: Uri) {
        statement.start(uri)
        navigate(Screen.StatementImport)
    }

    /** A PDF from the share sheet: a statement if it has many dated rows, otherwise a receipt. */
    fun importPdf(uri: Uri) {
        editor.value = EditorState(fromReceipt = true, busy = "Reading file…")
        openFresh(Screen.Editor)
        viewModelScope.launch {
            if (statement.tryStatement(uri)) openFresh(Screen.StatementImport)
            else importReceipt(uri, "application/pdf")
        }
    }

    fun confirmStatement() = viewModelScope.launch {
        val n = statement.confirm(categories.value)
        toast.value = "Imported $n transactions"
        openFresh(Screen.Home)
    }

    // ------------------------------------------------------------ categories & settings

    fun saveCategory(c: Category) = viewModelScope.launch {
        if (c.id == 0L) repo.categories.insert(c) else repo.categories.update(c)
        repo.refreshWidget()
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
        back()
    }
}
