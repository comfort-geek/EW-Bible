package com.ewbible.app.ui.screens

import android.graphics.Color as AndroidColor
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun OpenEWContent(
    commentaryHtml: String,
    fontSize: Float,
    textColor: Color,
    secondaryText: Color,
    hasError: Boolean = false,
    bookName: String = "",
    chapter: Int = 1,
) {
    val context = LocalContext.current

    var webViewFailed by remember { mutableStateOf(false) }

    // FIX ISSUE 2: reset failure when you change chapter/book, so a new chapter can load
    LaunchedEffect(bookName, chapter) {
        webViewFailed = false
    }

    // Clean HTML safely - never crash on bad HTML
    val renderedHtml = remember(commentaryHtml) {
        runCatching {
            val t = commentaryHtml.trim()
            if (t.contains("<body", ignoreCase = true)) {
                t.substringAfter("<body", "").substringAfter(">", "").substringBeforeLast("</body>", t)
            } else t
        }.getOrDefault("")
    }

    val textColorCss = "#%06X".format(0xFFFFFF and textColor.toArgb())
    val secondaryColorCss = "#%06X".format(0xFFFFFF and secondaryText.toArgb())

    // YOUR HTML + CSS - unchanged
    val html = remember(renderedHtml, textColorCss, secondaryColorCss, fontSize) {
        """
        <!doctype html>
        <html><head><meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
            body { color: $textColorCss; background: transparent; font-family: sans-serif; font-size: ${fontSize}px; line-height: 1.6; margin: 8px; padding-bottom: 80px; }
            p { margin: 0 0 1em; }
            h1, h2, h3, h4 { color: $textColorCss; line-height: 1.3; }
            a { color: #C9A227; }
            .empty { color: $secondaryColorCss; }
            blockquote { border-left: 3px solid #C9A227; margin-left: 0; padding-left: 12px; color: $secondaryColorCss; }
        </style>
        </head><body>${if (renderedHtml.isBlank()) "<p class=\"empty\">No commentary available for this chapter.</p>" else renderedHtml}</body></html>
        """.trimIndent()
    }

    if (webViewFailed || hasError) {
        // Fallback - never show blank / crash, show text version
        Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
            if (bookName.isNotBlank()) {
                Text(
                    // FIX ISSUE 1: title only
                    text = "$bookName $chapter",
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            Text(
                text = if (hasError) "Could not load commentary. Please try again."
                else if (renderedHtml.isBlank()) "No commentary available for this chapter."
                else renderedHtml.replace(Regex("<[^>]*>"), " ").trim(),
                color = if (renderedHtml.isBlank()) secondaryText else textColor,
                fontSize = fontSize.sp,
                lineHeight = (fontSize + 8).sp
            )
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (bookName.isNotBlank()) {
            Text(
                // FIX ISSUE 1: title only
                text = "$bookName $chapter",
                color = textColor,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
            )
        }

        // FIX ISSUE 2: lastLoaded resets per book+chapter, so new commentary always loads
        val lastLoaded = remember(bookName, chapter) { mutableStateOf("") }
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = false
                    settings.domStorageEnabled = false
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.builtInZoomControls = false
                    setBackgroundColor(AndroidColor.TRANSPARENT)
                    webViewClient = object : WebViewClient() {
                        override fun onReceivedError(
                            view: WebView?,
                            errorCode: Int,
                            description: String?,
                            failingUrl: String?
                        ) {
                            webViewFailed = true
                        }
                    }
                }
            },
            update = { wv ->
                if (lastLoaded.value != html) {
                    lastLoaded.value = html
                    runCatching {
                        wv.post {
                            runCatching {
                                wv.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                            }
                        }
                    }
                }
            },
            onRelease = { wv ->
                runCatching {
                    wv.stopLoading()
                    wv.removeAllViews()
                    wv.destroy()
                }
            }
        )
    }
}