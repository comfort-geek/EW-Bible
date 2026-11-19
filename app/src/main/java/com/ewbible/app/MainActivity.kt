package com.ewbible.app

import android.content.ClipData
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ewbible.app.data.database.AppDatabase
import com.ewbible.app.data.preferences.AppPreferences
import com.ewbible.app.ui.screens.HomeScreen
import com.ewbible.app.ui.theme.EWBibleTheme
import com.ewbible.app.ui.viewmodel.HomeViewModel
import com.ewbible.app.ui.viewmodel.HomeViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // enableEdgeToEdge() // TEMP OFF - testing Android 9 crash

        CrashReporter.init(this)
        CrashReporter.breadcrumb(this, "MainActivity.onCreate")

        val savedCrash = CrashReporter.getLastCrash(this)

        // If we crashed last time, show ONLY the crash report - do NOT load HomeScreen
        // This stops the crash loop
        if (savedCrash != null) {
            setContent {
                var lastCrash: String? by remember { mutableStateOf(savedCrash) }
                EWBibleTheme(darkTheme = false) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        if (lastCrash != null) {
                            AlertDialog(
                                onDismissRequest = { },
                                title = { Text("App crashed last time") },
                                text = {
                                    Column {
                                        Text("Copy this whole text and send it:", fontSize = 13.sp)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .height(300.dp)
                                                .verticalScroll(rememberScrollState())
                                        ) {
                                            Text(lastCrash!!, fontSize = 11.sp)
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("crash", lastCrash ?: ""))
                                        Toast.makeText(this@MainActivity, "Copied!", Toast.LENGTH_SHORT).show()
                                    }) { Text("Copy") }
                                },
                                dismissButton = {
                                    TextButton(onClick = {
                                        CrashReporter.clear(this@MainActivity)
                                        lastCrash = null
                                        recreate()
                                    }) { Text("Clear / Try Again") }
                                }
                            )
                        }
                    }
                }
            }
            return
        }

        // No crash - load normally
        val database = AppDatabase.getInstance(applicationContext)
        val preferences = AppPreferences(applicationContext)

        setContent {
            val isDarkMode by preferences.isDarkMode.collectAsState(initial = null)
            val useDarkTheme = isDarkMode ?: false

            val homeViewModel: HomeViewModel = viewModel(
                factory = HomeViewModelFactory(database.bibleDao(), preferences)
            )

            EWBibleTheme(darkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HomeScreen(viewModel = homeViewModel)
                }
            }
        }
    }
}