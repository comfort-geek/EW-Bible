package com.ewbible.app.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BibleDao {
    // --- World English Bible ---
    @Query("SELECT DISTINCT book FROM verses ORDER BY bookId ASC")
    fun getBooks(): Flow<List<String>>

    @Query("SELECT DISTINCT chapter FROM verses WHERE book = :book ORDER BY chapter ASC")
    fun getChapters(book: String): Flow<List<Int>>

    @Query("SELECT * FROM verses WHERE book = :book AND chapter = :chapter ORDER BY verse ASC")
    fun getVerses(book: String, chapter: Int): Flow<List<Verse>>

    // --- Search - ONLY WEB - with filters All, Gospel, Prophecy, Old, New ---
    @Query("SELECT * FROM verses WHERE text LIKE '%' || :query || '%' AND (:filter = 'All' OR category = :filter OR testament = :filter) ORDER BY bookId, chapter, verse")
    fun searchVerses(query: String, filter: String): Flow<List<Verse>>

    // --- Commentary ---
    @Query("SELECT * FROM commentary WHERE book = :book AND chapter = :chapter AND verse = :verse")
    fun getCommentary(book: String, chapter: Int, verse: Int): Flow<Commentary?>

    // --- Notes ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveNote(note: Note)

    @Query("SELECT * FROM notes WHERE book = :book AND chapter = :chapter AND verse = :verse")
    fun getNotes(book: String, chapter: Int, verse: Int): Flow<List<Note>>

    // --- Highlights ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveHighlight(highlight: Highlight)

    @Query("SELECT * FROM highlights WHERE book = :book AND chapter = :chapter")
    fun getHighlights(book: String, chapter: Int): Flow<List<Highlight>>

    // --- Daily Verse ---
    @Query("SELECT * FROM daily_verse WHERE date = :date")
    suspend fun getDailyVerse(date: String): DailyVerse?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDailyVerse(dailyVerse: DailyVerse)
}
