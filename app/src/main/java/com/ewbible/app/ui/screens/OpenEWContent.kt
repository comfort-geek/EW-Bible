package com.ewbible.app.ui.screens

import android.text.Spannable
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.ForegroundColorSpan
import android.text.style.URLSpan
import android.util.TypedValue
import android.view.MotionEvent
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.math.abs

// FIX: Single source of truth = BookCatalog. No hardcoded list anymore.
// Includes canonical names + aliases: Gen, Ps, 1 Sam, 1 Cor, Rev, Song of Songs, etc.
private val DISPLAY_NAMES: List<String> = BookCatalog.allDisplayNames()
private val BOOK_PATTERN = DISPLAY_NAMES
    .sortedByDescending { it.length } // "1 Samuel" before "Samuel", "Song of Songs" before "Song"
    .joinToString("|") { Regex.escape(it) }
private val VERSE_REGEX = Regex("(?<![A-Za-z])($BOOK_PATTERN)\\s+(\\d+):(\\d+)(?:\\s*-\\s*(\\d+))?(?!\\d)")

private fun cleanForHtml(html: String): String {
    if (html.isBlank()) return ""
    var t = html.trim()
    if (t.contains("<body", ignoreCase = true)) {
        t = t.substringAfter("<body", "").substringAfter(">", "").substringBeforeLast("</body>", t)
    }
    t = t.replace(Regex("(?is)<h1[^>]*>.*?</h1>"), "", 1)
    t = t.replace(Regex("(?is)<h[2-6][^>]*>(.*?)</h[2-6]>"), "<p><b>$1</b></p>")
    t = t.replace(Regex("(?i)</?(b|strong|i|em)>"), "")
    t = t.replace(Regex("(?i)font-weight\\s*:\\s*(bold|[700-900])\\s*;?"), "")
    t = t.replace(Regex("(?i)font-style\\s*:\\s*italic\\s*;?"), "")
    return t
}

private fun linkifyForHtml(html: String): String {
    if (html.isBlank()) return html
    return VERSE_REGEX.replace(html) { m ->
        val raw = m.groupValues[1].trim()
        // "Ps" -> id 19 -> "Psalms", "Gen" -> "Genesis", "1 Sam" -> "1 Samuel"
        // BookCatalog.id() returns 1 for unknown, so guard with a real lookup:
        // if raw is not a known display name, skip. All regex matches ARE known, so this is safe.
        val bookId = BookCatalog.id(raw)
        // Validate: id() defaults to 1, so re-check that canonical name matches or alias exists
        // For our regex set this is always true, but keep the chapter check as the real guard.
        val canonical = BookCatalog.canonicalName(raw) // e.g. "Ps" -> "Psalms"
        val ch = m.groupValues[2].toIntOrNull() ?: return@replace m.value
        val vs = m.groupValues[3].toIntOrNull() ?: return@replace m.value
        val endRaw = m.groupValues[4]
        val end = if (endRaw.isBlank()) vs else endRaw.toIntOrNull() ?: vs
        val maxCh = BookCatalog.chapters(bookId)
        if (ch !in 1..maxCh) return@replace m.value
        // FIX: link with canonical name, so tapping "Ps 19:1" opens "Psalms 19:1" correctly
        val encoded = URLEncoder.encode(canonical, "UTF-8")
        """<a href="ew://verse?book=$encoded&chapter=$ch&verse=$vs&endVerse=$end">${m.value}</a>"""
    }
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

    val goldSpanned = remember(commentaryHtml) {
        if (commentaryHtml.isBlank()) null
        else runCatching {
            val parsed = HtmlCompat.fromHtml(linkifyForHtml(cleanForHtml(commentaryHtml)), HtmlCompat.FROM_HTML_MODE_LEGACY, null, null)
            val copy = SpannableString(parsed)
            copy.getSpans(0, copy.length, URLSpan::class.java).forEach { span ->
                copy.setSpan(ForegroundColorSpan(0xFFC9A227.toInt()), copy.getSpanStart(span), copy.getSpanEnd(span), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            copy
        }.getOrNull()
    }

    if (hasError && goldSpanned == null) {
        Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
            Text("Could not load commentary. Please try again.", color = textColor.copy(alpha = 0.6f), fontSize = fontSize.sp, lineHeight = (fontSize + 8).sp)
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC9A227))) {
                Text("Try Again", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (goldSpanned == null) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
                if (!isLoading) {
                    Text("No commentary available for this chapter.", color = textColor.copy(alpha = 0.6f), fontSize = fontSize.sp, lineHeight = (fontSize + 8).sp)
                }
            }
        } else {
            AndroidView(
                modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds(),
                factory = { ctx ->
                    val padPx = with(density) { 16.dp.roundToPx() }
                    val bottomPx = with(density) { 96.dp.roundToPx() }
                    TextView(ctx).apply {
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize)
                        setLineSpacing(0f, 1.45f)
                        setTextColor(textColor.toArgb())
                        setPadding(padPx, 0, padPx, bottomPx)
                        isVerticalScrollBarEnabled = true
                        scrollBarStyle = TextView.SCROLLBARS_INSIDE_OVERLAY
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
                        var downX = 0f
                        var downY = 0f
                        setOnTouchListener { _, event ->
                            when (event.action) {
                                MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y }
                                MotionEvent.ACTION_UP -> {
                                    val dx = event.x - downX
                                    val dy = event.y - downY
                                    if (abs(dx) > 150 && abs(dx) > abs(dy) * 1.5f) {
                                        if (dx < 0) currentOnNext() else currentOnPrev()
                                        return@setOnTouchListener true
                                    }
                                }
                            }
                            false
                        }
                    }
                },
                update = { textView ->
                    if (textView.tag !== goldSpanned) {
                        textView.tag = goldSpanned
                        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize)
                        textView.setTextColor(textColor.toArgb())
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