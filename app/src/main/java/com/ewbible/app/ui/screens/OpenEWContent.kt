package com.ewbible.app.ui.screens

import android.graphics.Color as AndroidColor
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ewbible.app.data.database.BookCatalog
import java.net.URLDecoder
import java.net.URLEncoder

@Composable
fun OpenEWContent(
    commentaryHtml: String,
    summaryText: String = "",
    fontSize: Float,
    textColor: Color,
    secondaryText: Color,
    hasError: Boolean = false,
    isLoading: Boolean = false,
    bookName: String = "",
    chapter: Int = 1,
    onVerseClick: (book: String, chapter: Int, verse: Int) -> Unit = { _, _, _ -> },
    onNextChapter: () -> Unit = {},
    onPreviousChapter: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    val lastLoaded = remember(bookName, chapter) { mutableStateOf("") }

    LaunchedEffect(bookName, chapter) {
        lastLoaded.value = ""
    }

    val renderedHtml = remember(commentaryHtml) {
        runCatching {
            val t = commentaryHtml.trim()
            if (t.contains("<body", ignoreCase = true)) {
                t.substringAfter("<body", "").substringAfter(">", "").substringBeforeLast("</body>", t)
            } else t
        }.getOrDefault("")
    }

    val linkedHtml = remember(renderedHtml, bookName) {
        linkifyVerses(renderedHtml, bookName)
    }

    val textColorCss = "#%06X".format(0xFFFFFF and textColor.toArgb())
    val secondaryColorCss = "#%06X".format(0xFFFFFF and secondaryText.toArgb())

    val html = remember(linkedHtml, textColorCss, secondaryColorCss, fontSize) {
        """
        <!doctype html>
        <html><head><meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
            body { color: $textColorCss; background: transparent; font-family: sans-serif; font-size: ${fontSize}px; line-height: 1.6; margin: 8px; padding-bottom: 80px; }
            p { margin: 0 0 1em; }
            h1, h2, h3, h4 { color: $textColorCss; line-height: 1.3; }
            a { color: #C9A227; font-weight: bold; text-decoration: underline; }
            a.verse-link { color: #C9A227; font-weight: bold; }
            .empty { color: $secondaryColorCss; }
            blockquote { border-left: 3px solid #C9A227; margin-left: 0; padding-left: 12px; color: $secondaryColorCss; }
        </style>
        </head><body>${if (linkedHtml.isBlank()) "<p class=\"empty\">No commentary available for this chapter.</p>" else linkedHtml}</body></html>
        """.trimIndent()
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color(0xFFC9A227))
                Spacer(modifier = Modifier.height(12.dp))
                Text("Loading commentary...", color = secondaryText, fontSize = 14.sp)
            }
        }
        return
    }

    if (hasError && linkedHtml.isBlank() && summaryText.isBlank()) {
        Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
            Text(
                text = "Could not load commentary. Please try again.",
                color = secondaryText,
                fontSize = fontSize.sp,
                lineHeight = (fontSize + 8).sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC9A227))
            ) {
                Text("Try Again", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (summaryText.isNotBlank()) {
            Text(
                text = summaryText,
                color = textColor,
                fontSize = fontSize.sp,
                fontStyle = FontStyle.Italic,
                lineHeight = (fontSize + 8).sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }

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

                    // NO SWIPE HERE - swipe lives ONLY in HomeScreen.kt

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val url = request?.url?.toString() ?: return false
                            if (url.startsWith("ew://verse")) {
                                runCatching {
                                    val query = url.substringAfter("?", "")
                                    val params = query.split("&").associate {
                                        val parts = it.split("=", limit = 2)
                                        if (parts.size == 2) parts[0] to URLDecoder.decode(parts[1], "UTF-8") else parts[0] to ""
                                    }
                                    val b = params["book"]?.takeIf { it.isNotBlank() } ?: bookName
                                    val c = params["chapter"]?.toIntOrNull() ?: chapter
                                    val v = params["verse"]?.toIntOrNull() ?: 1
                                    onVerseClick(b, c, v)
                                }
                                return true
                            }
                            return false
                        }
                        override fun onReceivedError(
                            view: WebView?,
                            errorCode: Int,
                            description: String?,
                            failingUrl: String?
                        ) {
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

// NO BOOK NAMES HERE. Everything comes from BookCatalog.
private fun linkifyVerses(html: String, currentBook: String): String {
    if (html.isBlank()) return html

    val allNames = BookCatalog.allDisplayNames()
        .distinct()
        .sortedByDescending { it.length }
        .joinToString("|") { Regex.escape(it) }

    var result = html

    val fullPattern = Regex("\\b($allNames)\\s+(\\d+):(\\d+)\\b(?![^<]*</a>)")
    result = fullPattern.replace(result) { m ->
        val book = BookCatalog.canonicalName(m.groupValues[1])
        val ch = m.groupValues[2]
        val vs = m.groupValues[3]
        """<a class="verse-link" href="ew://verse?book=${URLEncoder.encode(book, "UTF-8")}&chapter=$ch&verse=$vs">${m.value}</a>"""
    }

    if (currentBook.isNotBlank()) {
        val barePattern = Regex("(?<![\\w:])\\b(\\d+):(\\d+)\\b(?![^<]*</a>)")
        result = barePattern.replace(result) { m ->
            val ch = m.groupValues[1]
            val vs = m.groupValues[2]
            """<a class="verse-link" href="ew://verse?book=${URLEncoder.encode(BookCatalog.canonicalName(currentBook), "UTF-8")}&chapter=$ch&verse=$vs">${m.value}</a>"""
        }
    }

    return result
}