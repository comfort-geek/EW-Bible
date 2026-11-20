package com.ewbible.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ewbible.app.data.database.BookCatalog
import com.ewbible.app.data.database.BookEntity

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
    val otCounts = remember(otBookEntities, chapterCounts) {
        otBookEntities.associate { it.id to maxOf(chapterCounts[it.id] ?: 0, BookCatalog.chapters(it.id)) }
    }
    val ntCounts = remember(ntBookEntities, chapterCounts) {
        ntBookEntities.associate { it.id to maxOf(chapterCounts[it.id] ?: 0, BookCatalog.chapters(it.id)) }
    }

    ModalDrawerSheet(
        drawerShape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp),
        // ✅ FIT: 85% on phones, max 320dp on tablets - never clips, never covers full screen
        modifier = Modifier.fillMaxWidth(0.85f).widthIn(max = 320.dp),
        drawerContainerColor = background,
        drawerContentColor = textColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            Text("Books", color = textColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            if (otBookEntities.isEmpty() && ntBookEntities.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = gold) }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    item(contentType = "header") { Text("Old Testament", color = gold, fontWeight = FontWeight.Bold, fontSize = 14.sp); Spacer(Modifier.height(8.dp)) }
                    items(otBookEntities, key = { it.id }, contentType = { "book" }) { book ->
                        BookDrawerRow(
                            book = book,
                            chapterCount = otCounts[book.id] ?: BookCatalog.chapters(book.id),
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
                    item(contentType = "header") { Spacer(modifier = Modifier.height(12.dp)); Text("New Testament", color = gold, fontWeight = FontWeight.Bold, fontSize = 14.sp); Spacer(Modifier.height(8.dp)) }
                    items(ntBookEntities, key = { it.id }, contentType = { "book" }) { book ->
                        BookDrawerRow(
                            book = book,
                            chapterCount = ntCounts[book.id] ?: BookCatalog.chapters(book.id),
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

@Composable
private fun ChapterChip(
    chapter: Int,
    isSelected: Boolean,
    bookId: Int,
    gold: Color,
    panel: Color,
    textColor: Color,
    onOpenBook: (Int, Int) -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(if (isSelected) gold else panel, RoundedCornerShape(10.dp))
            .clickable { onOpenBook(bookId, chapter) },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = chapter.toString(),
            color = if (isSelected) Color.White else textColor,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 13.sp,
        )
    }
}

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
    val chapters = remember(chapterCount) { (1..chapterCount.coerceAtLeast(1)).toList() }
    val gridHeight = remember(chapterCount) { ((chapterCount + 4) / 5 * 48).dp }

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
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier.fillMaxWidth().height(gridHeight).padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                userScrollEnabled = false
            ) {
                items(chapters, key = { it }, contentType = { "chapter" }) { chapter ->
                    ChapterChip(
                        chapter = chapter,
                        isSelected = selectedBookId == book.id && selectedChapter == chapter,
                        bookId = book.id,
                        gold = gold,
                        panel = panel,
                        textColor = textColor,
                        onOpenBook = onOpenBook
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}