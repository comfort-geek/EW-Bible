package com.ewbible.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ewbible.app.data.database.BookCatalog
import com.ewbible.app.data.database.VerseSearchResult

@OptIn(ExperimentalMaterial3Api::class)
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
    onOpenVerse: (book: String, chapter: Int, verse: Int) -> Unit
) {
    val filters = listOf("All", "Old Testament", "New Testament", "Prophecy", "Gospel")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .clipToBounds() // kills words leaking on right edge
            .padding(horizontal = 16.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onQueryChange,
            placeholder = { Text("Search the Bible...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        // All | Old Testament | New Testament | Prophecy | Gospel
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 16.dp)
        ) {
            items(filters) { filter ->
                FilterChip(
                    selected = searchFilter == filter,
                    onClick = { onFilterChange(filter) },
                    label = { Text(filter) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // NO "Type to search verses." - show nothing when blank
        if (searchQuery.isNotBlank()) {
            if (verseResults.isEmpty()) {
                Text("No results found.", color = textColor.copy(alpha = 0.6f), fontSize = 14.sp)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(verseResults, key = { "${it.bookId}-${it.chapter}-${it.verse}" }) { result ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = textColor.copy(alpha = 0.06f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth() // NOT fixed width
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onOpenVerse(
                                            BookCatalog.name(result.bookId),
                                            result.chapter,
                                            result.verse
                                        )
                                    }
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = "${BookCatalog.name(result.bookId)} ${result.chapter}:${result.verse}",
                                    color = gold,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = result.text,
                                    color = textColor,
                                    fontSize = fontSize.sp,
                                    lineHeight = (fontSize + 8).sp,
                                    softWrap = true, // wraps, never cuts
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}