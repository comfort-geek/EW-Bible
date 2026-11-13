package com.ewbible.app.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: Int,
    val key: String,
    val name: String,
    val testament: String
)

@Entity(tableName = "verses", primaryKeys = ["book_id", "chapter", "verse"])
data class VerseEntity(
    @ColumnInfo(name = "book_id") val bookId: Int,
    val chapter: Int,
    val verse: Int,
    val text: String
)

@Entity(tableName = "summaries", primaryKeys = ["book_id", "chapter"])
data class SummaryEntity(
    @ColumnInfo(name = "book_id") val bookId: Int,
    val chapter: Int,
    val summary: String,
    @ColumnInfo(name = "what_happened") val whatHappened: String,
    val application: String
)

@Entity(tableName = "commentary", primaryKeys = ["book_id", "chapter"])
data class CommentaryEntity(
    @ColumnInfo(name = "book_id") val bookId: Int,
    val chapter: Int,
    @ColumnInfo(name = "html_compressed") val htmlCompressed: ByteArray
) {
    fun getHtml(): String {
        return try {
            val inflater = java.util.zip.InflaterInputStream(htmlCompressed.inputStream())
            inflater.readBytes().toString(Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }
}

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "book_id") val bookId: Int,
    val chapter: Int,
    val verse: Int,
    @ColumnInfo(name = "note_text") val noteText: String,
    val timestamp: Long
)

@Entity(tableName = "highlights", primaryKeys = ["book_id", "chapter", "verse"])
data class HighlightEntity(
    @ColumnInfo(name = "book_id") val bookId: Int,
    val chapter: Int,
    val verse: Int,
    val color: String
)







entities..
package com.ewbible.app.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: Int,
    val key: String,
    val name: String,
    val testament: String
)

@Entity(tableName = "verses", primaryKeys = ["book_id", "chapter", "verse"])
data class VerseEntity(
    @ColumnInfo(name = "book_id") val bookId: Int,
    val chapter: Int,
    val verse: Int,
    val text: String
)

@Entity(tableName = "summaries", primaryKeys = ["book_id", "chapter"])
data class SummaryEntity(
    @ColumnInfo(name = "book_id") val bookId: Int,
    val chapter: Int,
    val summary: String,
    @ColumnInfo(name = "what_happened") val whatHappened: String,
    val application: String
)

@Entity(tableName = "commentary", primaryKeys = ["book_id", "chapter"])
data class CommentaryEntity(
    @ColumnInfo(name = "book_id") val bookId: Int,
    val chapter: Int,
    @ColumnInfo(name = "html_compressed") val htmlCompressed: ByteArray
) {
    fun getHtml(): String {
        return try {
            val inflater = java.util.zip.InflaterInputStream(htmlCompressed.inputStream())
            inflater.readBytes().toString(Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }
}

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "book_id") val bookId: Int,
    val chapter: Int,
    val verse: Int,
    @ColumnInfo(name = "note_text") val noteText: String,
    val timestamp: Long
)

@Entity(tableName = "highlights", primaryKeys = ["book_id", "chapter", "verse"])
data class HighlightEntity(
    @ColumnInfo(name = "book_id") val bookId: Int,
    val chapter: Int,
    val verse: Int,
    val color: String
)




appdatabase..
package com.ewbible.app.data.database

import android.content.Context
import androidx.room.*

@Database(
    entities = [BookEntity::class, VerseEntity::class, SummaryEntity::class, CommentaryEntity::class, NoteEntity::class, HighlightEntity::class],
    version = 1, exportSchema = false
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
                ).createFromAsset("ewbible.db").build()
                INSTANCE = instance
                instance
            }
        }
    }
}

preferences..
package com.ewbible.app.data.database

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ewbible_settings")

class AppPreferences(private val context: Context) {
    companion object {
        val LAST_MODE = stringPreferencesKey("last_mode")
        val LAST_BOOK = stringPreferencesKey("last_book")
        val LAST_CHAPTER = intPreferencesKey("last_chapter")
        val LAST_VERSE = intPreferencesKey("last_verse")
        val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
        val FONT_SIZE = floatPreferencesKey("font_size")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val LAST_SCROLL_INDEX = intPreferencesKey("last_scroll_index")
        val LAST_SCROLL_OFFSET = intPreferencesKey("last_scroll_offset")
    }

    val lastMode: Flow<String> = context.dataStore.data.map { it[LAST_MODE] ?: "World English Bible" }
    val lastBook: Flow<String> = context.dataStore.data.map { it[LAST_BOOK] ?: "Genesis" }
    val lastChapter: Flow<Int> = context.dataStore.data.map { it[LAST_CHAPTER] ?: 1 }
    val lastVerse: Flow<Int> = context.dataStore.data.map { it[LAST_VERSE] ?: 1 }
    val isDarkMode: Flow<Boolean> = context.dataStore.data.map { it[IS_DARK_MODE] ?: true }
    val fontSize: Flow<Float> = context.dataStore.data.map { it[FONT_SIZE] ?: 16f }
    val userName: Flow<String> = context.dataStore.data.map { it[USER_NAME] ?: "Guest" }
    val userEmail: Flow<String> = context.dataStore.data.map { it[USER_EMAIL] ?: "" }
    val lastScrollIndex: Flow<Int> = context.dataStore.data.map { it[LAST_SCROLL_INDEX] ?: 0 }
    val lastScrollOffset: Flow<Int> = context.dataStore.data.map { it[LAST_SCROLL_OFFSET] ?: 0 }

    suspend fun saveReading(mode: String, book: String, chapter: Int, verse: Int) {
        context.dataStore.edit { prefs ->
            prefs[LAST_MODE] = mode
            prefs[LAST_BOOK] = book
            prefs[LAST_CHAPTER] = chapter
            prefs[LAST_VERSE] = verse
        }
    }
    suspend fun saveSettings(darkMode: Boolean, fontSize: Float) {
        context.dataStore.edit { prefs ->
            prefs[IS_DARK_MODE] = darkMode
            prefs[FONT_SIZE] = fontSize
        }
    }
    suspend fun saveUser(name: String, email: String) {
        context.dataStore.edit { prefs ->
            prefs[USER_NAME] = name
            prefs[USER_EMAIL] = email
        }
    }
    suspend fun saveScroll(index: Int, offset: Int) {
        context.dataStore.edit { prefs ->
            prefs[LAST_SCROLL_INDEX] = index
            prefs[LAST_SCROLL_OFFSET] = offset
        }
    }
    suspend fun clearUser() {
        context.dataStore.edit { prefs ->
            prefs[USER_NAME] = "Guest"
            prefs[USER_EMAIL] = ""
        }
    }
}

