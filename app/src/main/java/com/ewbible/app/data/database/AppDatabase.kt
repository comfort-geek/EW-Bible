package com.ewbible.app.data.database

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
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

// FIXED: html_compressed is BLOB, not String
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
    @PrimaryKey @ColumnInfo(name = "id") val id: Long = 0,
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

@Dao
interface BibleDao {
    @Query("SELECT * FROM books ORDER BY id ASC")
    suspend fun getAllBooks(): List<BookEntity>

    @Query("SELECT MAX(chapter) FROM verses WHERE book_id = :bookId")
    suspend fun getChapterCount(bookId: Int): Int?

    @Query("SELECT * FROM verses WHERE book_id = :bookId AND chapter = :chapter ORDER BY verse ASC")
    suspend fun getChapterVerses(bookId: Int, chapter: Int): List<VerseEntity>

    @Query("SELECT text FROM verses WHERE book_id = :bookId AND chapter = :chapter AND verse = :verse LIMIT 1")
    suspend fun getVerse(bookId: Int, chapter: Int, verse: Int): String?

    @Query("SELECT verse || ' ' || text FROM verses WHERE book_id = :bookId AND chapter = :chapter ORDER BY verse ASC")
    suspend fun getChapterText(bookId: Int, chapter: Int): List<String>

    @Query("SELECT summary || '\n\nWhat happened: ' || what_happened || '\n\nApplication: ' || application FROM summaries WHERE book_id = :bookId AND chapter = :chapter LIMIT 1")
    suspend fun getSummary(bookId: Int, chapter: Int): String?

    // FIXED: returns ByteArray, not String
    @Query("SELECT html_compressed FROM commentary WHERE book_id = :bookId AND chapter = :chapter LIMIT 1")
    suspend fun getCommentaryCompressed(bookId: Int, chapter: Int): ByteArray?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatEntity): Long
    @Query("SELECT * FROM chats ORDER BY createdAt DESC")
    suspend fun getAllChats(): List<ChatEntity>
    @Query("DELETE FROM chats WHERE id = :chatId")
    suspend fun deleteChat(chatId: Long)
    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteMessagesForChat(chatId: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(msg: MessageEntity): Long
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    suspend fun getMessages(chatId: Long): List<MessageEntity>
    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId AND isUser = 1 AND timestamp > :startOfDay")
    suspend fun getDailyCount(chatId: Long, startOfDay: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(b: BookmarkEntity): Long
    @Query("SELECT * FROM bookmarks ORDER BY id DESC")
    suspend fun getBookmarks(): List<BookmarkEntity>
    @Query("DELETE FROM bookmarks WHERE bookId = :bookId AND chapter = :chapter AND verse = :verse")
    suspend fun deleteBookmark(bookId: Int, chapter: Int, verse: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(n: NoteEntity): Long
    @Query("SELECT * FROM notes ORDER BY timestamp DESC")
    suspend fun getNotes(): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHighlight(h: HighlightEntity): Long
    @Query("SELECT * FROM highlights")
    suspend fun getHighlights(): List<HighlightEntity>
    @Query("DELETE FROM highlights WHERE book_id = :bookId AND chapter = :chapter AND verse = :verse")
    suspend fun deleteHighlight(bookId: Int, chapter: Int, verse: Int)
}

@Database(
    entities = [
        BookEntity::class, VerseEntity::class, SummaryEntity::class,
        CommentaryEntity::class, NoteEntity::class, HighlightEntity::class,
        ChatEntity::class, MessageEntity::class, BookmarkEntity::class
    ],
    version = 1,
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