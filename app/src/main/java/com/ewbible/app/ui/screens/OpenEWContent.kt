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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.net.URLDecoder

@Composable
fun OpenEWContent(
    commentaryHtml: String,
    fontSize: Float,
    textColor: Color,
    secondaryText: Color,
    hasError: Boolean = false,
    bookName: String = "",
    chapter: Int = 1,
    // FIX 12: Callback when a linked verse is tapped
    onVerseClick: (book: String, chapter: Int, verse: Int) -> Unit = { _, _, _ -> },
) {
    val context = LocalContext.current
    var webViewFailed by remember { mutableStateOf(false) }

    LaunchedEffect(bookName, chapter) {
        webViewFailed = false
    }

    val renderedHtml = remember(commentaryHtml) {
        runCatching {
            val t = commentaryHtml.trim()
            if (t.contains("<body", ignoreCase = true)) {
                t.substringAfter("<body", "").substringAfter(">", "").substringBeforeLast("</body>", t)
            } else t
        }.getOrDefault("")
    }

    // FIX 12: Link ALL verses - full refs + bare chapter:verse in current book
    val linkedHtml = remember(renderedHtml, bookName, chapter) {
        linkifyVerses(renderedHtml, bookName, chapter)
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

    if (webViewFailed || hasError) {
        Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
            if (bookName.isNotBlank()) {
                Text(
                    text = "$bookName $chapter",
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            Text(
                text = if (hasError) "Could not load commentary. Please try again."
                else if (linkedHtml.isBlank()) "No commentary available for this chapter."
                else linkedHtml.replace(Regex("<[^>]*>"), " ").trim(),
                color = if (linkedHtml.isBlank()) secondaryText else textColor,
                fontSize = fontSize.sp,
                lineHeight = (fontSize + 8).sp
            )
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (bookName.isNotBlank()) {
            Text(
                // FIX 2: Title is "2 Samuel 3" only - Open EW tab, same format as Bible tab
                text = "$bookName $chapter",
                color = textColor,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
            )
        }

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
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val url = request?.url?.toString() ?: return false
                            // FIX 12: Intercept our verse links
                            if (url.startsWith("ew://verse")) {
                                try {
                                    val query = url.substringAfter("?", "")
                                    val params = query.split("&").associate {
                                        val parts = it.split("=", limit = 2)
                                        if (parts.size == 2) parts[0] to URLDecoder.decode(parts[1], "UTF-8") else parts[0] to ""
                                    }
                                    val b = params["book"] ?: bookName
                                    val c = params["chapter"]?.toIntOrNull() ?: chapter
                                    val v = params["verse"]?.toIntOrNull() ?: 1
                                    onVerseClick(b, c, v)
                                } catch (e: Exception) { }
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

// FIX 12: Turn every plain verse reference into a clickable link
// Handles: "2 Samuel 3:1", "1 Kings 2:3", "John 3:16", "Psalm 119:105", "3:1" (current book)
private fun linkifyVerses(html: String, currentBook: String, currentChapter: Int): String {
    if (html.isBlank()) return html
    // Don't double-link text already inside <a>...</a> - split by tags
    // Simple safe approach: process only text outside tags
    val bookNames = listOf(
        "1 Samuel", "2 Samuel", "1 Kings", "2 Kings", "1 Chronicles", "2 Chronicles",
        "1 Corinthians", "2 Corinthians", "1 Thessalonians", "2 Thessalonians",
        "1 Timothy", "2 Timothy", "1 Peter", "2 Peter", "1 John", "2 John", "3 John", "1 Samuel",
        "Genesis", "Exodus", "Leviticus", "Numbers", "Deuteronomy", "Joshua", "Judges", "Ruth",
        "Ezra", "Nehemiah", "Esther", "Job", "Psalm", "Psalms", "Proverbs", "Ecclesiastes",
        "Song of Solomon", "Song", "Isaiah", "Jeremiah", "Lamentations", "Ezekiel", "Daniel",
        "Hosea", "Joel", "Amos", "Obadiah", "Jonah", "Micah", "Nahum", "Habakkuk",
        "Zephaniah", "Haggai", "Zechariah", "Malachi", "Matthew", "Mark", "Luke", "John",
        "Acts", "Romans", "Galatians", "Ephesians", "Philippians", "Colossians", "Titus",
        "Philemon", "Hebrews", "James", "Jude", "Revelation", "Samuel", "Kings", "Chronicles"
    ).sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }

    var result = html
    // 1. Full refs: "2 Samuel 3:1" or "John 3:16" or "Psalm 119:105" - skip if already inside <a
    // We do a single pass with regex, avoiding already-linked parts by checking preceding "<a"
    val fullPattern = Regex("(?<!href=[\"'][^\"']*)\\b($bookNames)\\s+(\\d+):(\\d+)\\b(?![^<]*</a>)")
    result = fullPattern.replace(result) { m ->
        val book = m.groupValues[1]
        val ch = m.groupValues[2]
        val vs = m.groupValues[3]
        """<a class="verse-link" href="ew://verse?book=${java.net.URLEncoder.encode(book, "UTF-8")}&chapter=$ch&verse=$vs">${m.value}</a>"""
    }
    // 2. Bare refs: "3:1" or "verse 5" in current book context - only if not already linked
    // Bare chapter:verse like "3:1" - link to current book
    val barePattern = Regex("(?<!href=[\"'][^\"']*)(?<![\\w:])\\b(\\d+):(\\d+)\\b(?![^<]*</a>)")
    result = barePattern.replace(result) { m ->
        // Skip if this was already part of a full ref we just linked (it will be inside <a> now, regex above avoids it via lookahead, but double-check)
        val ch = m.groupValues[1]
        val vs = m.groupValues[2]
        """<a class="verse-link" href="ew://verse?book=${java.net.URLEncoder.encode(currentBook, "UTF-8")}&chapter=$ch&verse=$vs">${m.value}</a>"""
    }
    return result
}