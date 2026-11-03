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

        setContent {
            CatatUangTheme {
                val screen by vm.screen.collectAsStateWithLifecycle()
                val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                    uri?.let(vm::importReceipt)
                }
                BackHandler(enabled = screen != Screen.Home) { vm.back() }

                when (screen) {
                    Screen.Home -> HomeScreen(vm, onScanReceipt = {
                        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    })
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

    private fun handleIntent(intent: Intent?) {
        when {
            intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true -> {
                sharedImage(intent)?.let(vm::importReceipt)
            }
            intent?.action == ACTION_ADD -> vm.newExpense()
        }
    }

    private fun sharedImage(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }

    companion object {
        const val ACTION_OPEN = "com.catatuang.app.OPEN"
        const val ACTION_ADD = "com.catatuang.app.ADD_EXPENSE"
    }
}
