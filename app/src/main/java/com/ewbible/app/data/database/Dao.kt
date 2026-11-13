package com.ewbible.app.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BibleDao {
    @Query("SELECT * FROM books ORDER BY id")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT COALESCE(MAX(chapter), 1) FROM verses WHERE book_id = :bookId")
    fun getMaxChapter(bookId: Int): Flow<Int>

    @Query("SELECT * FROM verses WHERE book_id = :bookId AND chapter = :chapter ORDER BY verse")
    fun getVerses(bookId: Int, chapter: Int): Flow<List<VerseEntity>>

    @Query("SELECT * FROM summaries WHERE book_id = :bookId AND chapter = :chapter")
    suspend fun getSummary(bookId: Int, chapter: Int): SummaryEntity?

    @Query("SELECT * FROM commentary WHERE book_id = :bookId AND chapter = :chapter")
    suspend fun getCommentary(bookId: Int, chapter: Int): CommentaryEntity?

    @Query("SELECT * FROM highlights WHERE book_id = :bookId AND chapter = :chapter")
    fun getHighlights(bookId: Int, chapter: Int): Flow<List<HighlightEntity>>

    @Query("SELECT * FROM highlights ORDER BY book_id, chapter, verse")
    fun getAllHighlights(): Flow<List<HighlightEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveHighlight(h: HighlightEntity)

    @Query("DELETE FROM highlights WHERE book_id = :bookId AND chapter = :chapter AND verse = :verse")
    suspend fun deleteHighlight(bookId: Int, chapter: Int, verse: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveNote(n: NoteEntity)

    @Query("SELECT * FROM notes WHERE book_id = :bookId AND chapter = :chapter AND verse = :verse")
    fun getNotes(bookId: Int, chapter: Int, verse: Int): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY timestamp DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>
}
