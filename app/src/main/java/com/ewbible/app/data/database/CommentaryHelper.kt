package com.ewbible.app.data.database

import java.io.ByteArrayInputStream
import java.util.zip.InflaterInputStream

object CommentaryHelper {

    // 1. Decompress zlib BLOB -> HTML string
    fun decompress(compressed: ByteArray?): String {
        if (compressed == null || compressed.isEmpty()) return ""
        return try {
            InflaterInputStream(ByteArrayInputStream(compressed)).use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            }
        } catch (e: Exception) {
            ""
        }
    }

    // 2. HTML -> clean plain text for Gemini (no tags)
    fun htmlToPlainText(html: String): String {
        if (html.isEmpty()) return ""
        return html
            .replace(Regex("<h[1-4][^>]*>"), "\n\n")
            .replace(Regex("</h[1-4]>"), "\n")
            .replace(Regex("<p[^>]*>"), "\n")
            .replace(Regex("</p>"), "\n")
            .replace(Regex("<li[^>]*>"), "\n- ")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#8217;", "'")
            .replace("&#8220;", "\"")
            .replace("&#8221;", "\"")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
            // Gemini limit - don't send 75k chars, send first ~4000
            .take(4000)
    }

    // 3. One call: DB bytes -> clean text for Gemini
    fun getCleanCommentary(compressed: ByteArray?): String {
        return htmlToPlainText(decompress(compressed))
    }
}