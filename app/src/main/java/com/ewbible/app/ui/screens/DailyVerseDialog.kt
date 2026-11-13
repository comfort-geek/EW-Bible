package com.ewbible.app.ui.screens

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

@Composable
fun DailyVerseDialog(
    dailyVerseText: String,
    gold: Color,
    textColor: Color,
    fontSize: Float,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily Verse", color = gold, fontWeight = FontWeight.Bold) },
        text = { Text(dailyVerseText, color = textColor, fontSize = fontSize.sp, lineHeight = (fontSize + 8).sp, textAlign = TextAlign.Center) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

