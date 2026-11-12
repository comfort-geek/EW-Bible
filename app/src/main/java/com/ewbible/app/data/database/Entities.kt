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
        return try {
            val inflater = java.util.zip.InflaterInputStream(htmlCompressed.inputStream())
            inflater.readBytes().toString(Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
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
