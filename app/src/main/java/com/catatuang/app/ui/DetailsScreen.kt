package com.catatuang.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catatuang.app.data.CategoryTotal
import com.catatuang.app.data.TxnType
import com.catatuang.app.data.TxnWithCategory
import com.catatuang.app.data.monthRange
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.roundToInt

/** Distinct, colour-blind-friendly series colours for the category breakdown. */
private fun palette(dark: Boolean) = if (dark) {
    listOf(0xFFFFB68C, 0xFF7FD6B4, 0xFFA9B8FF, 0xFFF5D06B, 0xFFE59ACB, 0xFF8ED2F0, 0xFFC7B28F, 0xFFB0B7B3)
} else {
    listOf(0xFFC2581C, 0xFF0B7A57, 0xFF3A4FB8, 0xFFB98A00, 0xFFA8457F, 0xFF1F7EA8, 0xFF7A6340, 0xFF8A918D)
}.map { Color(it) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(vm: MainViewModel) {
    val offset by vm.monthOffset.collectAsStateWithLifecycle()
    val txns by vm.monthTxns.collectAsStateWithLifecycle()
    val typeTotals by vm.typeTotals.collectAsStateWithLifecycle()
    val prevTotals by vm.prevTypeTotals.collectAsStateWithLifecycle()
    val expense by vm.expenseTotals.collectAsStateWithLifecycle()
    val income by vm.incomeTotals.collectAsStateWithLifecycle()
    val invest by vm.investTotals.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(TxnType.EXPENSE) }

    val (monthStart, monthEnd) = remember(offset) { monthRange(offset = offset) }
    val totals = remember(typeTotals) { typeTotals.associate { it.type to it.total } }
    val prev = remember(prevTotals) { prevTotals.associate { it.type to it.total } }
    val spent = totals[TxnType.EXPENSE] ?: 0
    val earned = totals[TxnType.INCOME] ?: 0
    val invested = totals[TxnType.INVEST] ?: 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(formatMonth(monthStart)) },
                navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { vm.monthOffset.value = offset - 1 }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month") }
                    IconButton(onClick = { vm.monthOffset.value = offset + 1 }, enabled = offset < 0) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { CashFlowCard(earned, spent, invested, prev[TxnType.EXPENSE] ?: 0) }
            item { DailyChartCard(txns, monthStart, monthEnd, isCurrentMonth = offset == 0) }
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val tabs = listOf(TxnType.EXPENSE to "Spending", TxnType.INCOME to "Income", TxnType.INVEST to "Investing")
                    tabs.forEachIndexed { i, (type, label) ->
                        SegmentedButton(
                            selected = tab == type, onClick = { tab = type },
                            shape = SegmentedButtonDefaults.itemShape(i, tabs.size),
                        ) { Text(label) }
                    }
                }
            }
            item {
                val list = when (tab) { TxnType.INCOME -> income; TxnType.INVEST -> invest; else -> expense }
                BreakdownCard(tab, list, txns.filter { it.type == tab }, onOpen = { vm.edit(it) })
            }
            item { TopPayeesCard(tab, txns.filter { it.type == tab }) }
        }
    }
}

// ---------------------------------------------------------------- cash flow

@Composable
private fun CashFlowCard(income: Long, spent: Long, invested: Long, prevSpent: Long) {
    val dark = isSystemInDarkTheme()
    val net = income - spent - invested
    SectionCard("Cash flow") {
        val max = maxOf(income, spent + invested, 1L)
        FlowBar("Money in", income, max, listOf(income to KindColors.accent(TxnType.INCOME, dark)))
        Spacer(Modifier.height(10.dp))
        FlowBar(
            "Money out", spent + invested, max,
            listOf(spent to KindColors.accent(TxnType.EXPENSE, dark), invested to KindColors.accent(TxnType.INVEST, dark)),
        )
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Legend(KindColors.accent(TxnType.EXPENSE, dark), "Spent ${rupiahShort(spent)}")
            Legend(KindColors.accent(TxnType.INVEST, dark), "Invested ${rupiahShort(invested)}")
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(if (net >= 0) "Left over" else "Short by", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    rupiah(abs(net)), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
                    color = if (net >= 0) KindColors.accent(TxnType.INCOME, dark) else MaterialTheme.colorScheme.error,
                )
            }
            if (income > 0) {
                val rate = ((income - spent).toFloat() / income * 100).roundToInt()
                Column(horizontalAlignment = Alignment.End) {
                    Text("Kept (not spent)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$rate%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (prevSpent > 0) {
            val change = ((spent - prevSpent).toFloat() / prevSpent * 100).roundToInt()
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    change > 0 -> "Spending is up $change% on last month (${rupiahShort(prevSpent)})."
                    change < 0 -> "Spending is down ${-change}% on last month (${rupiahShort(prevSpent)})."
                    else -> "Spending is level with last month."
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FlowBar(label: String, total: Long, max: Long, parts: List<Pair<Long, Color>>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(84.dp))
        val track = MaterialTheme.colorScheme.surfaceContainerHigh
        Canvas(Modifier.weight(1f).height(14.dp)) {
            drawRoundRect(track, cornerRadius = CornerRadius(7.dp.toPx()))
            var x = 0f
            parts.forEach { (v, c) ->
                val w = size.width * v / max
                if (w > 0f) {
                    drawRoundRect(c, topLeft = Offset(x, 0f), size = Size(w, size.height), cornerRadius = CornerRadius(7.dp.toPx()))
                    x += w
                }
            }
        }
        Text(rupiahShort(total), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.width(84.dp).padding(start = 8.dp))
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------- daily chart

@Composable
private fun DailyChartCard(txns: List<TxnWithCategory>, start: Long, end: Long, isCurrentMonth: Boolean) {
    val dark = isSystemInDarkTheme()
    val days = remember(start) { Calendar.getInstance().apply { timeInMillis = start }.getActualMaximum(Calendar.DAY_OF_MONTH) }
    val today = remember { Calendar.getInstance().get(Calendar.DAY_OF_MONTH) }
    val perDay = remember(txns) {
        val arr = LongArray(days)
        txns.filter { it.type == TxnType.EXPENSE && it.timestamp in start until end }.forEach {
            val d = Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.DAY_OF_MONTH)
            arr[d - 1] += it.amount
        }
        arr
    }
    val elapsed = if (isCurrentMonth) today else days
    val avg = perDay.take(elapsed).sum() / maxOf(elapsed, 1)
    var selected by remember(start) { mutableStateOf<Int?>(null) }

    SectionCard("Daily spending") {
        val sel = selected
        Text(
            if (sel != null) "Day ${sel + 1}: ${rupiah(perDay[sel])}" else "Average ${rupiah(avg)} a day · tap a bar",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        val bar = KindColors.accent(TxnType.EXPENSE, dark)
        val faded = bar.copy(alpha = 0.35f)
        val avgColor = MaterialTheme.colorScheme.onSurfaceVariant
        val max = (perDay.maxOrNull() ?: 0L).coerceAtLeast(1L)
        Canvas(
            Modifier.fillMaxWidth().height(120.dp).pointerInput(days) {
                detectTapGestures { pos ->
                    val i = (pos.x / (size.width.toFloat() / days)).toInt().coerceIn(0, days - 1)
                    selected = if (selected == i) null else i
                }
            },
        ) {
            val slot = size.width / days
            val w = slot * 0.62f
            perDay.forEachIndexed { i, v ->
                val h = (size.height * v / max).coerceAtLeast(if (v > 0) 3f else 0f)
                val future = isCurrentMonth && i + 1 > today
                drawRoundRect(
                    color = if (sel == null || sel == i) (if (future) faded else bar) else faded,
                    topLeft = Offset(i * slot + (slot - w) / 2, size.height - h),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(w / 3),
                )
            }
            if (avg > 0) {
                val y = size.height - size.height * avg / max
                drawLine(avgColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("1", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("15", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("$days", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------- category breakdown

@Composable
private fun BreakdownCard(type: String, totals: List<CategoryTotal>, txns: List<TxnWithCategory>, onOpen: (Long) -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = palette(dark)
    val sum = totals.sumOf { it.total }
    var expanded by remember(type) { mutableStateOf<Long?>(-1L) }

    SectionCard("${kindLabel(type)} by category") {
        if (sum == 0L) {
            Text("Nothing recorded yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@SectionCard
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Donut(totals.map { it.total }, colors, Modifier.size(120.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Total", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(rupiah(sum), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("${txns.size} transactions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                txns.maxByOrNull { it.amount }?.let {
                    Text("Biggest: ${rupiahShort(it.amount)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        totals.forEachIndexed { i, c ->
            val key = c.categoryId ?: 0L
            val open = expanded == key
            val pct = (c.total * 100f / sum).roundToInt()
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { expanded = if (open) -1L else key }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(colors[i % colors.size]))
                Spacer(Modifier.width(10.dp))
                CategoryBadge(c.icon, type, size = 34.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.name ?: "Uncategorised", style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${c.count}× · $pct%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(rupiah(c.total), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedVisibility(open) {
                Column(Modifier.padding(start = 20.dp)) {
                    txns.filter { (it.categoryId ?: 0L) == key }.forEach { t -> TxnRow(t, onClick = { onOpen(t.id) }) }
                }
            }
        }
    }
}

@Composable
private fun Donut(values: List<Long>, colors: List<Color>, modifier: Modifier) {
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    Canvas(modifier) {
        val stroke = size.minDimension * 0.18f
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val sum = values.sum().toFloat().coerceAtLeast(1f)
        drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
        var startAngle = -90f
        values.forEachIndexed { i, v ->
            val sweep = 360f * v / sum
            // A small gap between segments keeps neighbouring colours apart.
            val gap = if (values.size > 1) 1.5f else 0f
            drawArc(colors[i % colors.size], startAngle + gap / 2, (sweep - gap).coerceAtLeast(0.5f), false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            startAngle += sweep
        }
    }
}

// ---------------------------------------------------------------- top payees

@Composable
private fun TopPayeesCard(type: String, txns: List<TxnWithCategory>) {
    val top = remember(txns) {
        txns.filter { it.counterparty.isNotBlank() }
            .groupBy { it.counterparty.trim().lowercase() }
            .map { (_, list) -> Triple(list.first().counterparty, list.sumOf { it.amount }, list.size) }
            .sortedByDescending { it.second }
            .take(5)
    }
    if (top.isEmpty()) return
    SectionCard(if (type == TxnType.INCOME) "Top senders" else "Top recipients") {
        val max = top.first().second.coerceAtLeast(1L)
        val dark = isSystemInDarkTheme()
        val accent = KindColors.accent(type, dark)
        val track = MaterialTheme.colorScheme.surfaceContainerHigh
        top.forEach { (name, total, count) ->
            Column(Modifier.padding(vertical = 6.dp)) {
                Row {
                    Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("${rupiah(total)} · $count×", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(4.dp))
                Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                    drawRoundRect(track, cornerRadius = CornerRadius(3.dp.toPx()))
                    drawRoundRect(accent, size = Size(size.width * total / max, size.height), cornerRadius = CornerRadius(3.dp.toPx()))
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}
