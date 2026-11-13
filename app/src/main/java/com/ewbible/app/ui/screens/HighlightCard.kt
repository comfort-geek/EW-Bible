package com.ewbible.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ewbible.app.data.database.AppDatabase
import com.ewbible.app.data.database.HighlightEntity
import com.ewbible.app.data.sync.SyncManager
import kotlinx.coroutines.launch

@Composable
fun HighlightCard(
    selectedVerse: Int?,
    currentBookId: Int,
    selectedChapter: Int,
    panel: Color,
    gold: Color,
    textColor: Color,
    db: AppDatabase,
    isLoggedIn: Boolean,
    syncManager: SyncManager,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = panel)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Highlight Verse ${selectedVerse ?: 1}", color = gold, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(Color.Yellow, Color.Green, Color(0xFFFFB6C1), Color.Cyan, Color(0xFFFFA500)).forEach { color ->
                    Box(
                        modifier = Modifier.size(34.dp).background(color, RoundedCornerShape(8.dp)).clickable {
                            selectedVerse?.let { verse ->
                                scope.launch {
                                    db.bibleDao().saveHighlight(HighlightEntity(bookId = currentBookId, chapter = selectedChapter, verse = verse, color = color.value.toString()))
                                    if (isLoggedIn) syncManager.uploadHighlight(currentBookId, selectedChapter, verse, color.value.toString())
                                }
                            }
                            onDismiss()
                        },
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = {
                selectedVerse?.let { verse ->
                    scope.launch {
                        db.bibleDao().deleteHighlight(currentBookId, selectedChapter, verse)
                        if (isLoggedIn) syncManager.deleteHighlight(currentBookId, selectedChapter, verse)
                    }
                }
                onDismiss()
            }) { Text("Remove Highlight") }
        }
    }
}
