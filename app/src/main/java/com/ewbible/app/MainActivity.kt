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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ewbible.app.data.ai.AiRepository
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

        CrashReporter.breadcrumb(this, "MainActivity.onCreate")

        val savedCrash = CrashReporter.getLastCrash(this)
            ?: CrashReporter.getLastNonFatal(this)
        if (savedCrash != null) {
            CrashReporter.breadcrumb(this, "App restarted after crash")
        }

        val preferences = AppPreferences(applicationContext)
        // FINAL: AiRepository - matches ChatViewModel
        val aiRepository = AiRepository()
        val syncManager = SyncManager()

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
                        Box(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Database failed to open.\n\n$dbError\n\nTake a screenshot, or copy this from the crash report.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else if (dao != null) {
                        // FINAL: Home factory - NO geminiRepository
                        val homeFactory = remember(dao) {
                            HomeViewModelFactory(
                                dao = dao,
                                preferences = preferences,
                                syncManager = syncManager
                            )
                        }
                        // FINAL: Chat factory - all 3 args
                        val chatFactory = remember(dao, aiRepository) {
                            object : ViewModelProvider.Factory {
                                @Suppress("UNCHECKED_CAST")
                                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                    if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
                                        return ChatViewModel(
                                            bibleDao = dao,
                                            geminiRepository = aiRepository,
                                            syncManager = syncManager
                                        ) as T
                                    }
                                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                                }
                            }
                        }

                        val homeViewModel: HomeViewModel = viewModel(factory = homeFactory)
                        val chatViewModel: ChatViewModel = viewModel(factory = chatFactory)

                        HomeScreen(
                            viewModel = homeViewModel,
                            chatViewModel = chatViewModel
                        )
                    }

                    if (showCrashDialog) {
                        CrashReportDialog(
                            crashText = crashText,
                            onCopy = { /* CrashReportDialog already copies to clipboard */ },
                            onClear = {
                                // FIXED: CrashReporter has clear(), not clearLastCrash() / clearLastNonFatal()
                                CrashReporter.clear(this@MainActivity)
                                crashText = ""
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