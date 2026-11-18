package com.ewbible.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ewbible.app.data.database.BookCatalog
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
    onNextChapter: () -> Unit = {},
    onPreviousChapter: () -> Unit = {},
) {
    BibleContent(
        selectedBookId = BookCatalog.id(selectedBook),
        selectedChapter = selectedChapter,
        summary = summary,
        versesList = versesList,
        currentHighlightMap = currentHighlightMap,
        fontSize = fontSize,
        gold = gold,
        panel = panel,
        textColor = textColor,
        listState = listState,
        onVerseClick = onVerseClick,
        onVerseLongClick = onVerseLongClick,
        onNextChapter = onNextChapter,
        onPreviousChapter = onPreviousChapter
    )
}

@Composable
fun BibleContent(
    selectedBookId: Int,
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
    onNextChapter: () -> Unit = {},
    onPreviousChapter: () -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current

    val safeBookId = selectedBookId.coerceIn(1, 66)
    val bookName = BookCatalog.name(safeBookId)
    val maxChapter = BookCatalog.chapters(safeBookId)
    val safeChapter = selectedChapter.coerceIn(1, maxChapter)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp)
    ) {
        item(key = "summary-$safeBookId-$safeChapter") {
            if (summary != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = panel),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Summary", color = gold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(summary.summary, color = textColor, fontSize = fontSize.sp, lineHeight = (fontSize + 6).sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("What happened", color = gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(summary.whatHappened, color = textColor, fontSize = fontSize.sp, lineHeight = (fontSize + 6).sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Application", color = gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(summary.application, color = textColor, fontSize = fontSize.sp, lineHeight = (fontSize + 6).sp)
                    }
                }
            }
        }

        if (versesList.isEmpty()) {
            item(key = "empty-$safeBookId-$safeChapter") {
                Text(
                    text = "No verses loaded for $bookName $safeChapter.",
                    color = textColor.copy(alpha = 0.6f),
                    fontSize = fontSize.sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        itemsIndexed(
            versesList,
            key = { _, verse -> "${verse.bookId}-${verse.chapter}-${verse.verse}" }
        ) { _, verse ->
            val verseBackground = currentHighlightMap[verse.verse] ?: Color.Transparent
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(verseBackground, RoundedCornerShape(8.dp))
                    .combinedClickable(
                        onClick = { onVerseClick(verse.verse) },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onVerseLongClick(verse.verse)
                        },
                    )
                    .padding(vertical = 8.dp, horizontal = 8.dp),
            ) {
                Text(
                    text = "${verse.verse}",
                    color = gold,
                    fontWeight = FontWeight.Bold,
                    fontSize = fontSize.sp,
                    modifier = Modifier.width(32.dp),
                )
                Text(
                    verse.text,
                    color = textColor,
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize + 6).sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}