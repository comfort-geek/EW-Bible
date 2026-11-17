package com.ewbible.app.data.database

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object CommentaryUtils {

    fun compressHtml(html: String): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(html.toByteArray(Charsets.UTF_8)) }
        return out.toByteArray()
    }

    fun decompressHtml(compressed: ByteArray?): String {
        // FIXED: ByteArray has no isNullOrEmpty(), use == null || isEmpty()
        if (compressed == null || compressed.isEmpty()) return ""
        return try {
            GZIPInputStream(ByteArrayInputStream(compressed)).use { gzip ->
                gzip.readBytes().toString(Charsets.UTF_8)
            }
        } catch (e: Exception) {
            ""
        }
    }

    // Aliases in case other files call these shorter names
    fun compress(html: String): ByteArray = compressHtml(html)
    fun decompress(compressed: ByteArray?): String = decompressHtml(compressed)
}