package com.ewbible.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

        if (CrashReporter.getLastCrash(this) != null) {
            CrashReporter.clear(this)
        }

        val database = AppDatabase.getInstance(applicationContext)
        val preferences = AppPreferences(applicationContext)
        val geminiRepository = GeminiRepository()

        // FIXED: One factory for BOTH viewmodels, with ChatDao + Gemini
        val factory = HomeViewModelFactory(
            dao = database.bibleDao(),
            preferences = preferences,
            chatDao = database.chatDao(),
            geminiRepository = geminiRepository
        )

        setContent {
            val isDarkMode by preferences.isDarkMode.collectAsState(initial = null)
            val useDarkTheme = isDarkMode ?: false

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
                }
            }
        }
    }
}