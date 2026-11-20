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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
        detectHorizontalDragGestures(
            onHorizontalDrag = { change, delta ->
                totalDx += delta
                totalDy += abs(change.position.y - change.previousPosition.y)
                if (abs(totalDx) < abs(totalDy) * 1.5f) {
                    totalDx = 0f
                    totalDy = 0f
                }
            },
            onDragEnd = {
                if (abs(totalDx) > 80 * density) {
                    if (totalDx < 0) nextState() else prevState()
                }
                totalDx = 0f
                totalDy = 0f
            },
            onDragCancel = { totalDx = 0f; totalDy = 0f }
        )
    }
}

// MAX: Extracted VerseRow = Compose can SKIP it when its inputs didn't change.
// Before: Row was inline in items() lambda = every highlight/font change recomposed ALL visible verses (Ps 119 = 20+ rows)
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
    // MAX: background only allocated when highlighted, not Color.Transparent for 176 verses
    val rowModifier = if (highlight != Color.Transparent) {
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).background(highlight, RoundedCornerShape(8.dp))
    } else {
        Modifier.fillMaxWidth().padding(vertical = 4.dp)
    }
    Row(
        modifier = rowModifier
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
    // MAX: This overload was doing BookCatalog.id(selectedBook) on every recomposition (map lookup + string compare)
    // HomeScreen now calls the Id overload directly, this stays only for old callers
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
    val effectiveFontSize = fontSize.coerceAtLeast(18f)
    val fontSizeSp = effectiveFontSize.sp
    val lineHeight = (effectiveFontSize + 6).sp

    val safeBookId = selectedBookId.coerceIn(1, 66)
    val maxChapter = BookCatalog.chapters(safeBookId)
    val safeChapter = selectedChapter.coerceIn(1, maxChapter)
    val currentKey = safeBookId to safeChapter

    val versesToShow = versesList
    val summaryToShow = summary?.takeIf { it.bookId == safeBookId && it.chapter == safeChapter }
    val versesMatchKey = versesToShow.isEmpty() ||
        (versesToShow.first().bookId == safeBookId && versesToShow.first().chapter == safeChapter)

    var lastKey by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    LaunchedEffect(currentKey, versesMatchKey) {
        if (versesMatchKey) {
            if (lastKey != null && lastKey != currentKey) {
                if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
                    listState.scrollToItem(0)
                }
            }
            lastKey = currentKey
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
            if (summaryToShow != null) {
                item(key = "summary-${currentKey.first}-${currentKey.second}", contentType = "summary") {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = panel),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Summary", color = gold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(summaryToShow.summary, color = textColor, fontSize = fontSizeSp, lineHeight = lineHeight)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("What happened", color = gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(summaryToShow.whatHappened ?: "", color = textColor, fontSize = fontSizeSp, lineHeight = lineHeight)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Application", color = gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(summaryToShow.application ?: "", color = textColor, fontSize = fontSizeSp, lineHeight = lineHeight)
                        }
                    }
                }
            } else if (summaryLoading) {
                item(key = "summary-loading-${currentKey.first}-${currentKey.second}", contentType = "summary") {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            if (versesToShow.isEmpty()) {
                item(key = "empty-${currentKey.first}-${currentKey.second}", contentType = "empty") {
                    Text(
                        text = "Loading...",
                        color = textColor.copy(alpha = 0.6f),
                        fontSize = fontSizeSp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // MAX: items() + key = verse.verse (Int), not String.
            // Before: itemsIndexed + key = "${book}-${chapter}-${verse}" = new String for every verse on every recomposition
            // List is always 1 chapter, so verse number alone is unique.
            // MAX: items() instead of itemsIndexed = no index param, less allocation
            items(
                items = versesToShow,
                key = { it.verse },
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