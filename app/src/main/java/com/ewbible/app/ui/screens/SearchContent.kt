package com.ewbible.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SearchContent(
    searchQuery: String,
    searchFilter: String,
    filteredSearchResults: List<String>,
    textColor: Color,
    gold: Color,
    onQueryChange: (String) -> Unit,
    onFilterChange: (String) -> Unit,
    onOpenBook: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onQueryChange,
            label = { Text("Search Bible...") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
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
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(filteredSearchResults) { bookName ->
                Text(
                    bookName,
                    color = textColor,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().clickable { onOpenBook(bookName) }.padding(vertical = 8.dp),
                )
            }
        }
    }
}

