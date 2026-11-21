package com.ewbible.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun CrashReportDialog(
    crashText: String,
    onCopy: () -> Unit = {},
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    var exported by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableIntStateOf(if (crashText.isBlank()) 1 else 0) }
    val geminiLogs by CrashReporter.geminiLogsFlow.collectAsState()
    val groqLogs by CrashReporter.groqLogsFlow.collectAsState()
    val geminiText = remember(geminiLogs) { geminiLogs.joinToString("\n") }
    val groqText = remember(groqLogs) { groqLogs.joinToString("\n") }

    // FIX #2: Load History / Logcat off main thread
    var historyText by remember { mutableStateOf("(no history yet)") }
    var logcatText by remember { mutableStateOf("(logcat empty)") }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 3) {
            historyText = withContext(Dispatchers.IO) {
                CrashReporter.getHistory(context) ?: "(no history yet)"
            }
        }
        if (selectedTab == 4) {
            logcatText = withContext(Dispatchers.IO) {
                CrashReporter.getLogcat(context) ?: "(logcat empty - READ_LOGS not granted)"
            }
        }
    }

    val currentText = when (selectedTab) {
        0 -> crashText
        1 -> geminiText
        2 -> groqText
        3 -> historyText
        else -> logcatText
    }
    val currentEmptyText = when (selectedTab) {
        0 -> "(no crash)"
        1 -> "(no Gemini logs yet)"
        2 -> "(no Groq logs yet)"
        3 -> "(no history yet)"
        else -> "(logcat empty)"
    }

    fun copyCurrent() {
        copyCrashText(context, if (currentText.isBlank()) currentEmptyText else currentText)
        copied = true
        exported = null
        onCopy()
    }

    // FIX #1: History clears ONLY history, Logcat clears ONLY logcat. No clearAll().
    // FIX #3: No refreshKey race - delete first, then update state on Main.
    fun clearCurrent() {
        when (selectedTab) {
            0 -> onClear()
            1 -> CrashReporter.clearGeminiLogs(context)
            2 -> CrashReporter.clearGroqLogs(context)
            3 -> {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        try { File(context.filesDir, "crash_history.txt").delete() } catch (_: Exception) { }
                    }
                    historyText = "(no history yet)"
                }
            }
            4 -> {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        try { File(context.filesDir, "last_logcat.txt").delete() } catch (_: Exception) { }
                    }
                    logcatText = "(logcat empty)"
                }
            }
        }
        copied = false
        exported = null
    }

    fun exportCurrent() {
        val fileName = when (selectedTab) {
            0 -> "EWBible-Crash.txt"
            1 -> "EWBible-Gemini.txt"
            2 -> "EWBible-Groq.txt"
            3 -> "EWBible-History.txt"
            else -> "EWBible-Logcat.txt"
        }
        val text = if (currentText.isBlank()) currentEmptyText else currentText
        scope.launch {
            val path = exportTextToDownloads(context, fileName, text)
            exported = path ?: "Export failed"
            copied = false
        }
    }

    fun selectTab(index: Int) {
        selectedTab = index
        copied = false
        exported = null
    }

AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (crashText.isBlank()) "Debug Logs" else "App crashed last time") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // FIX #4: ScrollableTabRow so 5 tabs fit on small phones
                ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
                    Tab(selected = selectedTab == 0, onClick = { selectTab(0) }) { Text("Crash", Modifier.padding(12.dp)) }
                    Tab(selected = selectedTab == 1, onClick = { selectTab(1) }) { Text("Gemini (${geminiLogs.size})", Modifier.padding(12.dp)) }
                    Tab(selected = selectedTab == 2, onClick = { selectTab(2) }) { Text("Groq (${groqLogs.size})", Modifier.padding(12.dp)) }
                    Tab(selected = selectedTab == 3, onClick = { selectTab(3) }) { Text("History", Modifier.padding(12.dp)) }
                    Tab(selected = selectedTab == 4, onClick = { selectTab(4) }) { Text("Logcat", Modifier.padding(12.dp)) }
                }
                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { copyCurrent() }) { Text(if (copied) "Copied ✓" else "Copy") }
                    OutlinedButton(onClick = { exportCurrent() }) { Text("Export") }
                    OutlinedButton(onClick = { clearCurrent() }) { Text("Clear") }
                }
                if (exported != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(exported!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                if (copied) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Copied ✓", color = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.height(8.dp))

                if (selectedTab == 0) {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(max = 400.dp)) {
                        if (crashText.isBlank()) {
                            Text("No crash. Check Gemini / Groq / History tabs for AI + ANR + performance logs.")
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
                    }
                } else if (selectedTab == 1) {
                    Column {
                        Text(
                            "Last ${geminiLogs.size} Gemini calls. Ask a question, then open this again to see HTTP code + error.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (geminiLogs.isEmpty()) {
                            Text("No Gemini calls yet. Ask a question first.", fontSize = 12.sp)
                        } else {
                            // FIX #5: No weight(1f), only heightIn - measures correctly in dialog
                            // FIX: itemsIndexed with index key - no duplicate hashCode crash
                            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                                itemsIndexed(geminiLogs, key = { index, _ -> index }) { _, line ->
                                    Text(line, fontSize = 11.sp, modifier = Modifier.padding(vertical = 2.dp))
                                }
                            }
                        }
                    }
                } else if (selectedTab == 2) {
                    Column {
                        Text(
                            "Last ${groqLogs.size} Groq calls. Ask a question, then open this again to see HTTP code + error.",
                            style = MaterialTheme.typography.bodySmall
                        )

// SEQUENCE 3 - continuation of Sequence 2, starts where you stopped
                        Spacer(modifier = Modifier.height(8.dp))
                        if (groqLogs.isEmpty()) {
                            Text("No Groq calls yet. Ask a question first.", fontSize = 12.sp)
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                                itemsIndexed(groqLogs, key = { index, _ -> index }) { _, line ->
                                    Text(line, fontSize = 11.sp, modifier = Modifier.padding(vertical = 2.dp))
                                }
                            }
                        }
                    }
                } else if (selectedTab == 3) {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(max = 400.dp)) {
                        Text("Last 10 fatal / non-fatal / ANR reports. Includes slow DB, slow network, low disk, high memory.", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(historyText.take(3000), fontSize = 11.sp)
                        if (historyText.length > 3000) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Showing first 3,000 chars. Copy copies full ${historyText.length} chars.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(max = 400.dp)) {
                        Text("Last logcat lines kept by CrashReporter. May be empty if READ_LOGS is not granted.", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(logcatText.take(3000), fontSize = 11.sp)
                        if (logcatText.length > 3000) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Showing first 3,000 chars. Copy copies full ${logcatText.length} chars.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { copyCurrent() }) { Text(if (copied) "Copied ✓" else "Copy") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("Later") }
                TextButton(onClick = { clearCurrent() }) { Text("Clear") }
            }
        }
    )
}

fun copyCrashText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("EWBible Crash Report", text))
}

suspend fun exportTextToDownloads(context: Context, fileName: String, text: String): String? {
    return withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@withContext null
                context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                "Saved to Downloads/$fileName"
            } else {
                @Suppress("DEPRECATION")
                val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloads.exists()) downloads.mkdirs()
                val file = File(downloads, fileName)
                file.writeText(text)
                file.absolutePath
            }
        } catch (_: Exception) {
            null
        }
    }
}

// Kept for backward compatibility if MainActivity calls them.
fun exportCrashToDownloads(context: Context, text: String): String? =
    exportSingleFile(context, "EWBible-Crash.txt", text)

fun exportGeminiToDownloads(context: Context, text: String): String? =
    exportSingleFile(context, "EWBible-Gemini.txt", text)

fun exportGroqToDownloads(context: Context, text: String): String? =
    exportSingleFile(context, "EWBible-Groq.txt", text)

fun exportHistoryToDownloads(context: Context, text: String): String? =
    exportSingleFile(context, "EWBible-History.txt", text)

fun exportLogcatToDownloads(context: Context, text: String): String? =
    exportSingleFile(context, "EWBible-Logcat.txt", text)

private fun exportSingleFile(context: Context, fileName: String, text: String): String? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return null
            context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            "Downloads/$fileName"
        } else {
            @Suppress("DEPRECATION")
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloads.exists()) downloads.mkdirs()
            val file = File(downloads, fileName)
            file.writeText(text)
            file.absolutePath
        }
    } catch (_: Exception) {
        null
    }
}