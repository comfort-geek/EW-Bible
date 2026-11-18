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
        NoteEntity::class,
        HighlightEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bibleDao(): BibleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        private const val DB_NAME = "ewbible.db"

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                    .createFromAsset(DB_NAME)
                    // FIX: Was fallbackToDestructiveMigration() - would wipe Notes/Highlights on any version bump
                    // Now only destructive on downgrade. Upgrades must have a real Migration.
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                    .also { INSTANCE = it }
            }
        }

        // When you bump version to 4, add here, do NOT use destructive migration:
        // Example:
        // val MIGRATION_3_4 = object : Migration(3, 4) {
        //     override fun migrate(db: SupportSQLiteDatabase) {
        //         db.execSQL("CREATE INDEX IF NOT EXISTS `index_verses_book_chapter` ON `verses` (`bookId`, `chapter`)")
        //     }
        // }
        // Then add .addMigrations(MIGRATION_3_4) above .build()
    }
}