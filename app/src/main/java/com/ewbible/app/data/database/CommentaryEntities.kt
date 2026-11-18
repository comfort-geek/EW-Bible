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

@Entity(
    tableName = "verses",
    primaryKeys = ["book_id", "chapter", "verse"]
)
data class VerseEntity(
    @ColumnInfo(name = "book_id") val bookId: Int,
    val chapter: Int,
    val verse: Int,
    val text: String
)

@Entity(
    tableName = "summaries",
    primaryKeys = ["book_id", "chapter"]
)
data class SummaryEntity(
    @ColumnInfo(name = "book_id") val bookId: Int,
    val chapter: Int,
    val summary: String,
    @ColumnInfo(name = "what_happened") val whatHappened: String,
    val application: String
)

@Entity(
    tableName = "commentary",
    primaryKeys = ["book_id", "chapter"]
)
data class CommentaryEntity(
    @ColumnInfo(name = "book_id") val bookId: Int,
    val chapter: Int,
    @ColumnInfo(name = "html_compressed") val htmlCompressed: ByteArray
) {
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

// NO @Index here on purpose - adding one = schema change = needs version 4
// notes table is small (user notes only), so no index needed on v3
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "book_id") val bookId: Int,
    val chapter: Int,
    val verse: Int,
    @ColumnInfo(name = "note_text") val noteText: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "highlights",
    primaryKeys = ["book_id", "chapter", "verse"]
)
data class HighlightEntity(
    @ColumnInfo(name = "book_id") val bookId: Int,
    val chapter: Int,
    val verse: Int,
    val color: String
)