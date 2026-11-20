package com.ewbible.app.data.database

import android.util.LruCache
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.InflaterInputStream

object CommentaryHelper {

    private val htmlCacheByRef = LruCache<Int, String>(40)

    fun decompress(compressed: ByteArray?): String {
        if (compressed == null || compressed.isEmpty()) return ""
        return try {
            InflaterInputStream(ByteArrayInputStream(compressed)).use { input ->
                ByteArrayOutputStream().use { out ->
                    val buf = ByteArray(8192)
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        out.write(buf, 0, n)
                    }
                    out.toString(Charsets.UTF_8.name())
                }
            }
        } catch (e: Exception) {
            ""
        }
    }

    // For Open EW display - keeps <h2>, <b>, <p>, <a>, <i>, lists.
    // NO 4000 char cut. Only removes <script>, <style>, <head>.
    fun cleanHtml(html: String): String {
        if (html.isEmpty()) return ""
        var t = html.trim()
        t = t.replace(Regex("(?is)<script.*?</script>"), "")
        t = t.replace(Regex("(?is)<style.*?</style>"), "")
        t = t.replace(Regex("(?is)<head.*?</head>"), "")
        if (t.contains("<body", ignoreCase = true)) {
            val start = t.indexOf('>', t.indexOf("<body", ignoreCase = true))
            if (start != -1) t = t.substring(start + 1)
            val end = t.lastIndexOf("</body>")
            if (end != -1) t = t.substring(0, end)
        }
        return t.trim()
    }

    // Use this for OpenEWContent
    fun getHtmlCommentary(compressed: ByteArray?): String {
        if (compressed == null || compressed.isEmpty()) return ""
        val key = compressed.contentHashCode()
        htmlCacheByRef.get(key)?.let { return it }
        val html = cleanHtml(decompress(compressed))
        htmlCacheByRef.put(key, html)
        return html
    }

    fun getHtmlCommentary(bookId: Int, chapter: Int, compressed: ByteArray?): String {
        if (compressed == null || compressed.isEmpty()) return ""
        val key = bookId * 1000 + chapter
        htmlCacheByRef.get(key)?.let { return it }
        val html = cleanHtml(decompress(compressed))
        htmlCacheByRef.put(key, html)
        return html
    }

    fun clearCache() {
        htmlCacheByRef.evictAll()
    }
}