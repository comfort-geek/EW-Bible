package com.ewbible.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ewbible.app.data.database.BookCatalog
import com.ewbible.app.data.database.BookEntity

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookDrawer(
    otBookEntities: List<BookEntity>,
    ntBookEntities: List<BookEntity>,
    chapterCounts: Map<Int, Int>,
    selectedBookId: Int,
    selectedChapter: Int,
    expandedBookId: Int?,
    background: Color,
    textColor: Color,
    panel: Color,
    gold: Color,
    secondaryText: Color,
    onOpenBook: (Int, Int) -> Unit,
    onSelectBook: (Int) -> Unit,
    onToggleBook: (Int) -> Unit,
) {
    ModalDrawerSheet(
        drawerShape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp),
        modifier = Modifier.width(300.dp),
        drawerContainerColor = background,
        drawerContentColor = textColor,
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Books", color = textColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            if (otBookEntities.isEmpty() && ntBookEntities.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = gold) }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
                    item { Text("Old Testament", color = gold, fontWeight = FontWeight.Bold, fontSize = 14.sp); Spacer(Modifier.height(8.dp)) }
                    items(otBookEntities, key = { it.id }) { book ->
                        BookDrawerRow(
                            book = book,
                            // FIXED: maxOf, not ?: — a wrong DB value (James=1) can no longer hide Catalog's 5
                            chapterCount = maxOf(chapterCounts[book.id] ?: 0, BookCatalog.chapters(book.id)),
                            selectedBookId = selectedBookId,
                            selectedChapter = selectedChapter,
                            expandedBookId = expandedBookId,
                            textColor = textColor,
                            panel = panel,
                            gold = gold,
                            secondaryText = secondaryText,
                            onOpenBook = onOpenBook,
                            onSelectBook = onSelectBook,
                            onToggleBook = onToggleBook
                        )
                    }
                    item { Spacer(modifier = Modifier.height(12.dp)); Text("New Testament", color = gold, fontWeight = FontWeight.Bold, fontSize = 14.sp); Spacer(Modifier.height(8.dp)) }
                    items(ntBookEntities, key = { it.id }) { book ->
                        BookDrawerRow(
                            book = book,
                            // FIXED: same maxOf here
                            chapterCount = maxOf(chapterCounts[book.id] ?: 0, BookCatalog.chapters(book.id)),
                            selectedBookId = selectedBookId,
                            selectedChapter = selectedChapter,
                            expandedBookId = expandedBookId,
                            textColor = textColor,
                            panel = panel,
                            gold = gold,
                            secondaryText = secondaryText,
                            onOpenBook = onOpenBook,
                            onSelectBook = onSelectBook,
                            onToggleBook = onToggleBook
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookDrawerRow(
    book: BookEntity,
    chapterCount: Int,
    selectedBookId: Int,
    selectedChapter: Int,
    expandedBookId: Int?,
    textColor: Color,
    panel: Color,
    gold: Color,
    secondaryText: Color,
    onOpenBook: (Int, Int) -> Unit,
    onSelectBook: (Int) -> Unit,
    onToggleBook: (Int) -> Unit,
) {
    val isSelected = selectedBookId == book.id
    val isExpanded = expandedBookId == book.id
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().clickable {
                onToggleBook(book.id)
                if (!isExpanded) onSelectBook(book.id)
            }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(book.name, color = if (isSelected) gold else textColor, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(if (isExpanded) "▾" else "▸", color = secondaryText)
        }
        if (isExpanded) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                FlowRow(
                    modifier = Modifier.width(232.dp).padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (chapter in 1..chapterCount.coerceAtLeast(1)) {
                        val isChapterSelected = selectedBookId == book.id && selectedChapter == chapter
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    if (isChapterSelected) gold else panel,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onOpenBook(book.id, chapter) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$chapter",
                                color = if (isChapterSelected) Color.White else textColor,
                                fontWeight = if (isChapterSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}