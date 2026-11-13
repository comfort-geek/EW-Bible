package com.ewbible.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NotesCard(
    noteText: String,
    panel: Color,
    gold: Color,
    textColor: Color,
    onNoteTextChange: (String) -> Unit,
    onSaveNote: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = panel),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Notes", color = gold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = noteText,
                onValueChange = onNoteTextChange,
                label = { Text("Write your note...") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onSaveNote, modifier = Modifier.fillMaxWidth()) { Text("Save Note") }
        }
    }
}

