package com.ewbible.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope.weight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ewbible.app.data.database.BookCatalog
import com.ewbible.app.data.database.SummaryEntity
import com.ewbible.app.data.database.VerseEntity
import kotlin.math.abs

@Composable
private fun Modifier.swipeChapter(
    onNextChapter: () -> Unit,
    onPreviousChapter: () -> Unit
): Modifier {
    val nextState by rememberUpdatedState(onNextChapter)
    val prevState by rememberUpdatedState(onPreviousChapter)
    return pointerInput(Unit) {
        var totalDx = 0f
        var totalDy = 0f
        var fired = false
        detectHorizontalDragGestures(
            onHorizontalDrag = { change, delta ->
                if (fired) return@detectHorizontalDragGestures
                totalDx += delta
                totalDy += abs(change.position.y - change.previousPosition.y)
                // INSTANT: fire as soon as horizontal wins, don't wait for finger lift
                if (abs(totalDx) > 90 * density && abs(totalDx) > abs(totalDy) * 1.5f) {
                    fired = true
                    if (totalDx < 0) nextState() else prevState()
                }
            },
            onDragEnd = { totalDx = 0f; totalDy = 0f; fired = false },
            onDragCancel = { totalDx = 0f; totalDy = 0f; fired = false }
        )
    }
}

@Composable
private fun VerseRow(
    verse: VerseEntity,
    highlight: Color,
    fontSizeSp: TextUnit,
    lineHeight: TextUnit,
    gold: Color,
    textColor: Color,
    onVerseClick: (Int) -> Unit,
    onVerseLongClick: (Int) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    // FAST: don't allocate 2 different Modifiers + clip + background on every recomposition
    // for un-highlighted verses (99% of rows). Transparent highlight = plain Row.
    val isHighlighted = highlight != Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(if (isHighlighted) Modifier.clip(RoundedCornerShape(8.dp)).background(highlight, RoundedCornerShape(8.dp)) else Modifier)
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
            fontSize = fontSizeSp,
            maxLines = 1,
            modifier = Modifier.width(32.dp),
        )
        Text(
            verse.text,
            color = textColor,
            fontSize = fontSizeSp,
            lineHeight = lineHeight,
            modifier = Modifier.weight(1f)
        )
    }
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
    val bookId = remember(selectedBook) { BookCatalog.id(selectedBook) }
    BibleContent(
        selectedBookId = bookId,
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
    val effectiveFontSize = fontSize.coerceIn(12f, 20f)
    val fontSizeSp = effectiveFontSize.sp
    val lineHeight = (effectiveFontSize + 6).sp

    val safeBookId = selectedBookId.coerceIn(1, 66)
    val maxChapter = remember(safeBookId) { BookCatalog.chapters(safeBookId) }
    val safeChapter = selectedChapter.coerceIn(1, maxChapter)
    val currentKey = remember(safeBookId, safeChapter) { safeBookId to safeChapter }

    // FAST 1: NEVER mutate state during composition.
    // Before: cachedVerses = versesList / cachedSummary = summary directly
    // in composition = recomposition loop on every swipe.
    // Now: last-good cache only updates in LaunchedEffect.
    var cachedVerses by remember { mutableStateOf<List<VerseEntity>>(emptyList()) }
    var cachedVersesKey by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var cachedSummary by remember { mutableStateOf<SummaryEntity?>(null) }
    var cachedSummaryKey by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    LaunchedEffect(versesList, currentKey) {
        // Only swap when incoming verses are actually for THIS chapter
        if (versesList.isNotEmpty()) {
            val first = versesList.first()
            if (first.bookId == safeBookId && first.chapter == safeChapter) {
                if (cachedVersesKey != currentKey || cachedVerses != versesList) {
                    cachedVerses = versesList
                    cachedVersesKey = currentKey
                }
            }
        }
    }
    LaunchedEffect(summary, currentKey) {
        // Summary runs independently of verses - never waits for verses
        if (summary != null && summary.bookId == safeBookId && summary.chapter == safeChapter) {
            if (cachedSummaryKey != currentKey || cachedSummary != summary) {
                cachedSummary = summary
                cachedSummaryKey = currentKey
            }
        }
        // If summary is null for new chapter, keep old summary on screen
        // until new one arrives. Don't blank it.
    }

    val versesToShow = cachedVerses
    val summaryToShow = cachedSummary
    val showSummary = summaryToShow != null && cachedSummaryKey == currentKey

    LaunchedEffect(currentKey) {
        // Scroll to top immediately on key change, don't wait for verses
        if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
            listState.scrollToItem(0)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .swipeChapter(
                onNextChapter = onNextChapter,
                onPreviousChapter = onPreviousChapter
            )
    ) {
        val isWide = maxWidth > 600.dp
        val horizontalPadding = if (isWide) 32.dp else 18.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.TopCenter)
                .widthIn(max = 720.dp)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = horizontalPadding),
                contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp)
            ) {
                if (showSummary) {
                    item(key = "summary-${currentKey.first}-${currentKey.second}", contentType = "summary") {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            colors = CardDefaults.cardColors(containerColor = panel),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Summary", color = gold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(summaryToShow!!.summary, color = textColor, fontSize = fontSizeSp, lineHeight = lineHeight)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("What happened", color = gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(summaryToShow.whatHappened ?: "", color = textColor, fontSize = fontSizeSp, lineHeight = lineHeight)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Application", color = gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(summaryToShow.application ?: "", color = textColor, fontSize = fontSizeSp, lineHeight = lineHeight)
                            }
                        }
                    }
                }

                // FAST 2: Key must be unique per chapter.
                // Before: key = { it.verse } - verse 1 in Gen 1 == verse 1 in Gen 2,
                // Compose reused the old row. Now: book-chapter-verse.
                items(
                    items = versesToShow,
                    key = { "${currentKey.first}-${currentKey.second}-${it.verse}" },
                    contentType = { "verse" }
                ) { verse ->
                    VerseRow(
                        verse = verse,
                        highlight = currentHighlightMap[verse.verse] ?: Color.Transparent,
                        fontSizeSp = fontSizeSp,
                        lineHeight = lineHeight,
                        gold = gold,
                        textColor = textColor,
                        onVerseClick = onVerseClick,
                        onVerseLongClick = onVerseLongClick
                    )
                }
            }
        }
    }
}