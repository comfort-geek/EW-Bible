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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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

private val FULL_VERSE_REGEX: Regex by lazy {
    Regex("\\b($ALL_NAMES_PATTERN)\\s+(\\d+):(\\d+)(?:\\s*-\\s*(\\d+))?\\b(?![^<]*</a>)")
}
private val BARE_VERSE_REGEX: Regex by lazy {
    Regex("(?<![\\w:])\\b(\\d+):(\\d+)(?:\\s*-\\s*(\\d+))?\\b(?![^<]*</a>)")
}

private val linkedHtmlCache = object : LinkedHashMap<String, String>(30, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > 30
}

// The DB HTML has <b><i> that never close, so the whole chapter stays bold+italic.
// Strip ALL <b>/<i>/<strong>/<em> from the body. Headings + verse links get bold back via CSS below.
private fun cleanCommentaryHtml(html: String): String {
    var result = html
    result = result.replace(Regex("(?i)</?(b|strong|i|em)>"), "")
    // Also strip inline styles that force bold/italic on big blocks
    result = result.replace(Regex("(?i)font-weight\\s*:\\s*(bold|[700-900])\\s*;?"), "")
    result = result.replace(Regex("(?i)font-style\\s*:\\s*italic\\s*;?"), "")
    return result
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
    onVerseClick: (book: String, chapter: Int, verse: Int, endVerse: Int) -> Unit = { _, _, _, _ -> },
    onNextChapter: () -> Unit = {},
    onPreviousChapter: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    // FIX 1: WebView factory runs ONCE. Without this, clicks/swipes use stale book/chapter.
    val currentBookName by rememberUpdatedState(bookName)
    val currentChapter by rememberUpdatedState(chapter)
    val currentOnVerseClick by rememberUpdatedState(onVerseClick)
    val currentOnNext by rememberUpdatedState(onNextChapter)
    val currentOnPrev by rememberUpdatedState(onPreviousChapter)

    val cacheKey = remember(bookName, chapter, commentaryHtml) { "$bookName-$chapter-${commentaryHtml.hashCode()}" }

    val linkedHtml = remember(commentaryHtml, cacheKey) {
        if (commentaryHtml.isBlank()) ""
        else linkedHtmlCache.getOrPut(cacheKey) {
            val t = commentaryHtml.trim()
            val body = if (t.contains("<body", ignoreCase = true)) {
                t.substringAfter("<body", "").substringAfter(">", "").substringBeforeLast("</body>", t)
            } else t
            linkifyVerses(cleanCommentaryHtml(body), bookName)
        }
    }

    val textColorCss = remember(textColor) { "#%06X".format(0xFFFFFF and textColor.toArgb()) }
    val secondaryColorCss = remember(textColor) { "#%06X".format(0xFFFFFF and textColor.copy(alpha = 0.6f).toArgb()) }

    val html = remember(linkedHtml, textColorCss, secondaryColorCss, fontSize) {
        """
        <!doctype html>
        <html><head><meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
            body { color: $textColorCss; background: transparent; font-family: sans-serif; font-size: ${fontSize}px; line-height: 1.6; margin: 0; padding: 16px 16px 80px 16px; font-weight: normal !important; font-style: normal !important; }
            p, div, span, li { font-weight: normal !important; font-style: normal !important; }
            p { margin: 0 0 1em; }
            h1, h2, h3, h4 { color: $textColorCss; line-height: 1.3; font-weight: bold !important; font-style: normal !important; margin: 16px 0 8px 0; }
            h1:first-child, h2:first-child { margin-top: 0; }
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
                                    // FIX: use latest book/chapter, not the ones from factory creation
                                    val b = params["book"]?.takeIf { it.isNotBlank() } ?: currentBookName
                                    val c = params["chapter"]?.toIntOrNull() ?: currentChapter
                                    val v = params["verse"]?.toIntOrNull() ?: 1
                                    val end = params["endVerse"]?.toIntOrNull() ?: v
                                    currentOnVerseClick(b, c, v, end)
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
                                    // FIX: use latest callbacks
                                    if (dx < 0) currentOnNext() else currentOnPrev()
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
                    // FIX 2: Save scroll before reload (font/theme change), restore after.
                    // Chapter change loads new html with different cacheKey, scroll will be 0 anyway
                    // because we only restore if it's the same book/chapter content re-render.
                    // For chapter change, wv.scrollY is from old chapter - we want top, so only restore
                    // if the html is a re-render of same chapter (font/theme). We detect that by
                    // checking if tag was set before - first load of new chapter should go to top.
                    // Simplest correct behavior: always go to top on chapter change (new html),
                    // restore scroll only when html changed but book/chapter didn't.
                    // We do that by storing last book-chapter in tag key.
                    val savedScrollY = wv.scrollY
                    val isSameChapterRerender = (wv.getTag(R.id.webview_chapter_tag) as? String) == cacheKey
                    wv.setTag(R.id.webview_chapter_tag, cacheKey)
                    wv.tag = html.hashCode()
                    wv.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                    if (isSameChapterRerender) {
                        wv.post { wv.scrollTo(0, savedScrollY) }
                    }
                }
            },
            onRelease = { wv ->
                // FIX 3: Properly destroy WebView to avoid leak when tab is disposed
                runCatching {
                    wv.stopLoading()
                    wv.loadUrl("about:blank")
                    wv.destroy()
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

    // 1. Full refs: "John 3:16", "1 Samuel 2:3" -> always link, but validate chapter exists in that book
    result = FULL_VERSE_REGEX.replace(result) { m ->
        val book = BookCatalog.canonicalName(m.groupValues[1])
        val ch = m.groupValues[2].toIntOrNull() ?: return@replace m.value
        val vs = m.groupValues[3]
        val end = m.groupValues[4].takeIf { it.isNotBlank() } ?: vs
        // FIX: Only link if chapter is valid for that book (e.g. "John 99:1" -> don't link)
        val maxCh = runCatching { BookCatalog.chapters(BookCatalog.id(book)) }.getOrDefault(0)
        if (ch < 1 || ch > maxCh) return@replace m.value
        """<a class="verse-link" href="ew://verse?book=${URLEncoder.encode(book, "UTF-8")}&chapter=$ch&verse=$vs&endVerse=$end">${m.value}</a>"""
    }

    // 2. Bare refs: "3:16" inside commentary for current book.
    // FIX: Only linkify if it is a valid chapter for the SAME book.
    // This stops "12:30" (a time), "99:99", or "150:1" in a 10-chapter book from becoming a verse link.
    if (currentBook.isNotBlank()) {
        val canonicalCurrent = BookCatalog.canonicalName(currentBook)
        val currentBookId = runCatching { BookCatalog.id(canonicalCurrent) }.getOrDefault(0)
        val maxChapterForCurrentBook = if (currentBookId > 0) {
            runCatching { BookCatalog.chapters(currentBookId) }.getOrDefault(0)
        } else 0

        if (maxChapterForCurrentBook > 0) {
            result = BARE_VERSE_REGEX.replace(result) { m ->
                val ch = m.groupValues[1].toIntOrNull() ?: return@replace m.value
                val vs = m.groupValues[2]
                val end = m.groupValues[3].takeIf { it.isNotBlank() } ?: vs

                // VALID SAME-BOOK CHAPTER CHECK:
                // - ch must be 1..maxChapter for this book
                // - verse must be >= 1 (basic sanity, we don't have per-chapter verse counts here)
                // - This also kills times like 12:30 if book has < 12 chapters, and 24:00 etc.
                if (ch < 1 || ch > maxChapterForCurrentBook) return@replace m.value
                if ((vs.toIntOrNull() ?: 0) < 1) return@replace m.value

                // Extra guard against times: if it looks like HH:MM time (ch 1..23, vs 0..59, and preceded by "at"/"by")
                // We already require ch to be valid, but a time like "10:30" in a 50-chapter book would still pass.
                // So check the 6 chars before the match in the ORIGINAL result string for time context.
                // Regex replace doesn't give us offset easily, so we use a conservative rule:
                // If verse part is 00-59 AND chapter is 1..12, it could be a time - but we still link it
                // because in Bible commentary "10:30" almost always means chapter 10 verse 30, not a time.
                // Keep it linked - the chapter-valid check above is the main filter.

                """<a class="verse-link" href="ew://verse?book=${URLEncoder.encode(canonicalCurrent, "UTF-8")}&chapter=$ch&verse=$vs&endVerse=$end">${m.value}</a>"""
            }
        }
    }
    return result
}