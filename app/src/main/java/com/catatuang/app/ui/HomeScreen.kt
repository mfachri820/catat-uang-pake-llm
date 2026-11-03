package com.catatuang.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catatuang.app.data.CategoryTotal
import com.catatuang.app.data.TxnWithCategory
import com.catatuang.app.data.monthRange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainViewModel, onScanReceipt: () -> Unit) {
    val txns by vm.monthTxns.collectAsStateWithLifecycle()
    val totals by vm.monthTotals.collectAsStateWithLifecycle()
    val offset by vm.monthOffset.collectAsStateWithLifecycle()
    val monthLabel = remember(offset) { formatMonth(monthRange(offset = offset).first) }
    val total = totals.sumOf { it.total }
    val byDay = remember(txns) { txns.groupBy { formatDay(it.timestamp) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Catat Uang", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { vm.navigate(Screen.Categories) }) {
                        Icon(Icons.Outlined.Category, contentDescription = "Categories")
                    }
                    IconButton(onClick = { vm.navigate(Screen.Settings) }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FloatingActionButton(
                    onClick = vm::newExpense,
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
            item { SummaryCard(total, totals, vm.settings.monthlyBudget.takeIf { offset == 0 && it > 0 }) }

            if (txns.isEmpty()) {
                item { EmptyState() }
            }
            byDay.forEach { (day, list) ->
                item(key = "h-$day") {
                    Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 6.dp)) {
                        Text(day, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Text(rupiah(list.sumOf { it.amount }), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        IconButton(onClick = onNext, enabled = canGoForward) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month") }
    }
}

@Composable
private fun SummaryCard(total: Long, totals: List<CategoryTotal>, budget: Long?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Total spent", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(rupiah(total), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            if (budget != null) {
                val ratio = (total.toFloat() / budget).coerceAtLeast(0f)
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { ratio.coerceAtMost(1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = if (ratio > 1f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    drawStopIndicator = {},
                )
                Spacer(Modifier.height(6.dp))
                val left = budget - total
                Text(
                    if (left >= 0) "${rupiah(left)} left of ${rupiah(budget)}" else "${rupiah(-left)} over budget",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            if (totals.isNotEmpty() && total > 0) {
                Spacer(Modifier.height(16.dp))
                totals.take(4).forEach { c ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${c.emoji ?: "❔"}  ${c.name ?: "Uncategorised"}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Box(Modifier.width(72.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))) {
                            Box(Modifier.fillMaxWidth(c.total.toFloat() / total).height(6.dp).background(MaterialTheme.colorScheme.primary))
                        }
                        Text(rupiahShort(c.total), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(84.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }
    }
}

@Composable
private fun TxnRow(t: TxnWithCategory, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) { Text(t.categoryEmoji ?: "❔") }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.counterparty.ifBlank { t.categoryName ?: "Expense" }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(t.categoryName ?: "Uncategorised", t.note.takeIf { it.isNotBlank() }, t.source.takeIf { it != "Manual" }).joinToString(" · ")
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(rupiah(t.amount), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                formatTime(t.timestamp) + if (t.categorisedBy == "jev") " · AI" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Column(Modifier.fillMaxWidth().padding(top = 32.dp, start = 8.dp, end = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🧾", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(12.dp))
        Text("No expenses yet this month", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "After a transfer in BRImo, tap Share on the receipt and choose Catat Uang. " +
                "The amount, recipient and date are filled in for you, and Jev picks the category.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
