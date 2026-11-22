package com.ewbible.app.data.database

import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.zip.Inflater

object CommentaryHelper {

    private val htmlCache = LruCache<Long, String>(60)

    private val bufferPool = ThreadLocal.withInitial { ByteArray(65536) }
    private val outPool = ThreadLocal.withInitial { ByteArray(262144) }

    private fun key(bookId: Int, chapter: Int): Long =
        (bookId.toLong() shl 32) or chapter.toLong()

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

    fun cleanHtml(html: String): String {
        if (html.isEmpty()) return ""
        var t = html
        val hasScript = t.contains("<script", ignoreCase = true)
        val hasStyle = t.contains("<style", ignoreCase = true)
        val hasHead = t.contains("<head", ignoreCase = true)
        val hasBody = t.contains("<body", ignoreCase = true)
        if (!hasScript && !hasStyle && !hasHead && !hasBody) {
            return if (t.isNotEmpty() && (t[0].isWhitespace() || t[t.length - 1].isWhitespace())) t.trim() else t
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
        return t.trim()
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

    fun getHtmlCommentary(compressed: ByteArray?): String {
        if (compressed == null || compressed.isEmpty()) return ""
        return cleanHtml(decompress(compressed))
    }

    fun getHtmlCommentary(bookId: Int, chapter: Int, compressed: ByteArray?): String {
        if (compressed == null || compressed.isEmpty()) return ""
        val k = key(bookId, chapter)
        htmlCache.get(k)?.let { return it }
        val html = cleanHtml(decompress(compressed))
        htmlCache.put(k, html)
        return html
    }

    // FIX 1: Check cache without hitting DB - for instant Next
    fun getCached(bookId: Int, chapter: Int): String? {
        return htmlCache.get(key(bookId, chapter))
    }

    fun hasCached(bookId: Int, chapter: Int): Boolean {
        return htmlCache.get(key(bookId, chapter)) != null
    }

    // FIX 2: Preload from bytes (your old version - keep it)
    fun preload(bookId: Int, chapter: Int, compressed: ByteArray?) {
        if (compressed == null || compressed.isEmpty()) return
        val k = key(bookId, chapter)
        if (htmlCache.get(k) != null) return
        val html = cleanHtml(decompress(compressed))
        htmlCache.put(k, html)
    }

    // FIX 3: THE INSTANT FIX - preload NEXT chapter by loading from DB in background
    // Call this when user opens a chapter. It fetches next chapter's bytes itself.
    // loader: suspend (bookId, chapter) -> ByteArray?  = your DAO call
    fun preloadNext(
        scope: CoroutineScope,
        currentBookId: Int,
        currentChapter: Int,
        loader: suspend (bookId: Int, chapter: Int) -> ByteArray?
    ) {
        scope.launch(Dispatchers.IO) {
            // Next chapter in same book
            val total = BookCatalog.chapters(currentBookId)
            if (currentChapter < total) {
                preloadOne(currentBookId, currentChapter + 1, loader)
            } else {
                // First chapter of next book - so Next at end of book is also instant
                val nextBookId = currentBookId + 1
                if (nextBookId <= 66) {
                    preloadOne(nextBookId, 1, loader)
                }
            }
            // Also preload previous, so Back is instant too
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
        val k = key(bookId, chapter)
        if (htmlCache.get(k) != null) return
        runCatching {
            val compressed = loader(bookId, chapter) ?: return
            val html = cleanHtml(decompress(compressed))
            if (html.isNotBlank()) htmlCache.put(k, html)
        }
    }

    fun clearCache() {
        htmlCache.evictAll()
    }
}