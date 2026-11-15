package com.ewbible.app.data.database

import android.content.Context
import androidx.room.*
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

        // 1 -> 2: Keep Notes + Highlights. Don't delete anything.
        // Just make sure commentary / summaries tables exist.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `commentary` (
                        `book_id` INTEGER NOT NULL,
                        `chapter` INTEGER NOT NULL,
                        `html_compressed` BLOB NOT NULL,
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
                // notes + highlights are NOT touched, so they survive
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ewbible.db"
                )
                .createFromAsset("ewbible.db")
                .addMigrations(MIGRATION_1_2)
                // NO fallbackToDestructiveMigration() - we never delete user data
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        // If old user has empty commentary, fill it from new asset
                        // without touching notes/highlights
                        try {
                            val cursor = db.query("SELECT COUNT(*) FROM commentary")
                            cursor.moveToFirst()
                            val count = cursor.getInt(0)
                            cursor.close()
                            if (count == 0) {
                                importCommentaryFromAsset(context.applicationContext, db)
                            }
                        } catch (e: Exception) { }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }

        private fun importCommentaryFromAsset(context: Context, db: SupportSQLiteDatabase) {
            try {
                // Copy new asset DB to temp file
                val tempFile = File(context.cacheDir, "temp_ewbible.db")
                context.assets.open("ewbible.db").use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                // Attach temp DB and copy only commentary + summaries + verses + books
                // NOTES + HIGHLIGHTS are NOT copied, so user's own data stays
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