package com.ewbible.app.data.database

import android.content.Context
import androidx.room.*

@Database(
    entities = [BookEntity::class, VerseEntity::class, SummaryEntity::class, CommentaryEntity::class, NoteEntity::class, HighlightEntity::class],
    version = 3, exportSchema = false // <-- bumped 2 -> 3
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bibleDao(): BibleDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        // <-- BUMP THIS every time you replace ewbible.db in assets/
        private const val ASSET_DB_VERSION = 3
        private const val PREFS = "db_prefs"
        private const val KEY_ASSET_VERSION = "asset_db_version"

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val appContext = context.applicationContext

                    // --- FIX: Delete old DB if asset DB is newer ---
                    val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    val savedVersion = prefs.getInt(KEY_ASSET_VERSION, -1)
                    if (savedVersion != ASSET_DB_VERSION) {
                        // This deletes the old copied DB so createFromAsset copies the new one
                        appContext.deleteDatabase("ewbible.db")
                        prefs.edit().putInt(KEY_ASSET_VERSION, ASSET_DB_VERSION).apply()
                        INSTANCE = null
                    }

                    val instance = Room.databaseBuilder(
                        appContext,
                        AppDatabase::class.java,
                        "ewbible.db"
                    )
                        .createFromAsset("ewbible.db")
                        .fallbackToDestructiveMigration() // safe now, DB is pre-packaged, we re-copy it
                        .build()
                    INSTANCE = instance
                    instance
                }
            }
        }
    }
}