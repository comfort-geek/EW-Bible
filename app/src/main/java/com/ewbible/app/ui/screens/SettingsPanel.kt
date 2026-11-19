package com.ewbible.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    // FIXED: Moved above early-return + Saveable, so it survives open/close + rotation
    var resourcesExpanded by rememberSaveable { mutableStateOf(false) }
    // FIXED: Local slider state - only commit once on release, not 50x per drag
    var sliderValue by remember(fontSize) { mutableFloatStateOf(fontSize.coerceIn(12f, 24f)) }

    if (!visible) return

    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            // FIXED: No ripple on scrim, no indication
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClose() },
        contentAlignment = Alignment.TopEnd
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.85f)
                .background(background)
                // FIXED: Actually consume clicks, don't let them fall through to scrim
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {}
                .verticalScroll(scrollState)
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = textColor)
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = gold,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isLoggedIn) userName.ifBlank { "My Account" } else "Guest",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    if (isLoggedIn && userEmail.isNotBlank()) {
                        Text(userEmail, fontSize = 13.sp, color = textColor.copy(alpha = 0.6f))
                    }
                }
                if (isLoggedIn) {
                    TextButton(onClick = onSignOut) {
                        Text("Log Out", color = Color.Red, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                } else {
                    TextButton(onClick = onGoogleSignIn) {
                        Text("Log In", color = gold, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { resourcesExpanded = !resourcesExpanded }
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Resources", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textColor)
                Text(if (resourcesExpanded) "▲" else "▼", fontSize = 14.sp, color = textColor)
            }

            if (resourcesExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(panel, RoundedCornerShape(12.dp))
                        .clickable {
                            onClose()
                            onOpenAiChat()
                        }
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            tint = gold,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Ask Gemini", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textColor)
                            Text("Ask anything about the Bible", fontSize = 13.sp, color = textColor.copy(alpha = 0.6f))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            HorizontalDivider()

            Spacer(modifier = Modifier.height(16.dp))
            Text("Font Size", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = textColor)
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it }, // local only - instant UI, no DataStore spam
                onValueChangeFinished = { onFontSizeChange(sliderValue) }, // 1 write on release
                valueRange = 12f..24f
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Dark Mode", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = textColor)
                Switch(checked = darkMode, onCheckedChange = onDarkModeChange)
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()

            Button(
                onClick = onDailyVerse,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = panel),
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(Icons.Default.MenuBook, contentDescription = null, tint = textColor)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Daily Verse", color = textColor, fontSize = 16.sp)
            }

            Button(
                onClick = onShowNotes,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = panel),
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(Icons.Default.Notes, contentDescription = null, tint = textColor)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Notes ($notesCount)", color = textColor, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}