package com.ewbible.app.data.database

import android.content.Context
import androidx.room.*

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: Int,
    val key: String = "",
    val name: String,
    val testament: String = ""
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
    @ColumnInfo(name = "what_happened") val whatHappened: String? = null,
    val application: String? = null
)

@Entity(
    tableName = "commentary",
    primaryKeys = ["book_id", "chapter"]
)
data class CommentaryEntity(
    @ColumnInfo(name = "book_id") val bookId: Int,
    val chapter: Int,
    @ColumnInfo(name = "html_compressed", typeAffinity = ColumnInfo.BLOB) val htmlCompressed: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CommentaryEntity) return false
        return bookId == other.bookId && chapter == other.chapter && htmlCompressed.contentEquals(other.htmlCompressed)
    }
    override fun hashCode(): Int {
        var result = bookId
        result = 31 * result + chapter
        result = 31 * result + (htmlCompressed?.contentHashCode() ?: 0)
        return result
    }
}

@Entity(
    tableName = "highlights",
    primaryKeys = ["book_id", "chapter", "verse"]
)
data class HighlightEntity(
    @ColumnInfo(name = "book_id") val bookId: Int,
    val chapter: Int,
    val verse: Int,
    val color: Int
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "book_id") val bookId: Int,
    val chapter: Int,
    val verse: Int,
    @ColumnInfo(name = "note_text") val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "New Chat",
    val ref: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("chatId")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chatId: Long,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@Database(
    entities = [
        BookEntity::class,
        VerseEntity::class,
        SummaryEntity::class,
        CommentaryEntity::class,
        HighlightEntity::class,
        NoteEntity::class,
        ChatEntity::class,
        MessageEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bibleDao(): BibleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ewbible.db"
                )
                    .createFromAsset("ewbible.db")
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}