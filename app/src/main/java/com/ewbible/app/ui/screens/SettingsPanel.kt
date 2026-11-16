package com.ewbible.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    onOpenResource: () -> Unit = {},
) {
    var resourcesExpanded by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { -it }),
        exit = slideOutHorizontally(targetOffsetX = { -it })
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onClose() }
            )
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.85f)
                    .background(background)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = textColor)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = textColor.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(16.dp))

                if (!isLoggedIn) {
                    Button(
                        onClick = onGoogleSignIn,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = panel)
                    ) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = textColor)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = gold, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        IconButton(onClick = onSignOut) {
                            Icon(Icons.Default.ExitToApp, contentDescription = null, tint = textColor)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = textColor.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(16.dp))

                // --- RESOURCES DROPDOWN - BELOW LOG IN / LOG OUT ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { resourcesExpanded = !resourcesExpanded }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Resources",
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = if (resourcesExpanded) "▲" else "▼",
                        color = gold,
                        fontSize = 12.sp
                    )
                }

                if (resourcesExpanded) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = panel),
                        modifier = Modifier.fillMaxWidth().clickable { onOpenResource() }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Bible.com",
                                    color = textColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "YouVersion - Most used Bible in the world",
                                    color = textColor.copy(alpha = 0.6f),
                                    fontSize = 13.sp
                                )
                            }
                            Text(text = "↗", color = gold, fontSize = 20.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = textColor.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FormatSize, contentDescription = null, tint = textColor)
                    Slider(
                        value = fontSize,
                        onValueChange = onFontSizeChange,
                        valueRange = 12f..28f,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DarkMode, contentDescription = null, tint = textColor, modifier = Modifier.weight(1f))
                    Switch(checked = darkMode, onCheckedChange = onDarkModeChange)
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = textColor.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDailyVerse,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = panel)
                ) {
                    Icon(Icons.Default.Book, contentDescription = null, tint = textColor)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onShowNotes,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = panel)
                ) {
                    Icon(Icons.Default.EditNote, contentDescription = null, tint = textColor)
                }
            }
        }
    }
}