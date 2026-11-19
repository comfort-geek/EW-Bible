package com.ewbible.app

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
fun CrashReportDialog(
    crashText: String,
    onShare: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("App crashed last time") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Tap Share to send this to yourself, then Clear to make it disappear.")
                Spacer(modifier = Modifier.height(12.dp))
                Text(crashText.take(3000))
            }
        },
        confirmButton = {
            Button(onClick = onShare) { Text("Share") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("Later") }
                TextButton(onClick = onClear) { Text("Clear") }
            }
        }
    )
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

fun shareCrashText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "EWBible Crash Report")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share crash report"))
}