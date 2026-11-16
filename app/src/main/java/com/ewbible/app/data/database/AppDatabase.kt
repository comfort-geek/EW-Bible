package com.ewbible.app.data.database

import android.content.Context
import androidx.room.*

@Database(
    entities = [BookEntity::class, VerseEntity::class, SummaryEntity::class, CommentaryEntity::class, NoteEntity::class, HighlightEntity::class],
    version = 2, exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bibleDao(): BibleDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val instance = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "ewbible.db"
                    )
                        .createFromAsset("ewbible.db")
                        // FIXED: Removed .fallbackToDestructiveMigration()
                        // It was deleting your pre-populated DB with commentary
                        // and replacing it with empty tables = blank EW tab
                        .build()
                    INSTANCE = instance
                    instance
                }
            }
        }
    }
}