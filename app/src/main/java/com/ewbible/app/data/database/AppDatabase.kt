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
                        // Do NOT add fallbackToDestructiveMigration() - it wipes commentary
                        // If you bump version in future, add a real Migration here:
                        // .addMigrations(MIGRATION_2_3)
                        .build()
                    INSTANCE = instance
                    instance
                }
            }
        }

        // Example for future: empty migration if you only add a column with default
        // val MIGRATION_2_3 = object : Migration(2, 3) {
        //     override fun migrate(db: SupportSQLiteDatabase) {
        //         db.execSQL("ALTER TABLE notes ADD COLUMN extra TEXT NOT NULL DEFAULT ''")
        //     }
        // }
    }
}