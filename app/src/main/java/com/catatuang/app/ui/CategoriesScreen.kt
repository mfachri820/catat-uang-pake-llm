package com.catatuang.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catatuang.app.data.Category
import com.catatuang.app.data.TxnType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(vm: MainViewModel) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Category?>(null) }
    var kind by remember { mutableStateOf(TxnType.EXPENSE) }
    val shown = categories.filter { it.kind == kind }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories") },
                navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = Category(name = "", icon = "tag", hint = "", kind = kind) }) {
                Icon(Icons.Filled.Add, "Add category")
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 96.dp)) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    val kinds = listOf(TxnType.EXPENSE, TxnType.INCOME, TxnType.INVEST)
                    kinds.forEachIndexed { i, k ->
                        SegmentedButton(
                            selected = kind == k, onClick = { kind = k },
                            shape = SegmentedButtonDefaults.itemShape(i, kinds.size),
                        ) { Text(kindLabel(k)) }
                    }
                }
                Text(
                    "Jev reads each category's description to decide where a transaction goes. " +
                        "Mention the merchants, apps and words you'd expect, e.g. “GoFood, warung, kopi” or “Bibit, reksa dana”.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            items(shown, key = { it.id }) { c ->
                Row(
                    Modifier.fillMaxWidth().clickable { editing = c }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CategoryBadge(c.icon, c.kind)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = MaterialTheme.typography.bodyLarge)
                        Text(c.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    }
                }
            }
        }
    }

    editing?.let { c -> CategoryDialog(c, onDismiss = { editing = null }, onSave = { vm.saveCategory(it); editing = null }, onDelete = { vm.deleteCategory(it); editing = null }) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryDialog(c: Category, onDismiss: () -> Unit, onSave: (Category) -> Unit, onDelete: (Category) -> Unit) {
    var name by remember { mutableStateOf(c.name) }
    var icon by remember { mutableStateOf(c.icon) }
    var hint by remember { mutableStateOf(c.hint) }
    val dark = isSystemInDarkTheme()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (c.id == 0L) "New ${kindLabel(c.kind).lowercase()} category" else "Edit category") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge(icon, c.kind, size = 48.dp)
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Text("Icon", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CategoryIcons.ALL.forEach { (key, res) ->
                        val selected = key == icon
                        Box(
                            Modifier.size(40.dp).clip(CircleShape)
                                .background(if (selected) KindColors.container(c.kind, dark) else MaterialTheme.colorScheme.surfaceContainerHigh)
                                .then(if (selected) Modifier.border(2.dp, KindColors.accent(c.kind, dark), CircleShape) else Modifier)
                                .clickable { icon = key },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painterResource(res), contentDescription = key, modifier = Modifier.size(20.dp),
                                tint = if (selected) KindColors.accent(c.kind, dark) else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                OutlinedTextField(hint, { hint = it }, label = { Text("What belongs here (for Jev)") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(c.copy(name = name.trim(), icon = icon, hint = hint.trim())) }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (c.id != 0L) TextButton(onClick = { onDelete(c) }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
