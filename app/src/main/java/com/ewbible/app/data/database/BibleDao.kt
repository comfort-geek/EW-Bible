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
    @Query("""
        SELECT v.book_id AS bookId,
               b.name AS bookName,
               v.chapter,
               v.verse,
               v.text
        FROM verses v
        INNER JOIN books b ON b.id = v.book_id
        WHERE v.text LIKE '%' || :query || '%' ESCAPE '\' COLLATE NOCASE
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

    @Query("SELECT * FROM highlights WHERE book_id = :bookId AND chapter = :chapter")
    fun getHighlights(bookId: Int, chapter: Int): Flow<List<HighlightEntity>>

    @Query("SELECT * FROM highlights ORDER BY book_id, chapter, verse")
    fun getAllHighlights(): Flow<List<HighlightEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveHighlight(highlight: HighlightEntity)

    @Query("""
        DELETE FROM highlights
        WHERE book_id = :bookId
          AND chapter = :chapter
          AND verse = :verse
    """)
    suspend fun deleteHighlight(bookId: Int, chapter: Int, verse: Int)

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
}