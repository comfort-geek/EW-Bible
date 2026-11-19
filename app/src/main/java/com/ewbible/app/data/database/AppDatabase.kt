package com.ewbible.app.data.database

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: Int = 0,
    @ColumnInfo(name = "key") val key: String = "",
    @ColumnInfo(name = "name") val name: String = "",
    @ColumnInfo(name = "testament") val testament: String = ""
)

@Entity(
    tableName = "verses",
    primaryKeys = ["book_id", "chapter", "verse"]
)
data class VerseEntity(
    @ColumnInfo(name = "book_id") val bookId: Int = 0,
    @ColumnInfo(name = "chapter") val chapter: Int = 0,
    @ColumnInfo(name = "verse") val verse: Int = 0,
    @ColumnInfo(name = "text") val text: String = ""
)

@Entity(
    tableName = "summaries",
    primaryKeys = ["book_id", "chapter"]
)
data class SummaryEntity(
    @ColumnInfo(name = "book_id") val bookId: Int = 0,
    @ColumnInfo(name = "chapter") val chapter: Int = 0,
    @ColumnInfo(name = "summary") val summary: String = "",
    @ColumnInfo(name = "what_happened") val whatHappened: String = "",
    @ColumnInfo(name = "application") val application: String = ""
)

@Entity(
    tableName = "commentary",
    primaryKeys = ["book_id", "chapter"]
)
data class CommentaryEntity(
    @ColumnInfo(name = "book_id") val bookId: Int = 0,
    @ColumnInfo(name = "chapter") val chapter: Int = 0,
    @ColumnInfo(name = "html_compressed", typeAffinity = ColumnInfo.BLOB)
    val htmlCompressed: ByteArray = byteArrayOf()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CommentaryEntity) return false
        return bookId == other.bookId && chapter == other.chapter
    }
    override fun hashCode(): Int = 31 * bookId + chapter
}

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "book_id") val bookId: Int = 0,
    @ColumnInfo(name = "chapter") val chapter: Int = 0,
    @ColumnInfo(name = "verse") val verse: Int = 0,
    @ColumnInfo(name = "note_text") val text: String = "",
    @ColumnInfo(name = "timestamp") val timestamp: Long = 0
)

@Entity(
    tableName = "highlights",
    primaryKeys = ["book_id", "chapter", "verse"]
)
data class HighlightEntity(
    @ColumnInfo(name = "book_id") val bookId: Int = 0,
    @ColumnInfo(name = "chapter") val chapter: Int = 0,
    @ColumnInfo(name = "verse") val verse: Int = 0,
    @ColumnInfo(name = "color") val color: Int = 0
)

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val ref: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chatId: Long = 0,
    val text: String = "",
    val isUser: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Int = 0,
    val chapter: Int = 0,
    val verse: Int = 0,
    val ref: String = ""
)

@Database(
    entities = [
        BookEntity::class, VerseEntity::class, SummaryEntity::class,
        CommentaryEntity::class, NoteEntity::class, HighlightEntity::class,
        ChatEntity::class, MessageEntity::class, BookmarkEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bibleDao(): BibleDao
    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ewbible.db"
                )
                .createFromAsset("ewbible.db")
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}