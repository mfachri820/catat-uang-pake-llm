package com.catatuang.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catatuang.app.data.TxnType
import com.catatuang.app.data.TxnWithCategory
import com.catatuang.app.data.monthRange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainViewModel, onScanReceipt: () -> Unit, onImportStatement: () -> Unit) {
    val txns by vm.monthTxns.collectAsStateWithLifecycle()
    val typeTotals by vm.typeTotals.collectAsStateWithLifecycle()
    val offset by vm.monthOffset.collectAsStateWithLifecycle()
    val filter by vm.listFilter.collectAsStateWithLifecycle()
    val monthLabel = remember(offset) { formatMonth(monthRange(offset = offset).first) }
    val totals = remember(typeTotals) { typeTotals.associate { it.type to it.total } }
    val shown = remember(txns, filter) { if (filter == null) txns else txns.filter { it.type == filter } }
    val byDay = remember(shown) { shown.groupBy { formatDay(it.timestamp) } }

    val toast by vm.toast.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(toast) {
        toast?.let { snackbar.showSnackbar(it); vm.toast.value = null }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Catat Uang", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onImportStatement) {
                        Icon(Icons.Outlined.UploadFile, contentDescription = "Import rekening koran")
                    }
                    IconButton(onClick = { vm.navigate(Screen.Categories) }) {
                        Icon(Icons.Outlined.Category, contentDescription = "Categories")
                    }
                    IconButton(onClick = { vm.navigate(Screen.Settings) }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FloatingActionButton(
                    onClick = { vm.newEntry(if (filter == null) TxnType.EXPENSE else filter!!) },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ) { Icon(Icons.Filled.Add, contentDescription = "Add manually") }
                ExtendedFloatingActionButton(
                    onClick = onScanReceipt,
                    icon = { Icon(Icons.Outlined.ReceiptLong, contentDescription = null) },
                    text = { Text("Scan receipt") },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 160.dp),
        ) {
            item {
                MonthSwitcher(
                    label = monthLabel,
                    canGoForward = offset < 0,
                    onPrev = { vm.monthOffset.value = offset - 1 },
                    onNext = { vm.monthOffset.value = offset + 1 },
                )
            }
            item {
                SummaryCard(
                    spent = totals[TxnType.EXPENSE] ?: 0,
                    income = totals[TxnType.INCOME] ?: 0,
                    invested = totals[TxnType.INVEST] ?: 0,
                    budget = vm.settings.monthlyBudget.takeIf { offset == 0 && it > 0 },
                    onClick = { vm.navigate(Screen.Details) },
                )
            }
            item {
                LazyRow(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val options = listOf(null to "All", TxnType.EXPENSE to "Spending", TxnType.INCOME to "Income", TxnType.INVEST to "Investing")
                    items(options) { (type, label) ->
                        FilterChip(selected = filter == type, onClick = { vm.listFilter.value = type }, label = { Text(label) })
                    }
                }
            }

            if (shown.isEmpty()) {
                item { EmptyState(filter) }
            }
            byDay.forEach { (day, list) ->
                item(key = "h-$day") {
                    Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 6.dp)) {
                        Text(day, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        val out = list.filter { it.type == TxnType.EXPENSE }.sumOf { it.amount }
                        if (out > 0) Text("-" + rupiah(out), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(list, key = { it.id }) { TxnRow(it, onClick = { vm.edit(it.id) }) }
            }
        }
    }
}

@Composable
private fun MonthSwitcher(label: String, canGoForward: Boolean, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month") }
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        IconButton(onClick = onNext, enabled = canGoForward) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month") }
    }
}

@Composable
private fun SummaryCard(spent: Long, income: Long, invested: Long, budget: Long?, onClick: () -> Unit) {
    val dark = isSystemInDarkTheme()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick),
    ) {
        val on = MaterialTheme.colorScheme.onPrimaryContainer
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Spent this month", style = MaterialTheme.typography.labelLarge, color = on, modifier = Modifier.weight(1f))
                Text("Details", style = MaterialTheme.typography.labelLarge, color = on)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = on, modifier = Modifier.size(18.dp))
            }
            Text(rupiah(spent), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold, color = on)
            if (budget != null) {
                val ratio = (spent.toFloat() / budget).coerceAtLeast(0f)
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { ratio.coerceAtMost(1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = if (ratio > 1f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    drawStopIndicator = {},
                )
                Spacer(Modifier.height(6.dp))
                val left = budget - spent
                Text(
                    if (left >= 0) "${rupiah(left)} left of your ${rupiah(budget)} budget" else "${rupiah(-left)} over your budget",
                    style = MaterialTheme.typography.bodySmall, color = on,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Income", "+" + rupiahShort(income), KindColors.accent(TxnType.INCOME, dark), Modifier.weight(1f))
                Stat("Invested", rupiahShort(invested), KindColors.accent(TxnType.INVEST, dark), Modifier.weight(1f))
                val net = income - spent - invested
                Stat("Left over", (if (net < 0) "-" else "") + rupiahShort(kotlin.math.abs(net)), on, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}

@Composable
fun TxnRow(t: TxnWithCategory, onClick: () -> Unit) {
    val dark = isSystemInDarkTheme()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(t.categoryIcon, t.type)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.counterparty.ifBlank { t.categoryName ?: kindLabel(t.type) }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(t.categoryName ?: "Uncategorised", t.note.takeIf { it.isNotBlank() }, t.source.takeIf { it != "Manual" }).joinToString(" · ")
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                when (t.type) { TxnType.INCOME -> "+" + rupiah(t.amount); else -> rupiah(t.amount) },
                style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                color = if (t.type == TxnType.EXPENSE) MaterialTheme.colorScheme.onSurface else KindColors.accent(t.type, dark),
            )
            Text(
                formatTime(t.timestamp) + if (t.categorisedBy == "jev") " · AI" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyState(filter: String?) {
    Column(Modifier.fillMaxWidth().padding(top = 32.dp, start = 8.dp, end = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CategoryBadge(
            when (filter) { TxnType.INCOME -> "income"; TxnType.INVEST -> "growth"; else -> "bills" },
            filter ?: TxnType.EXPENSE, size = 64.dp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            when (filter) { null -> "Nothing recorded this month"; else -> "No ${kindLabel(filter).lowercase()} this month" },
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "After a transfer in BRImo, tap Share on the receipt and choose Catat Uang. " +
                "The amount, recipient and date are filled in for you, and Jev picks the category.\n\n" +
                "Or catch up on a whole month: tap the upload icon at the top and pick your rekening koran PDF.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
