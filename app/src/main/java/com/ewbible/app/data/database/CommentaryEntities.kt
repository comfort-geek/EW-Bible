package com.ewbible.app.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import java.io.ByteArrayInputStream
import java.util.zip.InflaterInputStream
import java.util.zip.GZIPInputStream

@Entity(tableName = "commentary", primaryKeys = ["book_id", "chapter"])
data class CommentaryEntity(
    @ColumnInfo(name = "book_id") val bookId: Int,
    @ColumnInfo(name = "chapter") val chapter: Int,
    @ColumnInfo(name = "html_compressed") val htmlCompressed: ByteArray?
) {
    // FIXED: Your DB is zlib (0x78 0xDA), NOT gzip. Old code used GZIP -> always failed.
    fun getHtml(): String {
        val bytes = htmlCompressed ?: return ""
        if (bytes.isEmpty()) return ""

        // 1. Try zlib - this is what your DB actually is
        runCatching {
            InflaterInputStream(ByteArrayInputStream(bytes)).use {
                return it.readBytes().toString(Charsets.UTF_8)
            }
        }

        // 2. Try gzip - fallback for old rows
        runCatching {
            GZIPInputStream(ByteArrayInputStream(bytes)).use {
                return it.readBytes().toString(Charsets.UTF_8)
            }
        }

        // 3. Try raw text - fallback
        runCatching {
            return bytes.toString(Charsets.UTF_8)
        }

        return ""
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CommentaryEntity) return false
        if (bookId != other.bookId) return false
        if (chapter != other.chapter) return false
        if (htmlCompressed != null) {
            if (other.htmlCompressed == null) return false
            if (!htmlCompressed.contentEquals(other.htmlCompressed)) return false
        } else if (other.htmlCompressed != null) return false
        return true
    }

    override fun hashCode(): Int {
        var result = bookId
        result = 31 * result + chapter
        result = 31 * result + (htmlCompressed?.contentHashCode() ?: 0)
        return result
    }
}