package com.ewbible.app.ui.screens

import android.graphics.Color as AndroidColor
import android.view.MotionEvent
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ewbible.app.data.database.BookCatalog
import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.math.abs

private val ALL_NAMES_PATTERN: String by lazy {
    BookCatalog.allDisplayNames()
        .distinct()
        .sortedByDescending { it.length }
        .joinToString("|") { Regex.escape(it) }
}

// FIX #2: Now captures "-11" as group 4 -> "Romans 8:1-11" is ONE link
private val FULL_VERSE_REGEX: Regex by lazy {
    Regex("\\b($ALL_NAMES_PATTERN)\\s+(\\d+):(\\d+)(?:\\s*-\\s*(\\d+))?\\b(?![^<]*</a>)")
}
private val BARE_VERSE_REGEX: Regex by lazy {
    Regex("(?<![\\w:])\\b(\\d+):(\\d+)(?:\\s*-\\s*(\\d+))?\\b(?![^<]*</a>)")
}

private val linkedHtmlCache = object : LinkedHashMap<String, String>(30, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > 30
}

@Composable
fun OpenEWContent(
    commentaryHtml: String,
    fontSize: Float,
    textColor: Color,
    hasError: Boolean = false,
    isLoading: Boolean = false,
    bookName: String = "",
    chapter: Int = 1,
    onVerseClick: (book: String, chapter: Int, verse: Int) -> Unit = { _, _, _ -> },
    onNextChapter: () -> Unit = {},
    onPreviousChapter: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    val cacheKey = remember(bookName, chapter, commentaryHtml) { "$bookName-$chapter-${commentaryHtml.hashCode()}" }

    val linkedHtml = remember(commentaryHtml, cacheKey) {
        if (commentaryHtml.isBlank()) ""
        else linkedHtmlCache.getOrPut(cacheKey) {
            val t = commentaryHtml.trim()
            val body = if (t.contains("<body", ignoreCase = true)) {
                t.substringAfter("<body", "").substringAfter(">", "").substringBeforeLast("</body>", t)
            } else t
            linkifyVerses(body, bookName)
        }
    }

    val textColorCss = remember(textColor) { "#%06X".format(0xFFFFFF and textColor.toArgb()) }
    val secondaryColorCss = remember(textColor) { "#%06X".format(0xFFFFFF and textColor.copy(alpha = 0.6f).toArgb()) }

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

    if (hasError && linkedHtml.isBlank() && !isLoading) {
        Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
            Text("Could not load commentary. Please try again.", color = textColor.copy(alpha = 0.6f), fontSize = fontSize.sp, lineHeight = (fontSize + 8).sp)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC9A227))) {
                Text("Try Again", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                    overScrollMode = View.OVER_SCROLL_NEVER
                    isNestedScrollingEnabled = true
                    settings.javaScriptEnabled = false
                    settings.domStorageEnabled = false
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.builtInZoomControls = false
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    setBackgroundColor(AndroidColor.TRANSPARENT)
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
                    }
                    var downX = 0f
                    var downY = 0f
                    setOnTouchListener { _, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y }
                            MotionEvent.ACTION_UP -> {
                                val dx = event.x - downX
                                val dy = event.y - downY
                                if (abs(dx) > 150 && abs(dx) > abs(dy) * 1.5f) {
                                    if (dx < 0) onNextChapter() else onPreviousChapter()
                                    return@setOnTouchListener true
                                }
                            }
                        }
                        false
                    }
                }
            },
            update = { wv ->
                if (wv.tag != html.hashCode()) {
                    wv.tag = html.hashCode()
                    wv.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                }
            }
        )
        if (isLoading && linkedHtml.isBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFFC9A227))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Loading commentary...", color = textColor.copy(alpha = 0.6f), fontSize = 14.sp)
                }
            }
        }
    }
}

private fun linkifyVerses(html: String, currentBook: String): String {
    if (html.isBlank()) return html
    var result = html
    // Full: "Romans 8:1-11" -> one <a> with whole text, href points to start verse 1
    result = FULL_VERSE_REGEX.replace(result) { m ->
        val book = BookCatalog.canonicalName(m.groupValues[1])
        val ch = m.groupValues[2]
        val vs = m.groupValues[3]
        // m.value already = "Romans 8:1-11" including range, so display stays full
        """<a class="verse-link" href="ew://verse?book=${URLEncoder.encode(book, "UTF-8")}&chapter=$ch&verse=$vs">${m.value}</a>"""
    }
    if (currentBook.isNotBlank()) {
        // Bare: "8:1-11" inside Romans 8 -> links to Romans 8:1, displays "8:1-11"
        result = BARE_VERSE_REGEX.replace(result) { m ->
            val ch = m.groupValues[1]
            val vs = m.groupValues[2]
            """<a class="verse-link" href="ew://verse?book=${URLEncoder.encode(BookCatalog.canonicalName(currentBook), "UTF-8")}&chapter=$ch&verse=$vs">${m.value}</a>"""
        }
    }
    return result
}