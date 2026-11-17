package com.ewbible.app.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val key: String,
    val testament: String = "OT"
)

@Entity(tableName = "verses", primaryKeys = ["bookId", "chapter", "verse"])
data class VerseEntity(
    @ColumnInfo(name = "book_id") val bookId: Int,
    val chapter: Int,
    val verse: Int,
    val text: String
)

@Entity(tableName = "summaries", primaryKeys = ["bookId", "chapter"])
data class SummaryEntity(
    @ColumnInfo(name = "book_id") val bookId: Int,
    val chapter: Int,
    val summary: String,
    @ColumnInfo(name = "what_happened") val whatHappened: String,
    val application: String
)

@Entity(tableName = "commentary", primaryKeys = ["bookId", "chapter"])
data class CommentaryEntity(
    @ColumnInfo(name = "book_id") val bookId: Int,
    val chapter: Int,
    @ColumnInfo(name = "html_compressed") val htmlCompressed: ByteArray? = null
) {
    // Equals/hashCode for ByteArray - Room needs this or it warns
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CommentaryEntity) return false
        return bookId == other.bookId && chapter == other.chapter &&
            (htmlCompressed?.contentEquals(other.htmlCompressed) ?: (other.htmlCompressed == null))
    }
    override fun hashCode(): Int {
        var r = bookId
        r = 31 * r + chapter
        r = 31 * r + (htmlCompressed?.contentHashCode() ?: 0)
        return r
    }
}

@Entity(tableName = "notes", primaryKeys = ["bookId", "chapter", "verse"])
data class NoteEntity(
    @ColumnInfo(name = "book_id") val bookId: Int,
    val chapter: Int,
    val verse: Int,
    @ColumnInfo(name = "note_text") val noteText: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "highlights", primaryKeys = ["bookId", "chapter", "verse"])
data class HighlightEntity(
    @ColumnInfo(name = "book_id") val bookId: Int,
    val chapter: Int,
    val verse: Int,
    val color: String
)