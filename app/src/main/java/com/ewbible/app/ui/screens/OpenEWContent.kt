package com.ewbible.app.ui.screens

import android.text.Spannable
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.ForegroundColorSpan
import android.text.style.URLSpan
import android.util.LruCache
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import com.ewbible.app.data.database.BookCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.math.abs

private val DISPLAY_NAMES: List<String> = BookCatalog.allDisplayNames()
private val BOOK_PATTERN = DISPLAY_NAMES
    .sortedByDescending { it.length }
    .joinToString("|") { Regex.escape(it) }
private val VERSE_REGEX = Regex("(?<![A-Za-z])($BOOK_PATTERN)\\s+(\\d+):(\\d+)(?:\\s*-\\s*(\\d+))?(?!\\d)")

private val H1_REGEX = Regex("(?is)<h1[^>]*>.*?</h1>")
private val H2_6_REGEX = Regex("(?is)<h[2-6][^>]*>(.*?)</h[2-6]>")
private val TAG_REGEX = Regex("(?i)</?(b|strong|i|em)>")
private val BOLD_REGEX = Regex("(?i)font-weight\\s*:\\s*(bold|[700-900])\\s*;?")
private val ITALIC_REGEX = Regex("(?i)font-style\\s*:\\s*italic\\s*;?")

private val parsedCache = LruCache<String, SpannableString>(10)

private fun cleanForHtml(html: String, bookName: String, chapter: Int): String {
    if (html.isBlank()) return ""
    var t = html.trim()
    if (t.contains("<body", ignoreCase = true)) {
        val bodyStart = t.indexOf('>', t.indexOf("<body", ignoreCase = true))
        if (bodyStart != -1) t = t.substring(bodyStart + 1)
        val bodyEnd = t.lastIndexOf("</body>")
        if (bodyEnd != -1) t = t.substring(0, bodyEnd)
    }
    t = H1_REGEX.replaceFirst(t, "")
    val titleTag = "<p"
    val pStart = t.indexOf(titleTag, ignoreCase = true)
    if (pStart != -1 && pStart < 200) {
        val pEnd = t.indexOf("</p>", pStart, ignoreCase = true)
        if (pEnd != -1) {
            val inside = t.substring(pStart, pEnd)
            val textOnly = inside.substringAfter('>').trim()
            if (textOnly.equals("$bookName $chapter", ignoreCase = true)) {
                t = t.substring(pEnd + 4)
            }
        }
    }
    t = H2_6_REGEX.replace(t, "<p><b>$1</b></p>")
    t = TAG_REGEX.replace(t, "")
    t = BOLD_REGEX.replace(t, "")
    t = ITALIC_REGEX.replace(t, "")
    return t
}

private fun linkifyForHtml(html: String): String {
    if (html.isBlank()) return html
    if (!html.contains('<')) return VERSE_REGEX.replace(html) { linkifyMatch(it) }
    val out = StringBuilder(html.length + 1024)
    var i = 0
    val len = html.length
    while (i < len) {
        val lt = html.indexOf('<', i)
        if (lt == -1) {
            out.append(VERSE_REGEX.replace(html.substring(i), ::linkifyMatch))
            break
        }
        if (lt > i) out.append(VERSE_REGEX.replace(html.substring(i, lt), ::linkifyMatch))
        val gt = html.indexOf('>', lt)
        if (gt == -1) { out.append(html.substring(lt)); break }
        out.append(html, lt, gt + 1)
        i = gt + 1
    }
    return out.toString()
}

private fun linkifyMatch(m: MatchResult): String {
    val raw = m.groupValues[1].trim()
    val bookId = BookCatalog.id(raw)
    val canonical = BookCatalog.canonicalName(raw)
    val ch = m.groupValues[2].toIntOrNull() ?: return m.value
    val vs = m.groupValues[3].toIntOrNull() ?: return m.value
    val endRaw = m.groupValues[4]
    val end = if (endRaw.isBlank()) vs else endRaw.toIntOrNull() ?: vs
    if (ch !in 1..BookCatalog.chapters(bookId)) return m.value
    val encoded = URLEncoder.encode(canonical, "UTF-8")
    return """<a href="ew://verse?book=$encoded&chapter=$ch&verse=$vs&endVerse=$end">${m.value}</a>"""
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
    val currentBookName by rememberUpdatedState(bookName)
    val currentChapter by rememberUpdatedState(chapter)
    val currentOnVerseClick by rememberUpdatedState(onVerseClick)
    val currentOnNext by rememberUpdatedState(onNextChapter)
    val currentOnPrev by rememberUpdatedState(onPreviousChapter)
    val density = LocalDensity.current
    val effectiveFontSize = fontSize.coerceAtLeast(18f)

    val htmlHash = commentaryHtml.hashCode()
    val cacheKey = "$bookName-$chapter-$htmlHash"

    val parsed by produceState<SpannableString?>(initialValue = parsedCache.get(cacheKey), cacheKey) {
        if (commentaryHtml.isBlank()) { value = null; return@produceState }
        parsedCache.get(cacheKey)?.let { value = it; return@produceState }
        value = withContext(Dispatchers.Default) {
            runCatching {
                val parsedHtml = HtmlCompat.fromHtml(
                    linkifyForHtml(cleanForHtml(commentaryHtml, bookName, chapter)),
                    HtmlCompat.FROM_HTML_MODE_LEGACY, null, null
                )
                val spannable = parsedHtml as? Spannable ?: SpannableString(parsedHtml)
                spannable.getSpans(0, spannable.length, URLSpan::class.java).forEach { span ->
                    spannable.setSpan(
                        ForegroundColorSpan(0xFFC9A227.toInt()),
                        spannable.getSpanStart(span), spannable.getSpanEnd(span),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                val result = if (spannable is SpannableString) spannable else SpannableString(spannable)
                parsedCache.put(cacheKey, result)
                result
            }.getOrNull()
        }
    }

    // ✅ INSTANT: keep last good commentary until new one parses - never blank / never "No commentary" flash
    var cachedSpanned by remember { mutableStateOf<SpannableString?>(null) }
    var cachedKey by remember { mutableStateOf<String?>(null) }
    if (parsed != null) {
        if (cachedKey != cacheKey) {
            cachedSpanned = parsed
            cachedKey = cacheKey
        }
    }
    val goldSpanned = cachedSpanned

    if (hasError && goldSpanned == null) {
        Column(modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(18.dp)) {
            Text(
                "Could not load commentary. Please try again.",
                color = textColor.copy(alpha = 0.6f),
                fontSize = effectiveFontSize.sp,
                lineHeight = (effectiveFontSize + 8).sp
            )
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC9A227))) {
                Text("Try Again", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
        if (goldSpanned == null) {
            // ✅ REMOVED flash: only show "No commentary" when truly empty + NOT loading + no cached
            // While switching chapters, this branch is skipped because cachedSpanned is still old chapter
            if (!isLoading && commentaryHtml.isBlank()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
                    Text(
                        "No commentary available for this chapter.",
                        color = textColor.copy(alpha = 0.6f),
                        fontSize = effectiveFontSize.sp,
                        lineHeight = (effectiveFontSize + 8).sp
                    )
                }
            } else {
                // Parsing new chapter - show nothing, keep layout stable (no blank jump)
                Box(modifier = Modifier.weight(1f).fillMaxWidth())
            }
        } else {
            AndroidView(
                modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds(),
                factory = { ctx ->
                    val padPx = with(density) { 16.dp.roundToPx() }
                    // ✅ FIT: 120dp bottom = FAB + bottom bar + nav bar, last line never hidden
                    val bottomPx = with(density) { 120.dp.roundToPx() }
                    val swipePx = with(density) { 80.dp.toPx() }
                    var swipeFired = false
                    var startX = 0f
                    var startY = 0f
                    lateinit var gestureDetector: GestureDetector
                    val textView = object : TextView(ctx) {
                        override fun onTouchEvent(event: MotionEvent): Boolean {
                            when (event.action) {
                                MotionEvent.ACTION_DOWN -> {
                                    swipeFired = false
                                    startX = event.x
                                    startY = event.y
                                }
                                MotionEvent.ACTION_MOVE -> {
                                    // ✅ INSTANT: fire mid-drag at 80dp, don't wait for fling / lift
                                    if (!swipeFired) {
                                        val dx = event.x - startX
                                        val dy = event.y - startY
                                        if (abs(dx) > swipePx && abs(dx) > abs(dy) * 1.5f) {
                                            swipeFired = true
                                            if (dx < 0) currentOnNext() else currentOnPrev()
                                            return true
                                        }
                                    }
                                }
                            }
                            gestureDetector.onTouchEvent(event)
                            if (swipeFired) return true
                            return super.onTouchEvent(event)
                        }
                    }.apply {
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, effectiveFontSize)
                        setLineSpacing(0f, 1.45f)
                        setTextColor(textColor.toArgb())
                        setPadding(padPx, 0, padPx, bottomPx)
                        isVerticalScrollBarEnabled = true
                        scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
                        movementMethod = object : LinkMovementMethod() {
                            override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
                                if (event.action == MotionEvent.ACTION_UP) {
                                    val layout = widget.layout ?: return super.onTouchEvent(widget, buffer, event)
                                    val x = event.x.toInt() - widget.totalPaddingLeft + widget.scrollX
                                    val y = event.y.toInt() - widget.totalPaddingTop + widget.scrollY
                                    if (y < 0 || y > layout.height) return super.onTouchEvent(widget, buffer, event)
                                    val line = layout.getLineForVertical(y)
                                    val offset = layout.getOffsetForHorizontal(line, x.toFloat())
                                    val link = buffer.getSpans(offset, offset, URLSpan::class.java).firstOrNull()
                                    if (link != null && link.url.startsWith("ew://verse")) {
                                        runCatching {
                                            val query = link.url.substringAfter("?", "")
                                            val params = query.split("&").associate {
                                                val parts = it.split("=", limit = 2)
                                                if (parts.size == 2) parts[0] to URLDecoder.decode(parts[1], "UTF-8") else parts[0] to ""
                                            }
                                            val b = params["book"]?.takeIf { it.isNotBlank() } ?: currentBookName
                                            val c = params["chapter"]?.toIntOrNull() ?: currentChapter
                                            val v = params["verse"]?.toIntOrNull() ?: 1
                                            val end = params["endVerse"]?.toIntOrNull() ?: v
                                            currentOnVerseClick(b, c, v, end)
                                        }
                                        return true
                                    }
                                }
                                return super.onTouchEvent(widget, buffer, event)
                            }
                        }
                    }
                    gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                        override fun onDown(e: MotionEvent): Boolean = false
                    })
                    textView
                },
                update = { textView ->
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, effectiveFontSize)
                    textView.setTextColor(textColor.toArgb())
                    if (textView.tag !== goldSpanned) {
                        textView.tag = goldSpanned
                        textView.text = goldSpanned
                        textView.scrollTo(0, 0)
                    }
                },
                onRelease = { textView ->
                    runCatching { textView.text = "" }
                }
            )
        }
    }
}