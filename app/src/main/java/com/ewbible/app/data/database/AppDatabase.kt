package com.ewbible.app.data.database

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// ============ 1. BOOKS ============
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: Int,
    val name: String
)

// ============ 2. VERSES ============
// Matches your ewbible.db: book_id, chapter, verse, text
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

// ============ 3. SUMMARIES ============
@Entity(
    tableName = "summaries",
    primaryKeys = ["book_id", "chapter"]
)
data class SummaryEntity(
    @ColumnInfo(name = "book_id") val bookId: Int,
    val chapter: Int,
    val summary: String
)

// ============ 4. COMMENTARY ============
// Commentary is stored compressed in ewbible.db, so ByteArray
@Entity(
    tableName = "commentary",
    primaryKeys = ["book_id", "chapter"]
)
data class CommentaryEntity(
    @ColumnInfo(name = "book_id") val bookId: Int,
    val chapter: Int,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val content: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CommentaryEntity) return false
        return bookId == other.bookId && chapter == other.chapter && content.contentEquals(other.content)
    }
    override fun hashCode(): Int {
        var result = bookId
        result = 31 * result + chapter
        result = 31 * result + (content?.contentHashCode() ?: 0)
        return result
    }
}

// ============ 5. HIGHLIGHTS ============
@Entity(tableName = "highlights")
data class HighlightEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "book_id") val bookId: Int,
    val chapter: Int,
    val verse: Int,
    val color: String,
    val createdAt: Long = System.currentTimeMillis()
)

// ============ 6. NOTES ============
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "book_id") val bookId: Int,
    val chapter: Int,
    val verse: Int,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// ============ 7. CHATS ============
@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "New Chat",
    val createdAt: Long = System.currentTimeMillis()
)

// ============ 8. MESSAGES ============
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

// ============ DATABASE ============
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
                    .fallbackToDestructiveMigration(false)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}