package com.ewbible.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ewbible.app.data.auth.GoogleAuth
import com.ewbible.app.data.database.AppDatabase
import com.ewbible.app.data.database.AppPreferences
import com.ewbible.app.data.sync.SyncManager

@Composable
fun HomeScreen(
    db: AppDatabase = AppDatabase.getInstance(LocalContext.current),
    prefs: AppPreferences = AppPreferences(LocalContext.current),
    auth: GoogleAuth = GoogleAuth(LocalContext.current),
    sync: SyncManager = SyncManager()
) {
    val books by db.bibleDao().getAllBooks().collectAsState(initial = emptyList())
    val lastBook by prefs.lastBook.collectAsState(initial = "Genesis")

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "EW Bible",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "Books loaded: ${books.size}",
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                text = "Last book: $lastBook",
                modifier = Modifier.padding(top = 8.dp)
            )
            Button(
                onClick = {},
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("Open Bible")
            }
        }
    }
}
