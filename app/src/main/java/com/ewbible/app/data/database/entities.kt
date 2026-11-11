package com.ewbible.app.data.database
package com.ewbible.app.data.database

import androidx.room.*

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
        } catch (e: Exception) { "" }
    }
}

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val bookId: Int, val chapter: Int, val verse: Int,
    val noteText: String, val timestamp: Long
)

@Entity(tableName = "highlights", primaryKeys = ["book_id", "chapter", "verse"])
data class HighlightEntity(
    @ColumnInfo(name = "book_id") val bookId: Int,
    val chapter: Int, val verse: Int,
    val color: String
)
import androidx.room.Entity
import androidx.room.PrimaryKey

// WORLD ENGLISH BIBLE - This table will come from your DB file next
@Entity(tableName = "verses")
data class Verse(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val book: String = "",          // e.g. Genesis, John
    val bookId: Int = 0,            // 1-66
    val chapter: Int = 0,
    val verse: Int = 0,
    val text: String = "",          // WEB text
    val testament: String = "",     // Old Testament / New Testament
    val category: String = ""       // Gospel, Prophecy, etc. - for Search filter
)

// COMMENTARY - Linked to a verse
@Entity(tableName = "commentary")
data class Commentary(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val book: String = "",
    val chapter: Int = 0,
    val verse: Int = 0,
    val commentaryText: String = ""
)

// NOTES - User notes, will sync after Login
@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val book: String = "",
    val chapter: Int = 0,
    val verse: Int = 0,
    val noteText: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val userEmail: String = "" // Gmail after Login
)

// HIGHLIGHT - Hold verse -> color
@Entity(tableName = "highlights")
data class Highlight(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val book: String = "",
    val chapter: Int = 0,
    val verse: Int = 0,
    val color: String = "", // Yellow, Green, Pink, Cyan, Orange
    val userEmail: String = ""
)

// DAILY VERSE - Linked to WEB, changes daily
@Entity(tableName = "daily_verse")
data class DailyVerse(
    @PrimaryKey val date: String = "", // e.g. 2026-11-11
    val book: String = "",
    val chapter: Int = 0,
    val verse: Int = 0,
    val text: String = ""
)
