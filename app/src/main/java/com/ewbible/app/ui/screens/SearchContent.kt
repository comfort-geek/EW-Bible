package com.ewbible.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ewbible.app.data.database.BookCatalog
import com.ewbible.app.data.database.VerseSearchResult

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    val filters = listOf("All", "New Testament", "Gospel", "Old Testament", "Prophecy")
    val keyboard = LocalSoftwareKeyboardController.current
    val effectiveFontSize = fontSize.coerceAtLeast(14f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .clipToBounds()
            .padding(horizontal = 16.dp)
    ) {
        // DARK BLUE SEARCH BOX
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onQueryChange,
            placeholder = { Text("Search the Bible...", color = Color.White.copy(alpha = 0.6f)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1B2A4A),
                unfocusedContainerColor = Color(0xFF1B2A4A),
                disabledContainerColor = Color(0xFF1B2A4A),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = gold,
                focusedBorderColor = gold,
                unfocusedBorderColor = Color(0xFF2E426E),
                focusedPlaceholderColor = Color.White.copy(alpha = 0.6f),
                unfocusedPlaceholderColor = Color.White.copy(alpha = 0.6f)
            ),
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = Color.White)
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // GRID - NOT SLIDING
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 3
        ) {
            filters.forEach { filter ->
                FilterChip(
                    selected = searchFilter == filter,
                    onClick = { onFilterChange(filter) },
                    label = { Text(filter, fontSize = 12.sp, maxLines = 1) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // No hint - only placeholder is enough
        if (searchQuery.isNotBlank()) {
            if (verseResults.isEmpty()) {
                Text("No results found.", color = textColor.copy(alpha = 0.6f), fontSize = 14.sp)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(
                        verseResults,
                        key = { "${it.bookId}-${it.chapter}-${it.verse}" },
                        contentType = { "verse" }
                    ) { result ->
                        Card(
                            onClick = {
                                onOpenVerse(
                                    BookCatalog.name(result.bookId),
                                    result.chapter,
                                    result.verse
                                )
                            },
                            colors = CardDefaults.cardColors(containerColor = textColor.copy(alpha = 0.06f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
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
                                    fontSize = effectiveFontSize.sp,
                                    lineHeight = (effectiveFontSize + 8).sp,
                                    softWrap = true,
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