package com.catatuang.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel) {
    val s = vm.settings
    var key by remember { mutableStateOf(s.openRouterKey) }
    var model by remember { mutableStateOf(s.jevModel) }
    var threshold by remember { mutableFloatStateOf(s.confidenceThreshold) }
    var budget by remember { mutableStateOf(s.monthlyBudget.takeIf { it > 0 }?.toString().orEmpty()) }
    var showKey by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Monthly budget", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = budget,
                onValueChange = { v -> budget = v.filter(Char::isDigit).take(13) },
                label = { Text("Budget (optional)") },
                prefix = { Text("Rp ") },
                visualTransformation = ThousandsTransformation,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = { Text("Shown on the home screen and the widget") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()
            Text("Jev on OpenRouter", style = MaterialTheme.typography.titleSmall)
            Text(
                "Jev picks a category for imported receipts. Create a key at openrouter.ai/keys. " +
                    "The key is stored only on this phone. Repeat payees reuse their last category without calling Jev.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text("OpenRouter API key") },
                placeholder = { Text("sk-or-v1-…") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, "Show key")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Model") },
                supportingText = { Text("Default ~typesafe/jev-latest; pin e.g. typesafe/jev-1.14") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Flag as unsure below ${(threshold * 100).toInt()}% confidence", style = MaterialTheme.typography.bodyMedium)
            Slider(value = threshold, onValueChange = { threshold = it }, valueRange = 0.2f..0.9f, steps = 6)

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { vm.saveSettings(key, model, threshold, budget.toLongOrNull() ?: 0L) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Save") }
            Spacer(Modifier.height(24.dp))
        }
    }
}
