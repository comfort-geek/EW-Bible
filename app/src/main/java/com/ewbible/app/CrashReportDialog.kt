package com.ewbible.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Environment
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("App crashed last time") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
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
                if (copied) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Copied ✓", color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                // ✅ COPY = whole crashText, not the truncated preview
                copyCrashText(context, crashText)
                copied = true
                onCopy()
            }) { Text(if (copied) "Copied ✓" else "Copy") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("Later") }
                TextButton(onClick = onClear) { Text("Clear") }
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