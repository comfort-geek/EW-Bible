package com.ewbible.app.ui.screens

import android.graphics.Color as AndroidColor
import android.view.MotionEvent
import android.view.View
import android.webkit.WebResourceRequest
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color(0xFFC9A227))
                Spacer(modifier = Modifier.height(12.dp))
                Text("Loading commentary...", color = textColor.copy(alpha = 0.6f), fontSize = 14.sp)
            }
        }
        return
    }

    val renderedHtml = remember(commentaryHtml) {
        val t = commentaryHtml.trim()
        if (t.contains("<body", ignoreCase = true)) {
            t.substringAfter("<body", "").substringAfter(">", "").substringBeforeLast("</body>", t)
        } else t
    }

    val linkedHtml = remember(renderedHtml, bookName) {
        linkifyVerses(renderedHtml, bookName)
    }

    if (hasError && linkedHtml.isBlank()) {
        Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
            Text(
                text = "Could not load commentary. Please try again.",
                color = textColor.copy(alpha = 0.6f),
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

    val textColorCss = remember(textColor) { "#%06X".format(0xFFFFFF and textColor.toArgb()) }
    val secondaryColorCss = remember(textColor) { "#%06X".format(0xFFFFFF and textColor.copy(alpha = 0.6f).toArgb()) }

    val html = remember(linkedHtml, textColorCss, secondaryColorCss, fontSize) {
        """
        <!doctype html>
        <html><head><meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
            * { box-sizing: border-box; }
            body { color: $textColorCss; background: transparent; font-family: sans-serif; font-size: ${fontSize}px; line-height: 1.6; margin: 8px; padding-bottom: 80px; word-wrap: break-word; }
            p { margin: 0 0 1em; }
            h1, h2, h3, h4 { color: $textColorCss; line-height: 1.3; }
            a { color: #C9A227; font-weight: bold; text-decoration: underline; }
            a.verse-link { color: #C9A227; font-weight: bold; }
            .empty { color: $secondaryColorCss; }
            blockquote { border-left: 3px solid #C9A227; margin-left: 0; padding-left: 12px; color: $secondaryColorCss; }
            img { max-width: 100%; height: auto; }
        </style>
        </head><body>${if (linkedHtml.isBlank()) "<p class=\"empty\">No commentary available for this chapter.</p>" else linkedHtml}</body></html>
        """.trimIndent()
    }

    var lastLoadedHtml by remember(bookName, chapter) { mutableStateOf("") }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                overScrollMode = View.OVER_SCROLL_NEVER
                settings.javaScriptEnabled = false
                settings.domStorageEnabled = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                settings.setSupportZoom(false)
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
                        return true
                    }
                }
                var downX = 0f
                var downY = 0f
                var isHorizontalSwipe = false
                setOnTouchListener { _, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            downX = event.x
                            downY = event.y
                            isHorizontalSwipe = false
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = event.x - downX
                            val dy = event.y - downY
                            if (!isHorizontalSwipe && abs(dx) > 120 && abs(dx) > abs(dy) * 2f) {
                                isHorizontalSwipe = true
                            }
                            if (isHorizontalSwipe) return@setOnTouchListener true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            val dx = event.x - downX
                            val dy = event.y - downY
                            if (abs(dx) > 150 && abs(dx) > abs(dy) * 1.5f) {
                                if (dx < 0) onNextChapter() else onPreviousChapter()
                                return@setOnTouchListener true
                            }
                            isHorizontalSwipe = false
                        }
                    }
                    false
                }
            }
        },
        update = { wv ->
            if (lastLoadedHtml != html) {
                lastLoadedHtml = html
                wv.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        },
        onRelease = { wv ->
            runCatching {
                wv.stopLoading()
                wv.loadDataWithBaseURL(null, "", "text/html", "UTF-8", null)
                wv.removeAllViews()
                wv.destroy()
            }
        }
    )
}

private val verseFullPatternCache = mutableMapOf<String, Regex>()

private fun linkifyVerses(html: String, currentBook: String): String {
    if (html.isBlank()) return html
    var result = html
    val allNames = BookCatalog.allDisplayNames()
        .distinct()
        .sortedByDescending { it.length }
    if (allNames.isNotEmpty()) {
        val key = allNames.joinToString("|")
        val fullPattern = verseFullPatternCache.getOrPut(key) {
            val escaped = allNames.joinToString("|") { Regex.escape(it) }
            Regex("\\b($escaped)\\s+(\\d+):(\\d+)\\b")
        }
        result = fullPattern.replace(result) { m ->
            val book = BookCatalog.canonicalName(m.groupValues[1])
            val ch = m.groupValues[2]
            val vs = m.groupValues[3]
            """<a class="verse-link" href="ew://verse?book=${URLEncoder.encode(book, "UTF-8")}&chapter=$ch&verse=$vs">${m.value}</a>"""
        }
    }
    if (currentBook.isNotBlank()) {
        val barePattern = Regex("(?<![\\w:])\\b(\\d+):(\\d+)\\b")
        result = barePattern.replace(result) { m ->
            val ch = m.groupValues[1]
            val vs = m.groupValues[2]
            """<a class="verse-link" href="ew://verse?book=${URLEncoder.encode(BookCatalog.canonicalName(currentBook), "UTF-8")}&chapter=$ch&verse=$vs">${m.value}</a>"""
        }
    }
    return result
}