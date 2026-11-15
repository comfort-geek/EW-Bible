package com.ewbible.app.data.database

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import java.io.FileOutputStream

@Database(
    entities = [BookEntity::class, VerseEntity::class, SummaryEntity::class, CommentaryEntity::class, NoteEntity::class, HighlightEntity::class],
    version = 2, exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bibleDao(): BibleDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        // FIXED: matches real table - BLOB nullable, not NOT NULL
        private fun createCommentaryIfNeeded(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `commentary` (
                    `book_id` INTEGER NOT NULL,
                    `chapter` INTEGER NOT NULL,
                    `html_compressed` BLOB,
                    PRIMARY KEY(`book_id`, `chapter`)
                )
            """.trimIndent())
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `summaries` (
                    `book_id` INTEGER NOT NULL,
                    `chapter` INTEGER NOT NULL,
                    `summary` TEXT NOT NULL,
                    `what_happened` TEXT NOT NULL,
                    `application` TEXT NOT NULL,
                    PRIMARY KEY(`book_id`, `chapter`)
                )
            """.trimIndent())
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createCommentaryIfNeeded(db)
            }
        }

        // FIXED: Fresh install path 0 -> 2 was missing
        private val MIGRATION_0_2 = object : Migration(0, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createCommentaryIfNeeded(db)
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ewbible.db"
                )
                // FIXED: NO createFromAsset - Room creates its own v2 DB with correct hash
                .addMigrations(MIGRATION_1_2, MIGRATION_0_2)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Fresh install: import everything from seed
                        importFromAsset(context.applicationContext, db)
                    }
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        try {
                            val cursor = db.query("SELECT COUNT(*) FROM commentary")
                            cursor.moveToFirst()
                            val count = cursor.getInt(0)
                            cursor.close()
                            if (count == 0) {
                                importFromAsset(context.applicationContext, db)
                            }
                        } catch (e: Exception) { }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }

        private fun importFromAsset(context: Context, db: SupportSQLiteDatabase) {
            try {
                val tempFile = File(context.cacheDir, "temp_ewbible.db")
                context.assets.open("ewbible.db").use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                db.execSQL("ATTACH DATABASE '${tempFile.absolutePath}' AS newdb")
                db.execSQL("INSERT OR REPLACE INTO commentary SELECT * FROM newdb.commentary")
                db.execSQL("INSERT OR REPLACE INTO summaries SELECT * FROM newdb.summaries")
                db.execSQL("INSERT OR REPLACE INTO verses SELECT * FROM newdb.verses")
                db.execSQL("INSERT OR REPLACE INTO books SELECT * FROM newdb.books")
                db.execSQL("DETACH DATABASE newdb")
                tempFile.delete()
            } catch (e: Exception) { }
        }
    }
}