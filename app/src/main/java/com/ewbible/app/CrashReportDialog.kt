package com.ewbible.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Environment
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

@Composable
fun CrashReportDialog(
    crashText: String,
    onCopy: () -> Unit = {},
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    // If no crash, open directly on Gemini tab
    var selectedTab by remember { mutableIntStateOf(if (crashText.isBlank()) 1 else 0) }
    val geminiLogs by CrashReporter.geminiLogsFlow.collectAsState()
    val geminiText = remember(geminiLogs) { geminiLogs.joinToString("\n") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (crashText.isBlank()) "Debug Logs" else "App crashed last time") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // --- TABS ---
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) { Text("Crash", Modifier.padding(12.dp)) }
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) { Text("Gemini (${geminiLogs.size})", Modifier.padding(12.dp)) }
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (selectedTab == 0) {
                    // --- OLD CRASH UI - UNCHANGED ---
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        if (crashText.isBlank()) {
                            Text("No crash. Check Gemini tab for AI logs.")
                        } else {
                            Text("Tap Copy to copy the full crash report, then paste it anywhere. Tap Clear to make it disappear.")
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(crashText.take(3000))
                            if (crashText.length > 3000) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Showing first 3,000 chars. Copy copies the full ${crashText.length} chars.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        if (copied) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Copied ✓", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                } else {
                    // --- NEW GEMINI UI ---
                    Column {
                        Text(
                            "Last ${geminiLogs.size} Gemini calls. Ask a question, then open this again to see HTTP code + error.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                copyCrashText(context, if (geminiText.isBlank()) "(no Gemini logs yet)" else geminiText)
                                copied = true
                            }) { Text(if (copied) "Copied ✓" else "Copy Gemini") }
                            OutlinedButton(onClick = {
                                CrashReporter.clearGeminiLogs(context)
                                copied = false
                            }) { Text("Clear Gemini") }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (geminiLogs.isEmpty()) {
                            Text("No Gemini calls yet. Ask a question first.", fontSize = 12.sp)
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                                items(geminiLogs) { line ->
                                    Text(line, fontSize = 11.sp, modifier = Modifier.padding(vertical = 2.dp))
                                }
                            }
                        }
                        if (copied) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Copied ✓", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (selectedTab == 0) {
                    copyCrashText(context, crashText)
                } else {
                    copyCrashText(context, if (geminiText.isBlank()) "(no Gemini logs yet)" else geminiText)
                }
                copied = true
                onCopy()
            }) { Text(if (copied) "Copied ✓" else "Copy") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("Later") }
                TextButton(onClick = {
                    if (selectedTab == 0) onClear()
                    else CrashReporter.clearGeminiLogs(context)
                }) { Text("Clear") }
            }
        }
    )
}

// ✅ NEW: Copies the WHOLE report to clipboard
fun copyCrashText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("EWBible Crash Report", text))
}

// Copy crash to Downloads so you can open it even if app never opens
fun exportCrashToDownloads(context: Context, text: String): String? {
    return try {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloads.exists()) downloads.mkdirs()
        val file = File(downloads, "EWBible-Crash.txt")
        file.writeText(text)
        file.absolutePath
    } catch (e: Exception) {
        null
    }
}

fun exportGeminiToDownloads(context: Context, text: String): String? {
    return try {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloads.exists()) downloads.mkdirs()
        val file = File(downloads, "EWBible-Gemini.txt")
        file.writeText(text)
        file.absolutePath
    } catch (e: Exception) {
        null
    }
}