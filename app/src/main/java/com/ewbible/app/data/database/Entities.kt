package com.ewbible.app.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: Int,
    val key: String,
    val name: String,
    val testament: String
)

@Entity(tableName = "verses", primaryKeys = ["book_id", "chapter", "verse"])
data class VerseEntity(
    @ColumnInfo(name = "book_id") val bookId: Int,
    val chapter: Int,
    val verse: Int,
    val text: String
)

@Entity(tableName = "summaries", primaryKeys = ["book_id", "chapter"])
data class SummaryEntity(
    @ColumnInfo(name = "book_id") val bookId: Int,
    val chapter: Int,
    val summary: String,
    @ColumnInfo(name = "what_happened") val whatHappened: String,
    val application: String
)

@Entity(tableName = "commentary", primaryKeys = ["book_id", "chapter"])
data class CommentaryEntity(
    @ColumnInfo(name = "book_id") val bookId: Int,
    val chapter: Int,
    @ColumnInfo(name = "html_compressed") val htmlCompressed: ByteArray
) {
    fun getHtml(): String {
        // DB verified in Pydroid: ZLIB (x DA), NOT GZIP, NOT raw deflate
        // Old code used InflaterInputStream.readBytes() - returns "" on Android
        // This uses Inflater + buffer loop - reliable
        try {
            val inflater = java.util.zip.Inflater() // nowrap=false = ZLIB
            inflater.setInput(htmlCompressed)
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(4096)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0) break
                out.write(buf, 0, n)
            }
            inflater.end()
            val text = out.toString(Charsets.UTF_8)
            if (text.isNotBlank()) return text
        } catch (e: Exception) { }

        // Fallback: GZIP
        try {
            val gzip = java.util.zip.GZIPInputStream(htmlCompressed.inputStream())
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(4096)
            while (true) {
                val n = gzip.read(buf)
                if (n == -1) break
                out.write(buf, 0, n)
            }
            val text = out.toString(Charsets.UTF_8)
            if (text.isNotBlank()) return text
        } catch (e: Exception) { }

        // Fallback: Plain HTML
        try {
            return htmlCompressed.toString(Charsets.UTF_8)
        } catch (e: Exception) { return "" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CommentaryEntity) return false
        return bookId == other.bookId && chapter == other.chapter && htmlCompressed.contentEquals(other.htmlCompressed)
    }
    override fun hashCode(): Int {
        var result = bookId
        result = 31 * result + chapter
        result = 31 * result + htmlCompressed.contentHashCode()
        return result
    }
}

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "book_id") val bookId: Int,
    val chapter: Int,
    val verse: Int,
    @ColumnInfo(name = "note_text") val noteText: String,
    val timestamp: Long
)

@Entity(tableName = "highlights", primaryKeys = ["book_id", "chapter", "verse"])
data class HighlightEntity(
    @ColumnInfo(name = "book_id") val bookId: Int,
    val chapter: Int,
    val verse: Int,
    val color: String
)