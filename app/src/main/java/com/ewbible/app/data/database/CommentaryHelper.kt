package com.ewbible.app.data.database

import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.zip.Inflater

object CommentaryHelper {

    private val htmlCache = LruCache<Long, String>(60)
    private val htmlCacheLock = Any()
    private fun getHtmlCached(k: Long): String? = synchronized(htmlCacheLock) { htmlCache.get(k) }
    private fun putHtmlCached(k: Long, html: String) = synchronized(htmlCacheLock) { htmlCache.put(k, html) }

    // NEW: separate cache for AI plain text - so we don't strip HTML every time
    private val textCache = LruCache<Long, String>(60)
    private fun getTextCached(k: Long): String? = synchronized(htmlCacheLock) { textCache.get(k) }
    private fun putTextCached(k: Long, text: String) = synchronized(htmlCacheLock) { textCache.put(k, text) }

    private val bufferPool = ThreadLocal.withInitial { ByteArray(65536) }
    private val outPool = ThreadLocal.withInitial { ByteArray(262144) }

    private fun key(bookId: Int, chapter: Int): Long =
        (bookId.toLong() shl 32) or chapter.toLong()

    // --- YOUR ORIGINAL - KEEP, already correct zlib ---
    fun decompress(compressed: ByteArray?): String {
        if (compressed == null || compressed.isEmpty()) return ""
        val inflater = Inflater()
        try {
            inflater.setInput(compressed)
            val buf = bufferPool.get()!!
            var out = outPool.get()!!
            var outLen = 0
            while (!inflater.finished()) {
                if (outLen + 65536 > out.size) {
                    val grown = ByteArray(out.size * 2)
                    System.arraycopy(out, 0, grown, 0, outLen)
                    out = grown
                    outPool.set(out)
                }
                val n = inflater.inflate(buf)
                if (n == 0) {
                    if (inflater.needsInput()) break
                    if (inflater.needsDictionary()) break
                }
                System.arraycopy(buf, 0, out, outLen, n)
                outLen += n
            }
            return String(out, 0, outLen, Charsets.UTF_8)
        } catch (e: Exception) {
            return ""
        } finally {
            inflater.end()
        }
    }

    // --- NEW: FOR AI ONLY ---
    // Usage in ChatViewModel: CommentaryHelper.getPlainTextCommentary(bookId, chapter, compressed)
    fun getPlainTextCommentary(bookId: Int, chapter: Int, compressed: ByteArray?): String {
        if (compressed == null || compressed.isEmpty()) return ""
        val k = key(bookId, chapter)
        getTextCached(k)?.let { return it }
        val html = getHtmlCommentary(bookId, chapter, compressed)
        val plain = stripHtml(html).take(2500)
        if (plain.isNotBlank()) putTextCached(k, plain)
        return plain
    }

    fun getPlainTextCommentary(compressed: ByteArray?): String {
        if (compressed == null || compressed.isEmpty()) return ""
        return stripHtml(cleanHtml(decompress(compressed))).take(2500)
    }

    private fun stripHtml(html: String): String {
        var t = html
        // <br>, </p>, </div>, </h2> -> newline
        t = t.replace(Regex("(?i)<br[^>]*>"), "\n")
        t = t.replace(Regex("(?i)</(p|div|h[1-6]|li|tr)[^>]*>"), "\n")
        // remove all other tags
        t = t.replace(Regex("<[^>]+>"), "")
        // decode entities
        t = t.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
        // collapse whitespace, keep paragraphs
        t = t.replace(Regex("[ \t]+"), " ")
        t = t.replace(Regex("\n\\s*\n+"), "\n")
        return t.trim()
    }

    // --- YOUR ORIGINAL cleanHtml etc - KEEP ALL BELOW ---
    fun cleanHtml(html: String): String {
        if (html.isEmpty()) return ""
        var t = html
        val hasScript = t.contains("<script", ignoreCase = true)
        val hasStyle = t.contains("<style", ignoreCase = true)
        val hasHead = t.contains("<head", ignoreCase = true)
        val hasBody = t.contains("<body", ignoreCase = true)
        if (!hasScript && !hasStyle && !hasHead && !hasBody) {
            val cleaned = if (t.isNotEmpty() && (t[0].isWhitespace() || t[t.length - 1].isWhitespace())) t.trim() else t
            return startFromA(cleaned)
        }
        if (hasScript) t = removeTag(t, "script")
        if (hasStyle) t = removeTag(t, "style")
        if (hasHead) t = removeTag(t, "head")
        if (hasBody) {
            val start = t.indexOf('>', t.indexOf("<body", ignoreCase = true))
            if (start != -1) t = t.substring(start + 1)
            val end = t.lastIndexOf("</body>", ignoreCase = true)
            if (end != -1) t = t.substring(0, end)
        }
        return startFromA(t).trim()
    }

    private fun removeTag(html: String, tag: String): String {
        val open = "<$tag"
        val close = "</$tag>"
        var start = html.indexOf(open, ignoreCase = true)
        if (start == -1) return html
        val sb = StringBuilder(html.length)
        var pos = 0
        while (start != -1) {
            sb.append(html, pos, start)
            val contentStart = html.indexOf('>', start)
            if (contentStart == -1) break
            val end = html.indexOf(close, contentStart, ignoreCase = true)
            if (end == -1) break
            val endTag = html.indexOf('>', end)
            if (endTag == -1) break
            pos = endTag + 1
            start = html.indexOf(open, pos, ignoreCase = true)
        }
        sb.append(html, pos, html.length)
        return sb.toString()
    }

    private fun startFromA(html: String): String {
        val aHeading = Regex(
            """(?i)<(h[1-6]|p|div)[^>]*>(?:\s*<[^>]+>)*\s*A\.(?:\s|&nbsp;|<)"""
        )
        val match = aHeading.find(html)
        if (match != null) {
            return html.substring(match.range.first)
        }
        return html
    }

    fun getHtmlCommentary(compressed: ByteArray?): String {
        if (compressed == null || compressed.isEmpty()) return ""
        return cleanHtml(decompress(compressed))
    }

    fun getHtmlCommentary(bookId: Int, chapter: Int, compressed: ByteArray?): String {
        if (compressed == null || compressed.isEmpty()) return ""
        val k = key(bookId, chapter)
        getHtmlCached(k)?.let { return it }
        val html = cleanHtml(decompress(compressed))
        putHtmlCached(k, html)
        return html
    }

    fun getCached(bookId: Int, chapter: Int): String? {
        return getHtmlCached(key(bookId, chapter))
    }

    fun hasCached(bookId: Int, chapter: Int): Boolean {
        return getHtmlCached(key(bookId, chapter)) != null
    }

    fun preload(bookId: Int, chapter: Int, compressed: ByteArray?) {
        if (compressed == null || compressed.isEmpty()) return
        val k = key(bookId, chapter)
        if (getHtmlCached(k) != null) return
        val html = cleanHtml(decompress(compressed))
        putHtmlCached(k, html)
    }

    fun preloadNext(
        scope: CoroutineScope,
        currentBookId: Int,
        currentChapter: Int,
        loader: suspend (bookId: Int, chapter: Int) -> ByteArray?
    ) {
        scope.launch(Dispatchers.IO) {
            val total = BookCatalog.chapters(currentBookId)
            if (currentChapter < total) {
                preloadOne(currentBookId, currentChapter + 1, loader)
            } else {
                val nextBookId = currentBookId + 1
                if (nextBookId <= 66) {
                    preloadOne(nextBookId, 1, loader)
                }
            }
            if (currentChapter > 1) {
                preloadOne(currentBookId, currentChapter - 1, loader)
            }
        }
    }

    private suspend fun preloadOne(
        bookId: Int,
        chapter: Int,
        loader: suspend (bookId: Int, chapter: Int) -> ByteArray?
    ) {
        if (bookId !in 1..66) return
        if (chapter !in 1..BookCatalog.chapters(bookId)) return
        val k = key(bookId, chapter)
        if (getHtmlCached(k) != null) return
        runCatching {
            val compressed = loader(bookId, chapter) ?: return
            val html = cleanHtml(decompress(compressed))
            if (html.isNotBlank()) putHtmlCached(k, html)
        }
    }

    fun clearCache() {
        synchronized(htmlCacheLock) { htmlCache.evictAll(); textCache.evictAll() }
    }
}