package com.ewbible.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Verse::class, Commentary::class, Note::class, Highlight::class, DailyVerse::class],
    version = 1,
    exportSchema = false
)
abstract class EWBibleDatabase : RoomDatabase() {
    abstract fun bibleDao(): BibleDao

    companion object {
        @Volatile private var INSTANCE: EWBibleDatabase? = null

        fun getInstance(context: Context): EWBibleDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    EWBibleDatabase::class.java,
                    "ewbible.db" // <-- Your DB file name will go here in Step 8
                )
                // When you send your DB file, we will change this to:
                // .createFromAsset("ewbible.db")  OR  .createFromFile(yourFile)
                // For now it creates an empty DB so app still builds
                .fallbackToDestructiveMigration(false)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
