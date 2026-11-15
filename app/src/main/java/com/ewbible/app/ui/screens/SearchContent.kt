package com.ewbible.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class SearchResult(val bookName: String, val bookId: Int, val chapter: Int, val verse: Int, val text: String)

@Composable
fun SearchContent(
    searchQuery: String,
    searchFilter: String,
    // FIX 6: Kept for compatibility with HomeScreen call, but IGNORED - never show book list in Search
    filteredSearchResults: List<String> = emptyList(),
    verseResults: List<SearchResult>,
    textColor: Color,
    gold: Color,
    onQueryChange: (String) -> Unit,
    onFilterChange: (String) -> Unit,
    onOpenBook: (String) -> Unit,
    onOpenVerse: (String, Int, Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onQueryChange,
            label = { Text("Search Bible...") },
            placeholder = { Text("e.g. love, faith, lamp") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(12.dp))
        // FIX 5: Categories filter VERSE results, not books
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf("All", "Gospel", "Prophecy", "Old Testament", "New Testament").forEach { filter ->
                FilterChip(
                    selected = searchFilter == filter,
                    onClick = { onFilterChange(filter) },
                    label = { Text(filter, fontSize = 12.sp) },
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        val showVerses = searchQuery.trim().length >= 2
        if (!showVerses) {
            // FIX 6: NO book list here anymore - even if filteredSearchResults is passed, we ignore it
            // FIX 5: Empty state tells user this is VERSE search
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Search verse text", color = textColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Type 2+ letters to search verses", color = textColor, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Use categories above to narrow results", color = textColor, fontSize = 13.sp)
                }
            }
        } else {
            if (verseResults.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No verses found for \"$searchQuery\"", color = textColor, fontSize = 14.sp)
                }
            } else {
                // FIX 1: bottom padding so last verse not cut
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(verseResults, key = { "${it.bookId}-${it.chapter}-${it.verse}" }) { result ->
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { onOpenVerse(result.bookName, result.chapter, result.verse) }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(
                                "${result.bookName} ${result.chapter}:${result.verse}",
                                color = gold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(result.text, color = textColor, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}