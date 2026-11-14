package com.ewbible.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ewbible.app.data.database.SummaryEntity
import com.ewbible.app.data.database.VerseEntity

@Composable
fun BibleContent(
    selectedBook: String,
    selectedChapter: Int,
    summary: SummaryEntity?,
    versesList: List<VerseEntity>,
    currentHighlightMap: Map<Int, Color>,
    fontSize: Float,
    gold: Color,
    panel: Color,
    textColor: Color,
    listState: LazyListState,
    onVerseClick: (Int) -> Unit,
    onVerseLongClick: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "$selectedBook $selectedChapter",
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
        )
        if (versesList.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading verses...", color = textColor, fontSize = fontSize.sp)
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                summary?.let { item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = panel),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Summary", color = gold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(summary.summary, color = textColor, fontSize = fontSize.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("What happened", color = gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(summary.whatHappened, color = textColor, fontSize = fontSize.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Application", color = gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(summary.application, color = textColor, fontSize = fontSize.sp)
                        }
                    }
                } }
                itemsIndexed(versesList, key = { _, verse -> verse.verse }) { _, verse ->
                    val verseBackground = currentHighlightMap[verse.verse] ?: Color.Transparent
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(verseBackground, RoundedCornerShape(8.dp))
                            .combinedClickable(
                                onClick = { onVerseClick(verse.verse) },
                                onLongClick = { onVerseLongClick(verse.verse) },
                            )
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                    ) {
                        Text(
                            text = "${verse.verse}",
                            color = gold,
                            fontWeight = FontWeight.Bold,
                            fontSize = fontSize.sp,
                            modifier = Modifier.width(32.dp),
                        )
                        Text(verse.text, color = textColor, fontSize = fontSize.sp, lineHeight = (fontSize + 6).sp)
                    }
                }
            }
        }
    }
}