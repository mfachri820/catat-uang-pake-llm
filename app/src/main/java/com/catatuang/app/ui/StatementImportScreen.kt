package com.catatuang.app.ui

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catatuang.app.data.Category
import com.catatuang.app.statement.ImportRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatementImportScreen(vm: MainViewModel) {
    val s by vm.statement.state.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val byId = remember(categories) { categories.associateBy { it.id } }
    var showRaw by remember { mutableStateOf(false) }
    val chosen = s.rows.filter { it.selected }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rekening koran") },
                navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        bottomBar = {
            if (s.rows.isNotEmpty()) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = vm::confirmStatement,
                        enabled = chosen.isNotEmpty() && s.busy == null,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(52.dp),
                    ) {
                        Text("Import ${chosen.size} expenses · ${rupiah(chosen.sumOf { it.amount })}", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (s.needsPassword) item { PasswordCard(s.wrongPassword, vm.statement::submitPassword) }

            s.busy?.let { msg ->
                item {
                    Column {
                        val p = s.progress
                        if (p != null) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                        else LinearProgressIndicator(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (p != null) "$msg ${(p * 100).toInt()}%" else msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            s.error?.let { item { Banner(it, error = true) } }
            s.notice?.let { item { Banner(it, error = false) } }

            if (s.rows.isNotEmpty()) {
                item {
                    Column {
                        Text(
                            "${s.rows.size} outgoing transactions found",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (s.creditsCount > 0) {
                            Text(
                                "${s.creditsCount} incoming (${rupiah(s.creditsTotal)}) left out — this app tracks spending.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val dupes = s.rows.count { it.duplicateNote != null }
                        if (dupes > 0) {
                            Text(
                                "$dupes look like payments you already recorded from receipts, so they're unticked.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row {
                            TextButton(onClick = { vm.statement.setAll(true) }) { Text("Tick all") }
                            TextButton(onClick = { vm.statement.setAll(false) }) { Text("Untick all") }
                        }
                    }
                }
                items(s.rows, key = { it.key }) { row ->
                    StatementRowItem(
                        row = row,
                        category = row.categoryId?.let(byId::get),
                        categories = categories,
                        onToggle = { vm.statement.toggle(row.key) },
                        onPick = { vm.statement.setCategory(row.key, it) },
                    )
                    HorizontalDivider()
                }
            }

            s.rawText?.let { raw ->
                item {
                    TextButton(onClick = { showRaw = !showRaw }) { Text(if (showRaw) "Hide extracted text" else "Show extracted text") }
                    if (showRaw) {
                        Text(raw.take(20_000), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatementRowItem(
    row: ImportRow,
    category: Category?,
    categories: List<Category>,
    onToggle: () -> Unit,
    onPick: (Long) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Checkbox(checked = row.selected, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f).padding(top = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.counterparty, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(rupiah(row.amount), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            }
            Text(
                formatDateTime(row.timestamp) + " · " + row.description,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            row.duplicateNote?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
            Box {
                AssistChip(
                    onClick = { menu = true },
                    label = {
                        Text(
                            (category?.let { "${it.emoji} ${it.name}" } ?: "Choose category") + badge(row),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    categories.forEach { c ->
                        DropdownMenuItem(text = { Text("${c.emoji} ${c.name}") }, onClick = { onPick(c.id); menu = false })
                    }
                }
            }
        }
    }
}

private fun badge(row: ImportRow): String = when (row.categorisedBy) {
    "jev" -> row.confidence?.let { "  · AI ${(it * 100).toInt()}%" } ?: "  · AI"
    "history" -> "  · like before"
    "rule" -> "  · auto"
    else -> ""
}

@Composable
private fun Banner(text: String, error: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) { Text(text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium) }
}

@Composable
private fun PasswordCard(wrong: Boolean, onSubmit: (String) -> Unit) {
    var pw by remember { mutableStateOf("") }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lock, null)
                Spacer(Modifier.width(8.dp))
                Text("This e-statement is password protected", style = MaterialTheme.typography.titleSmall)
            }
            Text(
                "Use the password from the bank's e-statement email (often based on your date of birth). It's only used to open the file and isn't saved.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = pw, onValueChange = { pw = it }, label = { Text("PDF password") }, singleLine = true,
                isError = wrong, supportingText = if (wrong) ({ Text("Wrong password, try again") }) else null,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { onSubmit(pw) }, enabled = pw.isNotEmpty()) { Text("Open") }
        }
    }
}
