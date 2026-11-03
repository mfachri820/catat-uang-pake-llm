package com.catatuang.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        ShareShortcut.publish(this)

        setContent {
            CatatUangTheme {
                val screen by vm.screen.collectAsStateWithLifecycle()
                val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                    uri?.let(vm::importReceipt)
                }
                val pickStatement = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    uri?.let(vm::importStatement)
                }
                BackHandler(enabled = screen != Screen.Home) { vm.back() }

                when (screen) {
                    Screen.Home -> HomeScreen(
                        vm,
                        onScanReceipt = {
                            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onImportStatement = { pickStatement.launch(arrayOf("application/pdf")) },
                    )
                    Screen.StatementImport -> StatementImportScreen(vm)
                    Screen.Editor -> EditorScreen(vm)
                    Screen.Categories -> CategoriesScreen(vm)
                    Screen.Settings -> SettingsScreen(vm)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Anything shared to us from the share sheet: an image, a PDF, a few images, or plain text. */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val stream = sharedStream(intent)
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                when {
                    stream != null && isPdf(intent, stream) -> vm.importPdf(stream)
                    stream != null -> vm.importReceipt(stream, intent.type?.takeUnless { it.contains('*') })
                    !text.isNullOrBlank() -> vm.importReceiptText(text)
                }
            }
            // Some apps send the receipt plus a logo/banner; the first image is the receipt.
            Intent.ACTION_SEND_MULTIPLE -> sharedStreams(intent).firstOrNull()?.let { vm.importReceipt(it) }
            ACTION_ADD -> vm.newExpense()
        }
    }

    private fun isPdf(intent: Intent, uri: Uri): Boolean =
        intent.type == "application/pdf" || contentResolver.getType(uri) == "application/pdf"

    private fun sharedStream(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }

    private fun sharedStreams(intent: Intent): List<Uri> =
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        }

    companion object {
        const val ACTION_OPEN = "com.catatuang.app.OPEN"
        const val ACTION_ADD = "com.catatuang.app.ADD_EXPENSE"
    }
}
