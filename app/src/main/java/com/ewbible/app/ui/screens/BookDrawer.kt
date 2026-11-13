package com.ewbible.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.ewbible.app.data.database.AppDatabase
import com.ewbible.app.data.database.BookEntity

@Composable
fun BookDrawer(
    booksList: List<BookEntity>,
    otBookEntities: List<BookEntity>,
    ntBookEntities: List<BookEntity>,
    selectedBook: String,
    selectedChapter: Int,
    expandedBook: String?,
    background: Color,
    textColor: Color,
    panel: Color,
    gold: Color,
    secondaryText: Color,
    onOpenBook: (String, Int) -> Unit,
    onSelectBook: (String) -> Unit,
    onToggleBook: (String) -> Unit,
    db: AppDatabase,
) {
    ModalDrawerSheet(
        drawerShape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp),
        modifier = Modifier.width(300.dp),
        drawerContainerColor = background,
        drawerContentColor = textColor,
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Books", color = textColor, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            if (booksList.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = gold) }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item { Text("Old Testament", color = gold, fontWeight = FontWeight.Bold, fontSize = 14.sp); Spacer(Modifier.height(8.dp)) }
                    items(otBookEntities, key = { it.id }) { book ->
                        BookDrawerRow(book, selectedBook, selectedChapter, expandedBook, textColor, panel, gold, secondaryText, db, onOpenBook, onSelectBook, onToggleBook)
                    }
                    item { Spacer(Modifier.height(12.dp)); Text("New Testament", color = gold, fontWeight = FontWeight.Bold, fontSize = 14.sp); Spacer(Modifier.height(8.dp)) }
                    items(ntBookEntities, key = { it.id }) { book ->
                        BookDrawerRow(book, selectedBook, selectedChapter, expandedBook, textColor, panel, gold, secondaryText, db, onOpenBook, onSelectBook, onToggleBook)
                    }
                }
            }
        }
    }
}

@Composable
private fun BookDrawerRow(
    book: BookEntity,
    selectedBook: String,
    selectedChapter: Int,
    expandedBook: String?,
    textColor: Color,
    panel: Color,
    gold: Color,
    secondaryText: Color,
    db: AppDatabase,
    onOpenBook: (String, Int) -> Unit,
    onSelectBook: (String) -> Unit,
    onToggleBook: (String) -> Unit,
) {
    val isSelected = selectedBook == book.name
    val isExpanded = expandedBook == book.name
    val chapterCount by db.bibleDao().getMaxChapter(book.id).collectAsState(initial = 1)
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().clickable {
                onToggleBook(book.name)
                if (!isExpanded) onSelectBook(book.name)
            }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(book.name, color = if (isSelected) gold else textColor, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(if (isExpanded) "▾" else "▸", color = secondaryText)
        }
        if (isExpanded) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (chapter in 1..chapterCount.coerceAtLeast(1)) {
                    val isChapterSelected = selectedBook == book.name && selectedChapter == chapter
                    Text(
                        text = "Ch $chapter",
                        color = if (isChapterSelected) Color.White else textColor,
                        fontWeight = if (isChapterSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.background(if (isChapterSelected) gold else panel, RoundedCornerShape(8.dp))
                            .clickable { onOpenBook(book.name, chapter) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}
