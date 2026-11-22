package com.ewbible.app.data.database

import android.util.LruCache
import java.util.zip.Inflater

object CommentaryHelper {

    // Only ONE cache - by book/chapter. No contentHashCode scan.
    private val htmlCache = LruCache<Long, String>(60)

    // Reused buffer per thread - no new ByteArray(8192) on every open
    private val bufferPool = ThreadLocal.withInitial { ByteArray(65536) }
    private val outPool = ThreadLocal.withInitial { ByteArray(262144) }

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
                    // Grow once, not on every chunk
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

    // FAST clean: if there is no <script, <style, <head, <body - return as-is, zero copy.
    // No Regex at all.
    fun cleanHtml(html: String): String {
        if (html.isEmpty()) return ""
        var t = html
        // Fast path: 99% of chapters have no script/style/head - skip everything
        val hasScript = t.contains("<script", ignoreCase = true)
        val hasStyle = t.contains("<style", ignoreCase = true)
        val hasHead = t.contains("<head", ignoreCase = true)
        val hasBody = t.contains("<body", ignoreCase = true)
        if (!hasScript && !hasStyle && !hasHead && !hasBody) {
            // Only trim if needed - avoids copy
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

    // Remove <tag>...</tag> without Regex - single pass, StringBuilder only when needed
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

    // Old overload - keep for compatibility, but DON'T use it for Open EW.
    // It has to hash the whole array, so it's slower.
    fun getHtmlCommentary(compressed: ByteArray?): String {
        if (compressed == null || compressed.isEmpty()) return ""
        return cleanHtml(decompress(compressed))
    }

    // USE THIS ONE for Open EW - fast, no hash scan, cached by book/chapter
    fun getHtmlCommentary(bookId: Int, chapter: Int, compressed: ByteArray?): String {
        if (compressed == null || compressed.isEmpty()) return ""
        val key = (bookId.toLong() shl 32) or chapter.toLong()
        htmlCache.get(key)?.let { return it }
        val html = cleanHtml(decompress(compressed))
        htmlCache.put(key, html)
        return html
    }

    // Call this in background when user opens a chapter - next chapter will be instant
    fun preload(bookId: Int, chapter: Int, compressed: ByteArray?) {
        if (compressed == null || compressed.isEmpty()) return
        val key = (bookId.toLong() shl 32) or chapter.toLong()
        if (htmlCache.get(key) != null) return
        val html = cleanHtml(decompress(compressed))
        htmlCache.put(key, html)
    }

    fun clearCache() {
        htmlCache.evictAll()
    }
}