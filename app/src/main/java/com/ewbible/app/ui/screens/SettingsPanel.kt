package com.ewbible.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ewbible.app.CrashReportDialog
import com.ewbible.app.data.reminder.ReminderManager
import java.util.Calendar

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsPanel(
    visible: Boolean,
    isLoggedIn: Boolean,
    userName: String,
    userEmail: String,
    fontSize: Float,
    darkMode: Boolean,
    notesCount: Int,
    background: Color,
    panel: Color,
    textColor: Color,
    gold: Color,
    onClose: () -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onDarkModeChange: (Boolean) -> Unit,
    onDailyVerse: () -> Unit,
    onShowNotes: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onOpenAiChat: () -> Unit = {},
) {
    var sliderValue by remember(fontSize) { mutableFloatStateOf(fontSize.coerceIn(12f, 20f)) }
    var showLogs by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val context = LocalContext.current
    var reminderText by remember { mutableStateOf(ReminderManager.getReminderText(context)) }

    if (!visible && !showLogs) return
    val scrollState = rememberScrollState()

    if (showLogs) {
        CrashReportDialog(
            crashText = "",
            onCopy = {},
            onClear = {},
            onDismiss = { showLogs = false }
        )
    }
    if (!visible) return

    // Time Picker Dialog
    if (showTimePicker) {
        val now = Calendar.getInstance()
        val saved = ReminderManager.getReminder(context)
        val timePickerState = rememberTimePickerState(
            initialHour = saved?.first ?: now.get(Calendar.HOUR_OF_DAY),
            initialMinute = saved?.second ?: now.get(Calendar.MINUTE),
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Set Bible Reading Reminder", color = textColor) },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(onClick = {
                    ReminderManager.setReminder(context, timePickerState.hour, timePickerState.minute)
                    reminderText = ReminderManager.getReminderText(context)
                    showTimePicker = false
                    Toast.makeText(context, "Reminder set for $reminderText daily", Toast.LENGTH_SHORT).show()
                }) { Text("Set Reminder", color = gold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    // Long-press / Clear option: if a reminder exists, allow clearing
                    if (ReminderManager.getReminder(context) != null) {
                        ReminderManager.cancelReminder(context)
                        reminderText = ReminderManager.getReminderText(context)
                        Toast.makeText(context, "Reminder cancelled", Toast.LENGTH_SHORT).show()
                    }
                    showTimePicker = false
                }) { Text(if (ReminderManager.getReminder(context) != null) "Cancel Reminder" else "Close", color = Color.Red) }
            },
            containerColor = background
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClose() },
        contentAlignment = Alignment.TopEnd
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.82f)
                .widthIn(max = 340.dp)
                .background(background)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {}
                .verticalScroll(scrollState)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = textColor, modifier = Modifier.size(18.dp))
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = gold,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = if (isLoggedIn) userName.ifBlank { "My Account" } else "Guest",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isLoggedIn && userEmail.isNotBlank()) {
                        Text(
                            text = userEmail,
                            fontSize = 11.sp,
                            color = textColor.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false
                        )
                    }
                }
                if (isLoggedIn) {
                    TextButton(onClick = onSignOut, modifier = Modifier.height(32.dp)) {
                        Text("Log Out", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                } else {
                    TextButton(onClick = onGoogleSignIn, modifier = Modifier.height(32.dp)) {
                        Text("Log In", color = gold, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                }
            }

            HorizontalDivider()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .background(panel, RoundedCornerShape(10.dp))
                    .combinedClickable(
                        onClick = {
                            onClose()
                            onOpenAiChat()
                        },
                        onLongClick = { showLogs = true }
                    )
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = null,
                        tint = gold,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Ask AI", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor)
                        Text("Ask anything about the Bible", fontSize = 11.sp, color = textColor.copy(alpha = 0.6f))
                    }
                }
            }

            HorizontalDivider()

            Spacer(modifier = Modifier.height(10.dp))
            Text("Font Size", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = textColor)
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { onFontSizeChange(sliderValue) },
                valueRange = 12f..20f
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Dark Mode", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = textColor)
                Switch(checked = darkMode, onCheckedChange = onDarkModeChange)
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider()

            Button(
                onClick = onDailyVerse,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = panel),
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(Icons.Default.MenuBook, contentDescription = null, tint = textColor, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Daily Verse", color = textColor, fontSize = 13.sp)
            }

            Button(
                onClick = onShowNotes,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = panel),
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(Icons.Default.Notes, contentDescription = null, tint = textColor, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Notes ($notesCount)", color = textColor, fontSize = 13.sp)
            }

            // REMINDER - below Notes
            Button(
                onClick = { showTimePicker = true },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = panel),
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(Icons.Default.Alarm, contentDescription = null, tint = gold, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (reminderText == "Set Reminder") "Reminder" else "Reminder - $reminderText",
                    color = textColor, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}