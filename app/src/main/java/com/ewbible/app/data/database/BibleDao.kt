package com.ewbible.app.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class BookChapterCount(
    val bookId: Int,
    val chapterCount: Int
)

data class VerseSearchResult(
    val bookId: Int,
    val bookName: String,
    val chapter: Int,
    val verse: Int,
    val text: String
)

@Dao
interface BibleDao {

    // --- BOOKS / VERSES ---

    @Query("SELECT * FROM books ORDER BY id")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books ORDER BY id")
    suspend fun getAllBooksSync(): List<BookEntity>

    @Query("""
        SELECT v.book_id AS bookId,
               b.name AS bookName,
               v.chapter,
               v.verse,
               v.text
        FROM verses v
        INNER JOIN books b ON b.id = v.book_id
        ORDER BY RANDOM()
        LIMIT 1
    """)
    suspend fun getRandomVerse(): VerseSearchResult?

    @Query("SELECT COALESCE(MAX(chapter), 1) FROM verses WHERE book_id = :bookId")
    suspend fun getMaxChapter(bookId: Int): Int

    @Query("""
        SELECT book_id AS bookId, COALESCE(MAX(chapter), 0) AS chapterCount
        FROM verses
        GROUP BY book_id
    """)
    suspend fun getChapterCounts(): List<BookChapterCount>

    @Query("SELECT * FROM verses WHERE book_id = :bookId AND chapter = :chapter ORDER BY verse")
    fun getVerses(bookId: Int, chapter: Int): Flow<List<VerseEntity>>

    // FIX: Added ESCAPE '\' - caller must escape % _ \ before passing query
    // e.g. query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    // FIX: COLLATE on column, not after ESCAPE - always compiles in Room/SQLite
    @Query("""
        SELECT v.book_id AS bookId,
               b.name AS bookName,
               v.chapter,
               v.verse,
               v.text
        FROM verses v
        INNER JOIN books b ON b.id = v.book_id
        WHERE v.text COLLATE NOCASE LIKE '%' || :query || '%' ESCAPE '\'
        LIMIT 100
    """)
    suspend fun searchVerses(query: String): List<VerseSearchResult>

    @Query("SELECT * FROM summaries WHERE book_id = :bookId AND chapter = :chapter")
    suspend fun getSummary(bookId: Int, chapter: Int): SummaryEntity?

    @Query("SELECT html_compressed FROM commentary WHERE book_id = :bookId AND chapter = :chapter")
    suspend fun getCommentaryCompressed(bookId: Int, chapter: Int): ByteArray?

    // FIX: New - for linked verse sheet, don't load whole chapter
    @Query("SELECT * FROM verses WHERE book_id = :bookId AND chapter = :chapter AND verse = :verse LIMIT 1")
    suspend fun getVerse(bookId: Int, chapter: Int, verse: Int): VerseEntity?

    // PATCH: New - for linked verse RANGE (e.g. John 3:16-18).
    // Before, ViewModel loaded the whole chapter with getVerses().first() then filtered.
    // Now it loads only the 1-5 verses it needs.
    @Query("""
        SELECT * FROM verses
        WHERE book_id = :bookId
          AND chapter = :chapter
          AND verse BETWEEN :startVerse AND :endVerse
        ORDER BY verse
    """)
    suspend fun getVerseRange(
        bookId: Int,
        chapter: Int,
        startVerse: Int,
        endVerse: Int
    ): List<VerseEntity>

    // --- HIGHLIGHTS ---

    @Query("SELECT * FROM highlights WHERE book_id = :bookId AND chapter = :chapter")
    fun getHighlights(bookId: Int, chapter: Int): Flow<List<HighlightEntity>>

    @Query("SELECT * FROM highlights ORDER BY book_id, chapter, verse")
    fun getAllHighlights(): Flow<List<HighlightEntity>>

    @Query("SELECT * FROM highlights ORDER BY book_id, chapter, verse")
    suspend fun getAllHighlightsSync(): List<HighlightEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveHighlight(highlight: HighlightEntity)

    @Query("""
        DELETE FROM highlights
        WHERE book_id = :bookId
          AND chapter = :chapter
          AND verse = :verse
    """)
    suspend fun deleteHighlight(bookId: Int, chapter: Int, verse: Int)

    // --- NOTES ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveNote(note: NoteEntity)

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Query("""
        SELECT * FROM notes
        WHERE book_id = :bookId
          AND chapter = :chapter
          AND verse = :verse
        LIMIT 1
    """)
    suspend fun getNoteAtVerse(
        bookId: Int,
        chapter: Int,
        verse: Int
    ): NoteEntity?

    @Query("SELECT * FROM notes WHERE book_id = :bookId AND chapter = :chapter AND verse = :verse")
    fun getNotes(bookId: Int, chapter: Int, verse: Int): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY timestamp DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY timestamp DESC")
    suspend fun getAllNotesSync(): List<NoteEntity>

    @Query("DELETE FROM notes WHERE id = :noteId")
    suspend fun deleteNote(noteId: Int)

    @Query("""
        DELETE FROM notes
        WHERE book_id = :bookId
          AND chapter = :chapter
          AND verse = :verse
    """)
    suspend fun deleteAllNotesForVerse(bookId: Int, chapter: Int, verse: Int)

    // --- CHAT - FIXED: was missing, AppDatabase has chats/messages tables but no DAO methods ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatEntity): Long

    @Query("SELECT * FROM chats ORDER BY createdAt DESC")
    fun getAllChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats ORDER BY createdAt DESC")
    suspend fun getAllChatsSync(): List<ChatEntity>

    @Query("SELECT * FROM chats WHERE id = :chatId LIMIT 1")
    suspend fun getChatById(chatId: Long): ChatEntity?

    @Update
    suspend fun updateChat(chat: ChatEntity)

    @Query("DELETE FROM chats WHERE id = :chatId")
    suspend fun deleteChat(chatId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesForChat(chatId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    suspend fun getMessagesForChatSync(chatId: Long): List<MessageEntity>

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteMessagesForChat(chatId: Long)

    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId AND isUser = 1 AND date(timestamp / 1000, 'unixepoch') = date('now')")
    suspend fun getTodayUserMessageCount(chatId: Long): Int

    // NEW: Global daily count - 1 query, no N+1 sum across chats.
    // timestamp is in ms (System.currentTimeMillis()), so timestamp / 1000 = unix seconds.
    @Query("SELECT COUNT(*) FROM messages WHERE isUser = 1 AND date(timestamp / 1000, 'unixepoch') = date('now')")
    suspend fun getTodayUserMessageCountGlobal(): Int
}