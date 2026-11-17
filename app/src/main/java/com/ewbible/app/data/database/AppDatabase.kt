package com.ewbible.app.data.database

import android.content.Context
import android.database.Cursor
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [BookEntity::class, VerseEntity::class, SummaryEntity::class, CommentaryEntity::class, NoteEntity::class, HighlightEntity::class],
    version = 3, exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bibleDao(): BibleDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        // BUMP THIS every time you replace ewbible.db in assets/
        private const val ASSET_DB_VERSION = 3
        private const val PREFS = "db_prefs"
        private const val KEY_ASSET_VERSION = "asset_db_version"
        private const val DB_NAME = "ewbible.db"

        private data class NoteBackup(
            val bookId: Int, val chapter: Int, val verse: Int,
            val noteText: String, val timestamp: Long
        )
        private data class HighlightBackup(
            val bookId: Int, val chapter: Int, val verse: Int, val color: String
        )

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val appContext = context.applicationContext
                    val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    val savedVersion = prefs.getInt(KEY_ASSET_VERSION, -1)
                    val needsAssetUpdate = savedVersion != ASSET_DB_VERSION

                    var notesBackup: List<NoteBackup> = emptyList()
                    var highlightsBackup: List<HighlightBackup> = emptyList()

                    if (needsAssetUpdate) {
                        // 1. Backup user data BEFORE deleting
                        notesBackup = readNotes(appContext)
                        highlightsBackup = readHighlights(appContext)

                        // 2. Delete old DB so createFromAsset copies the new one
                        appContext.deleteDatabase(DB_NAME)
                        prefs.edit().putInt(KEY_ASSET_VERSION, ASSET_DB_VERSION).apply()
                        INSTANCE = null
                    }

                    val instance = Room.databaseBuilder(
                        appContext,
                        AppDatabase::class.java,
                        DB_NAME
                    )
                        .createFromAsset("ewbible.db")
                        .fallbackToDestructiveMigration()
                        .build()

                    // 3. Restore user data synchronously, before UI reads it
                    if (needsAssetUpdate && (notesBackup.isNotEmpty() || highlightsBackup.isNotEmpty())) {
                        restoreUserData(instance, notesBackup, highlightsBackup)
                    }

                    INSTANCE = instance
                    instance
                }
            }
        }

        // Tries snake_case first (matches your BibleDao: book_id, note_text)
        // Falls back to camelCase if your entities use bookId / noteText
        private fun readNotes(context: Context): List<NoteBackup> {
            val dbFile = context.getDatabasePath(DB_NAME)
            if (!dbFile.exists()) return emptyList()
            // Try snake_case, then camelCase
            readNotesWithColumns(dbFile.path,
                "SELECT book_id, chapter, verse, note_text, timestamp FROM notes"
            ).let { if (it.isNotEmpty()) return it }
            return readNotesWithColumns(dbFile.path,
                "SELECT bookId, chapter, verse, noteText, timestamp FROM notes"
            )
        }

        private fun readNotesWithColumns(path: String, sql: String): List<NoteBackup> {
            val out = mutableListOf<NoteBackup>()
            runCatching {
                val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                    path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY
                )
                val c: Cursor = db.rawQuery(sql, null)
                c.use {
                    while (it.moveToNext()) {
                        out.add(
                            NoteBackup(
                                bookId = it.getInt(0),
                                chapter = it.getInt(1),
                                verse = it.getInt(2),
                                noteText = it.getString(3) ?: "",
                                timestamp = it.getLong(4)
                            )
                        )
                    }
                }
                db.close()
            }
            return out
        }

        private fun readHighlights(context: Context): List<HighlightBackup> {
            val dbFile = context.getDatabasePath(DB_NAME)
            if (!dbFile.exists()) return emptyList()
            readHighlightsWithColumns(dbFile.path,
                "SELECT book_id, chapter, verse, color FROM highlights"
            ).let { if (it.isNotEmpty()) return it }
            return readHighlightsWithColumns(dbFile.path,
                "SELECT bookId, chapter, verse, color FROM highlights"
            )
        }

        private fun readHighlightsWithColumns(path: String, sql: String): List<HighlightBackup> {
            val out = mutableListOf<HighlightBackup>()
            runCatching {
                val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                    path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY
                )
                val c: Cursor = db.rawQuery(sql, null)
                c.use {
                    while (it.moveToNext()) {
                        out.add(
                            HighlightBackup(
                                bookId = it.getInt(0),
                                chapter = it.getInt(1),
                                verse = it.getInt(2),
                                color = it.getString(3) ?: ""
                            )
                        )
                    }
                }
                db.close()
            }
            return out
        }

        private fun restoreUserData(
            db: AppDatabase,
            notes: List<NoteBackup>,
            highlights: List<HighlightBackup>
        ) {
            runCatching {
                val sqlite: SupportSQLiteDatabase = db.openHelper.writableDatabase
                // Detect which column naming the NEW DB uses
                val notesCols = getColumns(sqlite, "notes")
                val notesSql = if ("book_id" in notesCols) {
                    "INSERT OR REPLACE INTO notes (book_id, chapter, verse, note_text, timestamp) VALUES (?,?,?,?,?)"
                } else {
                    "INSERT OR REPLACE INTO notes (bookId, chapter, verse, noteText, timestamp) VALUES (?,?,?,?,?)"
                }
                val hlCols = getColumns(sqlite, "highlights")
                val hlSql = if ("book_id" in hlCols) {
                    "INSERT OR REPLACE INTO highlights (book_id, chapter, verse, color) VALUES (?,?,?,?)"
                } else {
                    "INSERT OR REPLACE INTO highlights (bookId, chapter, verse, color) VALUES (?,?,?,?)"
                }

                sqlite.beginTransaction()
                try {
                    for (n in notes) {
                        sqlite.execSQL(notesSql, arrayOf(n.bookId, n.chapter, n.verse, n.noteText, n.timestamp))
                    }
                    for (h in highlights) {
                        sqlite.execSQL(hlSql, arrayOf(h.bookId, h.chapter, h.verse, h.color))
                    }
                    sqlite.setTransactionSuccessful()
                } finally {
                    sqlite.endTransaction()
                }
            }
        }

        private fun getColumns(db: SupportSQLiteDatabase, table: String): Set<String> {
            val cols = mutableSetOf<String>()
            runCatching {
                val c = db.query("PRAGMA table_info($table)")
                c.use {
                    while (it.moveToNext()) {
                        cols.add(it.getString(1) ?: "")
                    }
                }
            }
            return cols
        }
    }
}