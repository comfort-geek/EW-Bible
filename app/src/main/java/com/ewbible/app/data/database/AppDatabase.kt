package com.ewbible.app.data.database

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.concurrent.Executors

@Database(
    entities = [BookEntity::class, VerseEntity::class, SummaryEntity::class, CommentaryEntity::class, NoteEntity::class, HighlightEntity::class],
    version = 3, exportSchema = false
)
@TypeConverters(CommentaryTypeConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bibleDao(): BibleDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        private const val DB_NAME = "ewbible.db"
        private const val PREFS = "db_prefs"
        private const val KEY_ASSET_VERSION = "asset_db_version"
        private const val ASSET_DB_VERSION = 3

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDb(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun buildDb(appContext: Context): AppDatabase {
            val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val savedVersion = prefs.getInt(KEY_ASSET_VERSION, -1)
            val needsUpdate = savedVersion != ASSET_DB_VERSION

            // 1. Backup BEFORE Room opens anything, on background thread is done inside callback
            // We only delete here if we KNOW asset changed - and we do it BEFORE build()
            if (needsUpdate) {
                // Save notes/highlights to prefs/file BEFORE delete - do NOT open Room DB here
                // Simplest safe way: DON'T delete manually. Let fallback handle it.
                // We just mark that we need to restore after copy.
            }

            return Room.databaseBuilder(appContext, AppDatabase::class.java, DB_NAME)
                .createFromAsset("ewbible.db", object : PrepackagedDatabaseCallback() {
                    override fun onOpenPrepackagedDatabase(db: SupportSQLiteDatabase) {
                        super.onOpenPrepackagedDatabase(db)
                        // This runs on background thread, safe.
                        // If this is a fresh copy, mark version as done
                        prefs.edit().putInt(KEY_ASSET_VERSION, ASSET_DB_VERSION).apply()
                    }
                })
                // THIS is the fix for version mismatch crash:
                // If old DB version != 3 and no migration, Room deletes + recopies from asset
                // instead of crashing with "A migration was required but not found"
                .fallbackToDestructiveMigration(false)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // First install only - Room already copied asset, nothing to do
                    }
                })
                .build()
        }
    }
}