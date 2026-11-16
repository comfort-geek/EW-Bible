package com.ewbible.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsPanel(
    onClose: () -> Unit,
    onBibleComClick: () -> Unit,
    onDailyVerseClick: () -> Unit,
    onNotesClick: () -> Unit,
    isDarkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    fontSize: Float,
    onFontSizeChange: (Float) -> Unit,
    userName: String? = null,
    isLoggedIn: Boolean = false,
    onLoginClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {}
) {
    var resourcesExpanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    // Full screen dim background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable { onClose() },
        contentAlignment = Alignment.TopEnd
    ) {
        // Panel itself - anchored to RIGHT
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.85f)
                .background(Color(0xFFFFF8EE))
                .clickable(enabled = false) {}
                .verticalScroll(scrollState)
                .padding(20.dp)
        ) {
            // 1. TOP ROW: X to close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            // 2. PROFILE: Icon + Name FROM SIGN-IN + Log Out / Log In
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color(0xFFD4A017),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (isLoggedIn) (userName ?: "My Account") else "Guest",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.weight(1f)
                )

                if (isLoggedIn) {
                    TextButton(onClick = onLogoutClick) {
                        Text(
                            text = "Log Out",
                            color = Color.Red,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    TextButton(onClick = onLoginClick) {
                        Text(
                            text = "Log In",
                            color = Color(0xFFD4A017),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Divider()

            // 3. RESOURCES
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { resourcesExpanded = !resourcesExpanded }
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Resources", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(if (resourcesExpanded) "▲" else "▼", fontSize = 14.sp)
            }

            if (resourcesExpanded) {
                // Bible.com ONLY - no subtitle, no sentence
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF5EEDD), RoundedCornerShape(12.dp))
                        .clickable { onBibleComClick() }
                        .padding(16.dp)
                ) {
                    Text("Bible.com", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Divider()

            // 4. FONT SIZE
            Spacer(modifier = Modifier.height(16.dp))
            Slider(value = fontSize, onValueChange = onFontSizeChange)
            Spacer(modifier = Modifier.height(16.dp))

            // 5. DARK MODE - with label + button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Dark Mode", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Switch(checked = isDarkMode, onCheckedChange = onDarkModeChange)
            }

            Spacer(modifier = Modifier.height(24.dp))
            Divider()

            // 6. DAILY VERSE - with label
            Button(
                onClick = onDailyVerseClick,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5EEDD)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(Icons.Default.MenuBook, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Daily Verse", color = Color.Black, fontSize = 16.sp)
            }

            // 7. NOTES - with label
            Button(
                onClick = onNotesClick,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5EEDD)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(Icons.Default.Notes, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Notes", color = Color.Black, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}