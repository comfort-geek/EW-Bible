package com.ewbible.app.ui.screens

import android.graphics.Color as AndroidColor
import android.webkit.WebResourceRequest
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.math.abs

@Composable
fun OpenEWContent(
    commentaryHtml: String,
    fontSize: Float,
    textColor: Color,
    secondaryText: Color,
    hasError: Boolean = false,
    bookName: String = "",
    chapter: Int = 1,
    onVerseClick: (book: String, chapter: Int, verse: Int) -> Unit = { _, _, _ -> },
    onNextChapter: () -> Unit = {},
    onPreviousChapter: () -> Unit = {},
) {
    var webViewFailed by remember { mutableStateOf(false) }
    val lastLoaded = remember(bookName, chapter) { mutableStateOf("") }

    LaunchedEffect(bookName, chapter) {
        webViewFailed = false
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

    // FIXED: No title here - HomeScreen already shows "John 4"
    // FIXED: Only show error if hasError AND we really have no content
    // webViewFailed alone should not hide good commentary
    if (hasError && linkedHtml.isBlank()) {
        Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
            Text(
                text = "Could not load commentary. Please try again.",
                color = secondaryText,
                fontSize = fontSize.sp,
                lineHeight = (fontSize + 8).sp
            )
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // FIXED: No duplicate title Text() here anymore

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

                    // SWIPE INSIDE WEBVIEW - WebView eats touches, so detect here
                    var downX = 0f
                    var downY = 0f
                    setOnTouchListener { _, event ->
                        when (event.action) {
                            android.view.MotionEvent.ACTION_DOWN -> {
                                downX = event.x
                                downY = event.y
                            }
                            android.view.MotionEvent.ACTION_UP -> {
                                val dx = event.x - downX
                                val dy = event.y - downY
                                // Horizontal swipe, not vertical scroll
                                if (abs(dx) > 180 && abs(dx) > abs(dy) * 2) {
                                    if (dx < 0) onNextChapter() else onPreviousChapter()
                                    return@setOnTouchListener true
                                }
                            }
                        }
                        false // let WebView handle scroll / clicks normally
                    }

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
                            // FIXED: Don't set webViewFailed for loadDataWithBaseURL errors
                            // It was falsely showing "Could not load" even with good HTML
                            // webViewFailed = true
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

private fun linkifyVerses(html: String, currentBook: String): String {
    if (html.isBlank()) return html

    val bookNames = listOf(
        "1 Samuel", "2 Samuel", "1 Kings", "2 Kings", "1 Chronicles", "2 Chronicles",
        "1 Corinthians", "2 Corinthians", "1 Thessalonians", "2 Thessalonians",
        "1 Timothy", "2 Timothy", "1 Peter", "2 Peter", "1 John", "2 John", "3 John",
        "Genesis", "Exodus", "Leviticus", "Numbers", "Deuteronomy", "Joshua", "Judges", "Ruth",
        "Ezra", "Nehemiah", "Esther", "Job", "Psalm", "Psalms", "Proverbs", "Ecclesiastes",
        "Song of Solomon", "Song", "Isaiah", "Jeremiah", "Lamentations", "Ezekiel", "Daniel",
        "Hosea", "Joel", "Amos", "Obadiah", "Jonah", "Micah", "Nahum", "Habakkuk",
        "Zephaniah", "Haggai", "Zechariah", "Malachi", "Matthew", "Mark", "Luke", "John",
        "Acts", "Romans", "Galatians", "Ephesians", "Philippians", "Colossians", "Titus",
        "Philemon", "Hebrews", "James", "Jude", "Revelation", "Samuel", "Kings", "Chronicles"
    ).sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }

    var result = html

    val fullPattern = Regex("\\b($bookNames)\\s+(\\d+):(\\d+)\\b(?![^<]*</a>)")
    result = fullPattern.replace(result) { m ->
        val book = m.groupValues[1]
        val ch = m.groupValues[2]
        val vs = m.groupValues[3]
        """<a class="verse-link" href="ew://verse?book=${URLEncoder.encode(book, "UTF-8")}&chapter=$ch&verse=$vs">${m.value}</a>"""
    }

    if (currentBook.isNotBlank()) {
        val barePattern = Regex("(?<![\\w:])\\b(\\d+):(\\d+)\\b(?![^<]*</a>)")
        result = barePattern.replace(result) { m ->
            val ch = m.groupValues[1]
            val vs = m.groupValues[2]
            """<a class="verse-link" href="ew://verse?book=${URLEncoder.encode(currentBook, "UTF-8")}&chapter=$ch&verse=$vs">${m.value}</a>"""
        }
    }

    return result
}