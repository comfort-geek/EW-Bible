package com.ewbible.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ewbible.app.data.database.BookCatalog
import com.ewbible.app.data.database.SummaryEntity
import com.ewbible.app.data.database.VerseEntity
import kotlin.math.abs

// Swipe left = next, swipe right = prev. Same logic as Open EW: 80dp, mostly horizontal.
private fun Modifier.swipeChapter(
    onNextChapter: () -> Unit,
    onPreviousChapter: () -> Unit
): Modifier = pointerInput(onNextChapter, onPreviousChapter) {
    var totalDx = 0f
    var totalDy = 0f
    detectHorizontalDragGestures(
        onHorizontalDrag = { change, delta ->
            totalDx += delta
            totalDy += abs(change.position.y - change.previousPosition.y)
            // Don't consume vertical scroll - let LazyColumn handle it
            if (abs(totalDx) < abs(totalDy) * 1.5f) {
                totalDx = 0f
                totalDy = 0f
            }
        },
        onDragEnd = {
            // 80dp in px, not 150px - 150px felt dead on small phones
            if (abs(totalDx) > 80 * density) {
                if (totalDx < 0) onNextChapter() else onPreviousChapter()
            }
            totalDx = 0f
            totalDy = 0f
        },
        onDragCancel = { totalDx = 0f; totalDy = 0f }
    )
}

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
    summaryLoading: Boolean = false,
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
        onPreviousChapter = onPreviousChapter,
        summaryLoading = summaryLoading
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
    summaryLoading: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current

    // FIX: Same font rule as Open EW - never below 18sp
    val effectiveFontSize = fontSize.coerceAtLeast(18f)
    val lineHeight = (effectiveFontSize + 6).sp

    val safeBookId = selectedBookId.coerceIn(1, 66)
    val maxChapter = BookCatalog.chapters(safeBookId)
    val safeChapter = selectedChapter.coerceIn(1, maxChapter)
    val currentKey = safeBookId to safeChapter

    // FIX: Use DB list directly - no old -> new swap, no height jump.
    val versesToShow = versesList

    // PATCH: Only show summary if it belongs to THIS chapter.
    // ViewModel holds old summary while new one loads - showing it under new verses = height jump.
    // If it doesn't match, show nothing (loading spacer) until correct summary arrives.
    val summaryToShow = summary?.takeIf { it.bookId == safeBookId && it.chapter == safeChapter }

    // PATCH: Only scroll when verses actually belong to this chapter.
    // Before: currentKey changed -> scroll to top on OLD list -> new list arrives -> jump again.
    // Now: wait for matching verses, then scroll once.
    val versesMatchKey = versesToShow.isEmpty() ||
        (versesToShow.first().bookId == safeBookId && versesToShow.first().chapter == safeChapter)

    LaunchedEffect(currentKey, versesMatchKey) {
        if (versesMatchKey) {
            if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
                listState.scrollToItem(0)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .swipeChapter(
                onNextChapter = onNextChapter,
                onPreviousChapter = onPreviousChapter
            )
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp)
        ) {
            // Summary - key includes book+chapter so it is not reused across chapters
            if (summaryToShow != null) {
                item(key = "summary-${currentKey.first}-${currentKey.second}", contentType = "summary") {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = panel),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Summary", color = gold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(summaryToShow.summary, color = textColor, fontSize = effectiveFontSize.sp, lineHeight = lineHeight)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("What happened", color = gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(summaryToShow.whatHappened, color = textColor, fontSize = effectiveFontSize.sp, lineHeight = lineHeight)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Application", color = gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(summaryToShow.application, color = textColor, fontSize = effectiveFontSize.sp, lineHeight = lineHeight)
                        }
                    }
                }
            } else if (summaryLoading) {
                item(key = "summary-loading-${currentKey.first}-${currentKey.second}", contentType = "summary") {
                    // Keep same height as summary card so list does not jump when summary arrives
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            if (versesToShow.isEmpty()) {
                item(key = "empty-${currentKey.first}-${currentKey.second}", contentType = "empty") {
                    Text(
                        text = "Loading...",
                        color = textColor.copy(alpha = 0.6f),
                        fontSize = effectiveFontSize.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // FIX: Stable key + contentType stops Compose reusing wrong verse row on scroll/chapter change
            itemsIndexed(
                versesToShow,
                key = { _, verse -> "${verse.bookId}-${verse.chapter}-${verse.verse}" },
                contentType = { _, _ -> "verse" }
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
                        text = verse.verse.toString(),
                        color = gold,
                        fontWeight = FontWeight.Bold,
                        fontSize = effectiveFontSize.sp,
                        // FIX: Fixed width + no wrap for number, so verse text column never shifts
                        maxLines = 1,
                        modifier = Modifier.width(32.dp),
                    )
                    Text(
                        verse.text,
                        color = textColor,
                        fontSize = effectiveFontSize.sp,
                        lineHeight = lineHeight,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}