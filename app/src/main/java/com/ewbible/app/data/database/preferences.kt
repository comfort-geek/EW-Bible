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
        val LAST_MODE = stringPreferencesKey("last_mode") // World English Bible or Commentary
        val LAST_BOOK = stringPreferencesKey("last_book")
        val LAST_CHAPTER = intPreferencesKey("last_chapter")
        val LAST_VERSE = intPreferencesKey("last_verse")
        val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
        val FONT_SIZE = floatPreferencesKey("font_size")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_EMAIL = stringPreferencesKey("user_email")
    }

    // Read - where did we stop last time?
    val lastMode: Flow<String> = context.dataStore.data.map { it[LAST_MODE] ?: "World English Bible" }
    val lastBook: Flow<String> = context.dataStore.data.map { it[LAST_BOOK] ?: "Genesis" }
    val lastChapter: Flow<Int> = context.dataStore.data.map { it[LAST_CHAPTER] ?: 1 }
    val lastVerse: Flow<Int> = context.dataStore.data.map { it[LAST_VERSE] ?: 1 }
    val isDarkMode: Flow<Boolean> = context.dataStore.data.map { it[IS_DARK_MODE] ?: true }
    val fontSize: Flow<Float> = context.dataStore.data.map { it[FONT_SIZE] ?: 16f }
    val userName: Flow<String> = context.dataStore.data.map { it[USER_NAME] ?: "Guest" }

    // Save - call this every time user changes book/chapter/verse
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
}
