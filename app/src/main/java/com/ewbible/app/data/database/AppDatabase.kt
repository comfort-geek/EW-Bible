package com.ewbible.app.data.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

// ==================== BIBLE TABLES - FROM ewbible.db ====================
// These MUST match your ewbible.db column names.
// If build fails, tell me the error - it's just a column name mismatch.

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: Int = 0,
    val name: String = "",
    val testament: String = "",
    val chapters: Int = 0
)

@Entity(tableName = "verses")
data class VerseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Int = 0,
    val chapter: Int = 0,
    val verse: Int = 0,
    val text: String = ""
)

// ==================== APP TABLES - created by Room ====================

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

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Int = 0,
    val chapter: Int = 0,
    val verse: Int = 0,
    val ref: String = "",
    val text: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "highlights")
data class HighlightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Int = 0,
    val chapter: Int = 0,
    val verse: Int = 0,
    val ref: String = "",
    val color: Int = 0
)

// ==================== DAO - WHOLE APP ====================

@Dao
interface BibleDao {

    // --- Bible reading ---
    @Query("SELECT * FROM books ORDER BY id ASC")
    suspend fun getAllBooks(): List<BookEntity>

    @Query("SELECT * FROM verses WHERE bookId = :bookId AND chapter = :chapter ORDER BY verse ASC")
    suspend fun getChapterVerses(bookId: Int, chapter: Int): List<VerseEntity>

    @Query("SELECT text FROM verses WHERE bookId = :bookId AND chapter = :chapter AND verse = :verse LIMIT 1")
    suspend fun getVerse(bookId: Int, chapter: Int, verse: Int): String?

    // --- For Chat / Gemini ---
    @Query("SELECT verse || ' ' || text FROM verses WHERE bookId = :bookId AND chapter = :chapter ORDER BY verse ASC")
    suspend fun getChapterText(bookId: Int, chapter: Int): List<String>

    @Query("SELECT text FROM summaries WHERE bookId = :bookId AND chapter = :chapter LIMIT 1")
    suspend fun getSummary(bookId: Int, chapter: Int): String?

    @Query("SELECT text FROM commentary WHERE bookId = :bookId AND chapter = :chapter LIMIT 1")
    suspend fun getCommentary(bookId: Int, chapter: Int): String?

    // --- Chat ---
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

    // --- Bookmarks / Notes / Highlights ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(b: BookmarkEntity): Long

    @Query("SELECT * FROM bookmarks ORDER BY id DESC")
    suspend fun getBookmarks(): List<BookmarkEntity>

    @Query("DELETE FROM bookmarks WHERE bookId = :bookId AND chapter = :chapter AND verse = :verse")
    suspend fun deleteBookmark(bookId: Int, chapter: Int, verse: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(n: NoteEntity): Long

    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    suspend fun getNotes(): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHighlight(h: HighlightEntity): Long

    @Query("SELECT * FROM highlights")
    suspend fun getHighlights(): List<HighlightEntity>

    @Query("DELETE FROM highlights WHERE bookId = :bookId AND chapter = :chapter AND verse = :verse")
    suspend fun deleteHighlight(bookId: Int, chapter: Int, verse: Int)
}

// ==================== DATABASE ====================

@Database(
    entities = [
        BookEntity::class,
        VerseEntity::class,
        ChatEntity::class,
        MessageEntity::class,
        BookmarkEntity::class,
        NoteEntity::class,
        HighlightEntity::class
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
                .createFromAsset("ewbible.db") // <-- loads your file from assets/
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}