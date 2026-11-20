package com.ewbible.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ewbible.app.data.ai.GeminiRepository
import com.ewbible.app.data.database.AppDatabase
import com.ewbible.app.data.preferences.AppPreferences
import com.ewbible.app.data.sync.SyncManager
import com.ewbible.app.ui.screens.HomeScreen
import com.ewbible.app.ui.theme.EWBibleTheme
import com.ewbible.app.ui.viewmodel.ChatViewModel
import com.ewbible.app.ui.viewmodel.HomeViewModel
import com.ewbible.app.ui.viewmodel.HomeViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ✅ FIXED: init() already ran in EwBibleApp - don't init twice
        CrashReporter.breadcrumb(this, "MainActivity.onCreate")

        // Read crash ONCE before setContent
        val savedCrash = CrashReporter.getLastCrash(this)
            ?: CrashReporter.getLastNonFatal(this)
        if (savedCrash != null) {
            CrashReporter.breadcrumb(this, "App restarted after crash")
            exportCrashToDownloads(this, savedCrash)
        }

        val preferences = AppPreferences(applicationContext)
        val geminiRepository = GeminiRepository()

        // Create SyncManager ONCE here
        val syncManager = SyncManager()

        // DO NOT crash here if DB is broken - catch it and show error UI instead
        var dbError: String? = null
        val dao = try {
            AppDatabase.getInstance(applicationContext).bibleDao()
        } catch (e: Throwable) {
            dbError = e.toString() + "\n" + (e.cause?.toString() ?: "")
            CrashReporter.recordNonFatal(this, "MainActivity-DB-Init", e)
            CrashReporter.breadcrumb(this, "DB INIT FAILED: ${e.message}")
            null
        }

        setContent {
            val isDarkMode by preferences.isDarkMode.collectAsState(initial = null)
            val useDarkTheme = isDarkMode ?: false

            var showCrashDialog by remember { mutableStateOf(savedCrash != null) }
            var crashText by remember { mutableStateOf(savedCrash ?: "") }

            EWBibleTheme(darkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (dbError != null) {
                        // DB broken - show this instead of crash-loop
                        Box(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                // ✅ FIXED: Share -> Copy
                                text = "Database failed to open.\n\n$dbError\n\nTake a screenshot, or copy this from the crash report.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else if (dao != null) {
                        val factory = HomeViewModelFactory(
                            dao = dao,
                            preferences = preferences,
                            geminiRepository = geminiRepository,
                            syncManager = syncManager
                        )
                        val homeViewModel: HomeViewModel = viewModel(factory = factory)
                        val chatViewModel: ChatViewModel = viewModel(factory = factory)

                        HomeScreen(
                            viewModel = homeViewModel,
                            chatViewModel = chatViewModel
                        )
                    }

                    if (showCrashDialog) {
                        CrashReportDialog(
                            crashText = crashText,
                            // ✅ FIXED: onShare + shareCrashText() -> onCopy (copies full report)
                            onCopy = { /* CrashReportDialog already copies to clipboard */ },
                            onClear = {
                                CrashReporter.clear(this@MainActivity)
                                CrashReporter.clearAll(this@MainActivity)
                                showCrashDialog = false
                            },
                            onDismiss = { showCrashDialog = false }
                        )
                    }
                }
            }
        }
    }
}