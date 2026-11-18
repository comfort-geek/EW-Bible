package com.ewbible.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ewbible.app.data.database.BookCatalog
import com.ewbible.app.data.database.VerseSearchResult

@Composable
fun SearchContent(
    searchQuery: String,
    searchFilter: String,
    verseResults: List<VerseSearchResult>,
    textColor: Color,
    gold: Color,
    fontSize: Float,
    onQueryChange: (String) -> Unit,
    onFilterChange: (String) -> Unit,
    onOpenVerse: (book: Int, chapter: Int, verse: Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onQueryChange,
            label = { Text("Search the Bible...", color = textColor.copy(alpha = 0.6f)) },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            singleLine = true
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("All", "Old Testament", "New Testament", "Gospel", "Prophecy").forEach { filter ->
                val selected = searchFilter == filter
                Text(
                    text = filter,
                    color = if (selected) Color.White else textColor,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clickable { onFilterChange(filter) }
                        .background(
                            color = if (selected) gold else textColor.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (searchQuery.isBlank()) {
            Text("Type to search verses.", color = textColor.copy(alpha = 0.6f), fontSize = 14.sp, modifier = Modifier.padding(16.dp))
            return@Column
        }
        if (verseResults.isEmpty()) {
            Text("No results for \"$searchQuery\".", color = textColor.copy(alpha = 0.6f), fontSize = 14.sp, modifier = Modifier.padding(16.dp))
            return@Column
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(verseResults) { result ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenVerse(result.bookId, result.chapter, result.verse) }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        "${BookCatalog.name(result.bookId)} ${result.chapter}:${result.verse}",
                        color = gold, fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(result.text, color = textColor, fontSize = fontSize.sp)
                }
            }
        }
    }
}