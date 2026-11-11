package com.ewbible.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.ewbible.app.ui.screens.HomeScreen
import com.ewbible.app.ui.theme.EWBibleTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Theme is handled inside HomeScreen for Dark Mode switch,
            // but we keep EWBibleTheme here as base so Material3 works
            EWBibleTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // This is your new layout:
                    // Top Bar: ☰ left slides, ⋮ right slides
                    // Header: EW Bible + Grow in the Word of God, every day.
                    // Buttons: World English Bible, Commentary, Search
                    // Left Drawer: Book -> Chapter -> Verse
                    // Right Drawer: Settings, Login, Dark Mode, A- / A+, Daily Verse, Notes
                    // Hold verse = Highlight colors
                    HomeScreen()
                }
            }
        }
    }
}
