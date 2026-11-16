package com.ewbible.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { -it }),
        exit = slideOutHorizontally(targetOffsetX = { -it })
    ) {
        // Scrim + panel: panel is left side, top to bottom
        Box(modifier = Modifier.fillMaxSize()) {
            // Tap outside to close
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
            ) {
                // Empty - click handled by panel below via Box clickable
            }
            // LEFT PANEL - top to bottom
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.85f)
                    .background(background)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // TOP ROW: Title + X to close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Settings", color = textColor, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = textColor)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // --- ACCOUNT SECTION ---
                Text("Account", color = gold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))

                if (!isLoggedIn) {
                    Text(
                        "Sign in to sync your notes and highlights across devices.",
                        color = textColor.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onGoogleSignIn,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = panel)
                    ) {
                        Text("Login with Google", color = textColor, fontSize = 15.sp)
                    }
                } else {
                    // Show name + email
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = gold, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(userName, color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(userEmail, color = textColor.copy(alpha = 0.7f), fontSize = 13.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Syncing notes & highlights to $userEmail",
                        color = gold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onSignOut,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor)
                    ) {
                        Text("Sign Out", color = textColor, fontSize = 15.sp)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = textColor.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(16.dp))

                // --- READING ---
                Text("Reading", color = gold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("A-", color = textColor, fontSize = 13.sp)
                    Slider(
                        value = fontSize,
                        onValueChange = onFontSizeChange,
                        valueRange = 12f..28f,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    Text("A+", color = textColor, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Dark Mode", color = textColor, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Switch(checked = darkMode, onCheckedChange = onDarkModeChange)
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = textColor.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDailyVerse,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = panel)
                ) { Text("Daily Verse", color = textColor, fontSize = 14.sp) }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onShowNotes,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = panel)
                ) { Text("Notes ($notesCount)", color = textColor, fontSize = 14.sp) }
            }
        }
    }
}