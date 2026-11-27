package com.ewbible.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.key
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

    var lastBook by remember { mutableStateOf(safeBookId) }
    var lastChapter by remember { mutableStateOf(safeChapter) }
    var lastVerses by remember { mutableStateOf<List<VerseEntity>>(emptyList()) }
    var lastSummary by remember { mutableStateOf<SummaryEntity?>(null) }
    var lastHighlights by remember { mutableStateOf<Map<Int, Color>>(emptyMap()) }

    val firstVerse = versesList.firstOrNull()
    val versesAreForThisChapter = firstVerse != null &&
        firstVerse.bookId == safeBookId && firstVerse.chapter == safeChapter
    val summaryIsForThisChapter = summary != null &&
        summary.bookId == safeBookId && summary.chapter == safeChapter

    val isComplete = versesAreForThisChapter && !summaryLoading &&
        (summaryIsForThisChapter || summary == null)

    if (isComplete) {
        lastBook = safeBookId
        lastChapter = safeChapter
        lastVerses = versesList
        lastSummary = summary
        lastHighlights = currentHighlightMap
    }

    val hasLast = lastVerses.isNotEmpty()
    val displayBook = if (hasLast) lastBook else safeBookId
    val displayChapter = if (hasLast) lastChapter else safeChapter
    val versesToShow = if (hasLast) lastVerses else versesList
    val summaryToShow = if (hasLast) lastSummary else summary
    val highlightsToShow = if (hasLast) lastHighlights else currentHighlightMap
    val showSummary = summaryToShow != null &&
        summaryToShow.bookId == displayBook && summaryToShow.chapter == displayChapter

    // FIX: Don't scrollToItem in LaunchedEffect on every chapter change.
    // It suspends and freezes the swipe. LazyColumn with key() below
    // already recreates and starts at top.
    // Keep it only if user manually scrolled and chapter didn't change - removed.

    // Book name for header - from your catalog order 1..66
    val displayBookName = remember(displayBook) {
        runCatching { BookCatalog.allDisplayNames()[displayBook - 1] }.getOrDefault("Book $displayBook")
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
        // FIX: NO swipeChapter here anymore. Was covering whole screen = lag.
    ) {
        val isWide = maxWidth > 600.dp
        val horizontalPadding = if (isWide) 32.dp else 18.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // FIX: Swipe ONLY on this header. Verses scroll freely below.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .swipeChapter(
                        onNextChapter = onNextChapter,
                        onPreviousChapter = onPreviousChapter
                    )
                    .padding(start = horizontalPadding, end = horizontalPadding, top = 8.dp, bottom = 8.dp)
            ) {
                Text(
                    text = "$displayBookName $displayChapter",
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }

            key(displayBook, displayChapter) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .align(Alignment.CenterHorizontally)
                        .widthIn(max = 720.dp)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = horizontalPadding),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp)
                    ) {
                        if (showSummary) {
                            item(key = "summary-$displayBook-$displayChapter", contentType = "summary") {
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

                        items(
                            items = versesToShow,
                            key = { it.verse },
                            contentType = { "verse" }
                        ) { verse ->
                            VerseRow(
                                verse = verse,
                                highlight = highlightsToShow[verse.verse] ?: Color.Transparent,
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
    }
}