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
        val DAILY_VERSE_DATE = stringPreferencesKey("daily_verse_date")
        val DAILY_VERSE_BOOK_ID = intPreferencesKey("daily_verse_book_id")
        val DAILY_VERSE_CHAPTER = intPreferencesKey("daily_verse_chapter")
        val DAILY_VERSE_NUMBER = intPreferencesKey("daily_verse_number")
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
    val dailyVerseDate: Flow<String> = context.dataStore.data.map { it[DAILY_VERSE_DATE] ?: "" }
    val dailyVerseReference: Flow<DailyVerseReference?> = context.dataStore.data.map { preferences ->
        val bookId = preferences[DAILY_VERSE_BOOK_ID]
        val chapter = preferences[DAILY_VERSE_CHAPTER]
        val verse = preferences[DAILY_VERSE_NUMBER]
        if (bookId != null && chapter != null && verse != null) DailyVerseReference(bookId, chapter, verse) else null
    }

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
            prefs[LAST_SCROLL_INDEX] = index.coerceAtLeast(0)
            prefs[LAST_SCROLL_OFFSET] = offset.coerceAtLeast(0)
        }
    }

    suspend fun saveDailyVerse(date: String, reference: DailyVerseReference) {
        context.dataStore.edit { prefs ->
            prefs[DAILY_VERSE_DATE] = date
            prefs[DAILY_VERSE_BOOK_ID] = reference.bookId
            prefs[DAILY_VERSE_CHAPTER] = reference.chapter
            prefs[DAILY_VERSE_NUMBER] = reference.verse
        }
    }

    suspend fun clearUser() {
        context.dataStore.edit { prefs ->
            prefs[USER_NAME] = "Guest"
            prefs[USER_EMAIL] = ""
        }
    }
}

data class DailyVerseReference(val bookId: Int, val chapter: Int, val verse: Int)
