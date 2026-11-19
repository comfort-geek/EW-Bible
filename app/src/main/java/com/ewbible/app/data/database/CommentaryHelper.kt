package com.ewbible.app.data.database

import android.util.LruCache
import java.util.zip.Inflater

object CommentaryHelper {

    // MAX: No Regex anymore. Regex was doing 13 full passes over the string.
    // This does 1 pass, char by char, and stops at 4000 chars.
    private val cacheByHash = LruCache<Int, String>(40)
    private val cacheByRef = LruCache<Int, String>(40)

    // MAX: Reuse buffers per-thread, no new ByteArray(8192) on every call.
    // Inflater is faster than InflaterInputStream - no stream wrapper.
    private val inflaterBuffer = ThreadLocal.withInitial { ByteArray(8192) }
    private val outBuffer = ThreadLocal.withInitial { ByteArray(131072) }

    fun decompress(compressed: ByteArray?): String {
        if (compressed == null || compressed.isEmpty()) return ""
        return try {
            val inflater = Inflater()
            inflater.setInput(compressed)
            val out = outBuffer.get()!!
            val buf = inflaterBuffer.get()!!
            var total = 0
            while (!inflater.finished() && total < 131072) {
                val n = inflater.inflate(buf)
                if (n <= 0) break
                val copy = minOf(n, 131072 - total)
                System.arraycopy(buf, 0, out, total, copy)
                total += copy
                if (inflater.needsInput()) break
            }
            inflater.end()
            // Decode once
            String(out, 0, total, Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    // MAX: 1 pass, no Regex, no 13 intermediate Strings.
    // Old: html.replace().replace().replace() x13 = 13 new Strings.
    // New: 1 StringBuilder, stops as soon as we have 4000 clean chars.
    fun htmlToPlainText(html: String): String {
        if (html.isEmpty()) return ""
        val out = StringBuilder(4000)
        var i = 0
        val len = html.length
        var lastWasSpace = true // start trimmed

        fun appendSpace() {
            if (!lastWasSpace && out.length < 4000) {
                out.append(' ')
                lastWasSpace = true
            }
        }
        fun appendNewline(count: Int) {
            // Collapse to max 2 newlines, no leading newlines
            if (out.isEmpty()) return
            var existing = 0
            var j = out.length - 1
            while (j >= 0 && out[j] == '\n') { existing++; j-- }
            val toAdd = (count - existing).coerceAtLeast(0)
            repeat(toAdd) { if (out.length < 4000) out.append('\n') }
            lastWasSpace = true
        }

        while (i < len && out.length < 4000) {
            val c = html[i]
            when {
                c == '<' -> {
                    // Find end of tag
                    val end = html.indexOf('>', i)
                    if (end == -1) break
                    val tag = html.substring(i + 1, end).trim().lowercase()
                    when {
                        tag.startsWith("h1") || tag.startsWith("h2") ||
                        tag.startsWith("h3") || tag.startsWith("h4") -> {
                            if (!tag.startsWith("/")) appendNewline(2) else appendNewline(1)
                        }
                        tag.startsWith("p") -> appendNewline(1)
                        tag.startsWith("/p") -> appendNewline(1)
                        tag.startsWith("li") -> {
                            appendNewline(1)
                            if (out.length < 3998) { out.append("- "); lastWasSpace = true }
                        }
                        tag.startsWith("br") -> appendNewline(1)
                        else -> appendSpace() // any other tag = space
                    }
                    i = end + 1
                }
                c == '&' -> {
                    val end = html.indexOf(';', i)
                    if (end != -1 && end - i < 10) {
                        val ent = html.substring(i, end + 1)
                        val rep = when (ent) {
                            "&nbsp;" -> " "
                            "&amp;" -> "&"
                            "&quot;" -> "\""
                            "&lt;" -> "<"
                            "&gt;" -> ">"
                            "&#8217;" -> "'"
                            "&#8220;", "&#8221;" -> "\""
                            "&#8211;", "&#8212;" -> "-"
                            else -> null
                        }
                        if (rep != null) {
                            if (rep == " ") appendSpace() else {
                                if (out.length < 4000) out.append(rep)
                                lastWasSpace = false
                            }
                            i = end + 1
                            continue
                        }
                        // Unknown numeric entity &#123;
                        if (ent.startsWith("&#")) {
                            val num = ent.substring(2, ent.length - 1).toIntOrNull()
                            if (num != null && num in 32..10000) {
                                if (out.length < 4000) out.append(num.toChar())
                                lastWasSpace = false
                                i = end + 1
                                continue
                            }
                        }
                    }
                    // Not an entity, keep &
                    if (out.length < 4000) out.append(c)
                    lastWasSpace = false
                    i++
                }
                c == ' ' || c == '\t' || c == '\r' -> {
                    appendSpace()
                    i++
                }
                c == '\n' -> {
                    appendNewline(1)
                    i++
                }
                else -> {
                    if (out.length < 4000) out.append(c)
                    lastWasSpace = false
                    i++
                }
            }
        }
        // Trim trailing spaces/newlines without new String copy if possible
        var endLen = out.length
        while (endLen > 0 && (out[endLen - 1] == ' ' || out[endLen - 1] == '\n')) endLen--
        return out.substring(0, endLen)
    }

    fun clean(compressed: ByteArray?): String = getCleanCommentary(compressed)

    fun getCleanCommentary(compressed: ByteArray?): String {
        if (compressed == null || compressed.isEmpty()) return ""
        val key = compressed.contentHashCode()
        cacheByHash.get(key)?.let { return it }
        val clean = htmlToPlainText(decompress(compressed))
        cacheByHash.put(key, clean)
        return clean
    }

    fun cleanCached(bookId: Int, chapter: Int, compressed: ByteArray?): String {
        if (compressed == null || compressed.isEmpty()) return ""
        val key = bookId * 1000 + chapter
        cacheByRef.get(key)?.let { return it }
        val clean = htmlToPlainText(decompress(compressed))
        cacheByRef.put(key, clean)
        return clean
    }

    fun clearCache() {
        cacheByHash.evictAll()
        cacheByRef.evictAll()
    }
}