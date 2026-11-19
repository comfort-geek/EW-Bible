package com.ewbible.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ewbible.app.data.database.AppDatabase
import com.ewbible.app.data.database.GeminiRepository
import com.ewbible.app.data.preferences.AppPreferences
import com.ewbible.app.ui.screens.HomeScreen
import com.ewbible.app.ui.theme.EWBibleTheme
import com.ewbible.app.ui.viewmodel.ChatViewModel
import com.ewbible.app.ui.viewmodel.HomeViewModel
import com.ewbible.app.ui.viewmodel.HomeViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        CrashReporter.init(this)
        CrashReporter.breadcrumb(this, "MainActivity.onCreate")

        // Read crash ONCE before setContent
        val savedCrash = CrashReporter.getLastCrash(this)
            ?: CrashReporter.getLastNonFatal(this)
        if (savedCrash != null) {
            CrashReporter.breadcrumb(this, "App restarted after crash")
            // Backup to Downloads - so you can see it in Files app even if app is broken
            exportCrashToDownloads(this, savedCrash)
        }

        val database = AppDatabase.getInstance(applicationContext)
        val dao = database.bibleDao()
        val preferences = AppPreferences(applicationContext)
        val geminiRepository = GeminiRepository()

        val factory = HomeViewModelFactory(
            dao = dao,
            preferences = preferences,
            geminiRepository = geminiRepository,
            bibleDao = dao
        )

        setContent {
            val isDarkMode by preferences.isDarkMode.collectAsState(initial = null)
            val useDarkTheme = isDarkTheme ?: false

            // Popup state - shows automatically if crash exists
            var showCrashDialog by remember { mutableStateOf(savedCrash != null) }
            var crashText by remember { mutableStateOf(savedCrash ?: "") }

            val homeViewModel: HomeViewModel = viewModel(factory = factory)
            val chatViewModel: ChatViewModel = viewModel(factory = factory)

            EWBibleTheme(darkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HomeScreen(
                        viewModel = homeViewModel,
                        chatViewModel = chatViewModel
                    )

                    // AUTO POPUP - on top of everything, even if HomeScreen is broken
                    if (showCrashDialog) {
                        CrashReportDialog(
                            crashText = crashText,
                            onShare = { shareCrashText(this@MainActivity, crashText) },
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