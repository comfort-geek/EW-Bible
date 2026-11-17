package com.ewbible.app.data.database

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
    val bookId: Int,
    val chapter: Int,
    val verse: Int,
    val text: String
)

@Entity(tableName = "summaries", primaryKeys = ["bookId", "chapter"])
data class SummaryEntity(
    val bookId: Int,
    val chapter: Int,
    val summary: String,
    val whatHappened: String,
    val application: String
)

@Entity(tableName = "commentary", primaryKeys = ["bookId", "chapter"])
data class CommentaryEntity(
    val bookId: Int,
    val chapter: Int,
    val html: String = "",
    val text: String = ""
) {
    fun getHtml(): String = html.ifBlank { text }
}

@Entity(tableName = "notes", primaryKeys = ["bookId", "chapter", "verse"])
data class NoteEntity(
    val bookId: Int,
    val chapter: Int,
    val verse: Int,
    val noteText: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "highlights", primaryKeys = ["bookId", "chapter", "verse"])
data class HighlightEntity(
    val bookId: Int,
    val chapter: Int,
    val verse: Int,
    val color: String
)

data class VerseSearchResult(
    val bookId: Int,
    val bookName: String,
    val chapter: Int,
    val verse: Int,
    val text: String
)

data class ChapterCountResult(
    val bookId: Int,
    val chapterCount: Int
)