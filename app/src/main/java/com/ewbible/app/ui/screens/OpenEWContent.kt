package com.ewbible.app.ui.screens

import android.graphics.Color as AndroidColor
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp

@Composable
fun OpenEWContent(
    commentaryHtml: String,
    fontSize: Float,
    textColor: Color,
    secondaryText: Color,
) {
    val renderedHtml = if (commentaryHtml.isBlank()) {
        "<p class=\"empty\">No commentary available for this chapter.</p>"
    } else {
        // If commentary is a full <html> doc, strip it - otherwise nested <html> = blank
        val t = commentaryHtml.trim()
        if (t.contains("<body", ignoreCase = true)) {
            t.substringAfter("<body", "").substringAfter(">", "").substringBeforeLast("</body>", t)
        } else t
    }
    val textColorCss = "#%06X".format(0xFFFFFF and textColor.toArgb())
    val secondaryColorCss = "#%06X".format(0xFFFFFF and secondaryText.toArgb())
    val html = """
        <!doctype html>
        <html><head><meta name="viewport" content="width=device-width, initial-scale=1">
        <style>body { color: $textColorCss; background: transparent; font-size: ${fontSize}px; line-height: 1.5; margin: 8px; } p { margin: 0 0 1em; } .empty { color: $secondaryColorCss; }</style>
        </head><body>$renderedHtml</body></html>
    """.trimIndent()

    // Only reload WebView when html actually changes
    val lastLoaded = remember { mutableStateOf("") }
    AndroidView(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = false
                settings.domStorageEnabled = false
                setBackgroundColor(AndroidColor.TRANSPARENT)
                webViewClient = WebViewClient()
            }
        },
        update = { webView ->
            if (lastLoaded.value != html) {
                lastLoaded.value = html
                webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        },
    )
}