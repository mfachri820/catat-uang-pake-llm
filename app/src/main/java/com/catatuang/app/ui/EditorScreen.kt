package com.catatuang.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Calendar
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(vm: MainViewModel) {
    val e by vm.editor.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    var showDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showRaw by remember { mutableStateOf(false) }

    val title = when {
        e.id != 0L -> "Edit expense"
        e.fromReceipt -> "Check receipt"
        else -> "New expense"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (e.id != 0L) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Delete") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            e.busy?.let { msg ->
                Column {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            e.duplicateWarning?.let { Notice(it, warning = true) }
            e.error?.let { Notice(it, warning = true) }

            OutlinedTextField(
                value = e.amountText,
                onValueChange = { v -> vm.updateEditor { it.copy(amountText = v.filter(Char::isDigit).take(13), error = null) } },
                label = { Text("Amount") },
                prefix = { Text("Rp ") },
                textStyle = MaterialTheme.typography.headlineSmall,
                visualTransformation = ThousandsTransformation,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = e.counterparty,
                onValueChange = { v -> vm.updateEditor { it.copy(counterparty = v) } },
                label = { Text("Paid to") },
                placeholder = { Text("Recipient or merchant") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = e.note,
                onValueChange = { v -> vm.updateEditor { it.copy(note = v) } },
                label = { Text("Note") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.fillMaxWidth().clickable { showDate = true }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.CalendarMonth, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Text(formatDateTime(e.timestamp), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(e.source, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Text("Category", style = MaterialTheme.typography.titleSmall)
            e.aiMessage?.let { msg ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (e.aiUncertain) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AutoAwesome, null)
                        Spacer(Modifier.width(10.dp))
                        Text(msg, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                categories.forEach { c ->
                    FilterChip(
                        selected = e.categoryId == c.id,
                        onClick = { vm.pickCategory(c.id) },
                        label = { Text("${c.emoji} ${c.name}") },
                    )
                }
            }
            if (e.busy == null && vm.settings.openRouterKey.isNotBlank()) {
                TextButton(onClick = vm::suggestCategory) {
                    Icon(Icons.Outlined.AutoAwesome, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Ask Jev to pick")
                }
            }

            e.rawText?.let { raw ->
                TextButton(onClick = { showRaw = !showRaw }) { Text(if (showRaw) "Hide scanned text" else "Show scanned text") }
                if (showRaw) {
                    Text(raw, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Button(
                onClick = vm::save,
                enabled = e.busy == null || e.amountText.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Save", fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = toUtcDate(e.timestamp))
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { picked -> vm.updateEditor { it.copy(timestamp = keepTime(it.timestamp, picked)) } }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this expense?") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Notice(text: String, warning: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = if (warning) MaterialTheme.colorScheme.errorContainer else Color.Unspecified)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.WarningAmber, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

/** DatePicker works in UTC midnights; convert from/to local time keeping the original hour. */
private fun toUtcDate(local: Long): Long {
    val l = Calendar.getInstance().apply { timeInMillis = local }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear(); set(l.get(Calendar.YEAR), l.get(Calendar.MONTH), l.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

private fun keepTime(original: Long, pickedUtc: Long): Long {
    val p = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = pickedUtc }
    return Calendar.getInstance().apply {
        timeInMillis = original
        set(p.get(Calendar.YEAR), p.get(Calendar.MONTH), p.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}
