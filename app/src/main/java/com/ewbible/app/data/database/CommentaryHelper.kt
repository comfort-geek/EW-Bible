package com.ewbible.app.data.database

import android.util.LruCache
import java.io.ByteArrayInputStream
import java.util.zip.InflaterInputStream

object CommentaryHelper {

    // FIXED: Pre-compile Regex ONCE - was recompiling 13 Regex on every call
    private val H_OPEN = Regex("<h[1-4][^>]*>")
    private val H_CLOSE = Regex("</h[1-4]>")
    private val P_OPEN = Regex("<p[^>]*>")
    private val P_CLOSE = Regex("</p>")
    private val LI_OPEN = Regex("<li[^>]*>")
    private val TAG = Regex("<[^>]+>")
    private val SPACES = Regex("[ \\t]+")
    private val NEWLINES = Regex("\\n{3,}")

    // FIXED: Cache - commentary is re-opened constantly, decompress is expensive.
    // Key = bookId*1000 + chapter is passed by caller via cleanCached(), or hash for clean()
    // 20 entries ~ 20 * 4KB = ~80KB, tiny
    private val cache = LruCache<Int, String>(20)

    // 1. Decompress zlib BLOB -> HTML string, capped so a corrupt/huge BLOB can't OOM
    fun decompress(compressed: ByteArray?): String {
        if (compressed == null || compressed.isEmpty()) return ""
        return try {
            // FIXED: read max 128KB, not unlimited
            InflaterInputStream(ByteArrayInputStream(compressed)).use { input ->
                val buffer = ByteArray(8192)
                val out = StringBuilder(8192)
                var total = 0
                while (total < 131072) {
                    val n = input.read(buffer)
                    if (n <= 0) break
                    out.append(String(buffer, 0, n, Charsets.UTF_8))
                    total += n
                }
                out.toString()
            }
        } catch (e: Exception) {
            ""
        }
    }

    // 2. HTML -> clean plain text for Gemini (no tags)
    fun htmlToPlainText(html: String): String {
        if (html.isEmpty()) return ""
        // FIXED: Use pre-compiled Regex, single pass where possible
        return html
            .replace(H_OPEN, "\n\n")
            .replace(H_CLOSE, "\n")
            .replace(P_OPEN, "\n")
            .replace(P_CLOSE, "\n")
            .replace(LI_OPEN, "\n- ")
            .replace(TAG, " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#8217;", "'")
            .replace("&#8220;", "\"")
            .replace("&#8221;", "\"")
            .replace(SPACES, " ")
            .replace(NEWLINES, "\n\n")
            .trim()
            .take(4000)
    }

    // 3. One call: DB bytes -> clean text for Gemini
    // FIXED: Added clean() - this is what ChatViewModel actually calls
    fun clean(compressed: ByteArray?): String = getCleanCommentary(compressed)

    fun getCleanCommentary(compressed: ByteArray?): String {
        if (compressed == null || compressed.isEmpty()) return ""
        // FIXED: Cache by content hash - same chapter opened twice = 0 decompress
        val key = compressed.contentHashCode()
        cache.get(key)?.let { return it }
        val clean = htmlToPlainText(decompress(compressed))
        cache.put(key, clean)
        return clean
    }

    // 4. BEST for performance: call this from ViewModel with bookId/chapter
    // Avoids hashing 50KB ByteArray every time, uses cheap Int key
    fun cleanCached(bookId: Int, chapter: Int, compressed: ByteArray?): String {
        if (compressed == null || compressed.isEmpty()) return ""
        val key = bookId * 1000 + chapter
        cache.get(key)?.let { return it }
        val clean = htmlToPlainText(decompress(compressed))
        cache.put(key, clean)
        return clean
    }

    fun clearCache() = cache.evictAll()
}