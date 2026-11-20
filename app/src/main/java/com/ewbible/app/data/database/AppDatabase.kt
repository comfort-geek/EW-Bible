package com.ewbible.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        BookEntity::class,
        VerseEntity::class,
        SummaryEntity::class,
        CommentaryEntity::class,
        HighlightEntity::class,
        NoteEntity::class,
        ChatEntity::class,
        MessageEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun bibleDao(): BibleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ewbible.db"
                )
                    .createFromAsset("ewbible.db")
                    // REMOVED: .fallbackToDestructiveMigration()
                    // That was deleting all verses/commentary on version mismatch
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}