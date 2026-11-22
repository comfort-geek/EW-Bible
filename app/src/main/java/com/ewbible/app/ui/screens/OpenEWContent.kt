package com.ewbible.app.ui.screens

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.util.LruCache
import android.util.TypedValue
import android.view.MotionEvent
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
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

private val DISPLAY_NAMES: List<String> = BookCatalog.allDisplayNames()
private val BOOK_PATTERN = DISPLAY_NAMES
    .sortedByDescending { it.length }
    .joinToString("|") { Regex.escape(it) }
private val VERSE_REGEX = Regex("(?<![A-Za-z])($BOOK_PATTERN)\\s+(\\d+):(\\d+)(?:\\s*-\\s*(\\d+))?(?!\\d)")
private val H1_REGEX = Regex("(?is)<h1[^>]*>.*?</h1>")

private val parsedCache = LruCache<String, List<SpannableString>>(10)

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

private val HEADING_LINE = Regex("""^(?:[A-Z]\.|[0-9]+\.|[a-z]\.)\s+[A-Z].{0,90}$""")

private fun splitIntoParagraphs(full: SpannableString): List<SpannableString> {
    val text = full.toString()
    if (text.isBlank()) return emptyList()
    val result = ArrayList<SpannableString>()
    var start = 0
    while (start < text.length) {
        var end = text.indexOf("\n\n", start)
        if (end == -1) end = text.length else end += 1
        if (end - start > 2000) {
            val cut = text.indexOf('\n', start + 1000)
            if (cut != -1 && cut < end) end = cut + 1
        }
        val chunkText = text.substring(start, end).trim()
        if (chunkText.isNotEmpty()) {
            val chunk = SpannableString(chunkText)
            val chunkStart = start + (text.substring(start, end).indexOf(chunkText))
            val chunkEnd = chunkStart + chunkText.length
            full.getSpans(chunkStart, chunkEnd, Any::class.java).forEach { span ->
                val s = maxOf(full.getSpanStart(span), chunkStart)
                val e = minOf(full.getSpanEnd(span), chunkEnd)
                if (e > s) {
                    chunk.setSpan(span, s - chunkStart, e - chunkStart, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            if (chunkText.length in 5..110 && HEADING_LINE.matches(chunkText)) {
                chunk.setSpan(StyleSpan(Typeface.BOLD), 0, chunkText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                chunk.setSpan(RelativeSizeSpan(1.12f), 0, chunkText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            result.add(chunk)
        }
        start = end + 1
        while (start < text.length && text[start] == '\n') start++
    }
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
    val currentBookName by rememberUpdatedState(bookName)
    val currentChapter by rememberUpdatedState(chapter)
    val currentOnVerseClick by rememberUpdatedState(onVerseClick)
    val currentOnNext by rememberUpdatedState(onNextChapter)
    val currentOnPrev by rememberUpdatedState(onPreviousChapter)
    val density = LocalDensity.current
    val effectiveFontSize = fontSize.coerceAtLeast(18f)

    val htmlHash = commentaryHtml.hashCode()
    val cacheKey = "$bookName-$chapter-$htmlHash"
    val listState = rememberLazyListState()

    val paragraphs by produceState<List<SpannableString>?>(initialValue = parsedCache.get(cacheKey), cacheKey) {
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
                val full = if (spannable is SpannableString) spannable else SpannableString(spannable)
                val split = splitIntoParagraphs(full)
                parsedCache.put(cacheKey, split)
                split
            }.getOrNull()
        }
    }

    if (hasError && paragraphs == null) {
        Column(modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(18.dp)) {
            Text(
                "Could not load commentary. Please try again.",
                color = textColor.copy(alpha = 0.6f),
                fontSize = effectiveFontSize.sp,
                lineHeight = (effectiveFontSize + 8).sp
            )
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Text(
            text = if (bookName.isNotBlank()) "$bookName $chapter" else "",
            color = textColor,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
        )

        if (paragraphs == null) {
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
                Box(modifier = Modifier.weight(1f).fillMaxWidth())
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 180.dp)
            ) {
                itemsIndexed(
                    items = paragraphs!!,
                    key = { index, _ -> "$cacheKey-$index" }
                ) { _, paragraph ->
                    AndroidView(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        factory = { ctx ->
                            TextView(ctx).apply {
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, effectiveFontSize)
                                setLineSpacing(0f, 1.45f)
                                setTextColor(textColor.toArgb())
                                breakStrategy = android.text.Layout.BREAK_STRATEGY_SIMPLE
                                hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_NONE
                                isVerticalScrollBarEnabled = false
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
                        },
                        update = { textView ->
                            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, effectiveFontSize)
                            textView.setTextColor(textColor.toArgb())
                            if (textView.tag !== paragraph) {
                                textView.tag = paragraph
                                textView.text = paragraph
                            }
                        }
                    )
                }
            }
        }
    }
}