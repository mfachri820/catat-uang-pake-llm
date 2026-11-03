package com.catatuang.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catatuang.app.data.Category

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(vm: MainViewModel) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Category?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories") },
                navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = Category(name = "", emoji = "🏷️", hint = "") }) {
                Icon(Icons.Filled.Add, "Add category")
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 96.dp)) {
            item {
                Text(
                    "Jev reads each category's description to decide where a payment goes. " +
                        "Mention the merchants and words you'd expect, e.g. “GoFood, warung, kopi”.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            items(categories, key = { it.id }) { c ->
                Row(
                    Modifier.fillMaxWidth().clickable { editing = c }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(c.emoji, style = MaterialTheme.typography.headlineSmall)
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

@Composable
private fun CategoryDialog(c: Category, onDismiss: () -> Unit, onSave: (Category) -> Unit, onDelete: (Category) -> Unit) {
    var name by remember { mutableStateOf(c.name) }
    var emoji by remember { mutableStateOf(c.emoji) }
    var hint by remember { mutableStateOf(c.hint) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (c.id == 0L) "New category" else "Edit category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(emoji, { emoji = it.take(4) }, label = { Text("Icon") }, singleLine = true, modifier = Modifier.width(80.dp))
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(hint, { hint = it }, label = { Text("What belongs here (for Jev)") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(c.copy(name = name.trim(), emoji = emoji.ifBlank { "🏷️" }, hint = hint.trim())) }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (c.id != 0L) TextButton(onClick = { onDelete(c) }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
