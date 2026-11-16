package com.ewbible.app.data.sync

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

data class CloudNote(
    val bookId: Int = 0,
    val chapter: Int = 0,
    val verse: Int = 0,
    val noteText: String = "",
    val timestamp: Long = 0
)

data class CloudHighlight(
    val bookId: Int = 0,
    val chapter: Int = 0,
    val verse: Int = 0,
    val color: String = ""
)

data class CloudProgress(
    val mode: String = "World English Bible",
    val book: String = "Genesis",
    val chapter: Int = 1,
    val verse: Int = 1,
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
    val updatedAt: Long = 0
)

class SyncManager {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private fun userId(): String? = auth.currentUser?.uid

    private fun notesRef(uid: String) = db.collection("users").document(uid).collection("notes")
    private fun highlightsRef(uid: String) = db.collection("users").document(uid).collection("highlights")
    private fun progressRef(uid: String) = db.collection("users").document(uid).collection("progress")

    private fun docId(bookId: Int, chapter: Int, verse: Int) = "${bookId}_${chapter}_${verse}"

    // --- NOTES ---

    suspend fun uploadNote(
        bookId: Int,
        chapter: Int,
        verse: Int,
        text: String
    ): Result<Unit> {
        val uid = userId() ?: return Result.failure(NotLoggedInException())
        return runCatching {
            val note = CloudNote(
                bookId = bookId,
                chapter = chapter,
                verse = verse,
                noteText = text,
                timestamp = System.currentTimeMillis()
            )
            notesRef(uid).document(docId(bookId, chapter, verse))
                .set(note, SetOptions.merge())
                .await()
            Unit // FIXED: await() returns Void, force Unit
        }.onFailure { Log.e("SyncManager", "uploadNote failed", it) }
    }

    suspend fun getNotes(): Result<List<CloudNote>> {
        val uid = userId() ?: return Result.failure(NotLoggedInException())
        return runCatching {
            notesRef(uid).get().await().toObjects(CloudNote::class.java)
        }.onFailure { Log.e("SyncManager", "getNotes failed", it) }
    }

    suspend fun deleteNote(
        bookId: Int,
        chapter: Int,
        verse: Int
    ): Result<Unit> {
        val uid = userId() ?: return Result.failure(NotLoggedInException())
        return runCatching {
            notesRef(uid).document(docId(bookId, chapter, verse)).delete().await()
            Unit // FIXED
        }.onFailure { Log.e("SyncManager", "deleteNote failed", it) }
    }

    // --- HIGHLIGHTS ---

    suspend fun uploadHighlight(
        bookId: Int,
        chapter: Int,
        verse: Int,
        color: String
    ): Result<Unit> {
        val uid = userId() ?: return Result.failure(NotLoggedInException())
        return runCatching {
            val highlight = CloudHighlight(
                bookId = bookId,
                chapter = chapter,
                verse = verse,
                color = color
            )
            highlightsRef(uid).document(docId(bookId, chapter, verse))
                .set(highlight, SetOptions.merge())
                .await()
            Unit // FIXED
        }.onFailure { Log.e("SyncManager", "uploadHighlight failed", it) }
    }

    suspend fun getHighlights(): Result<List<CloudHighlight>> {
        val uid = userId() ?: return Result.failure(NotLoggedInException())
        return runCatching {
            highlightsRef(uid).get().await().toObjects(CloudHighlight::class.java)
        }.onFailure { Log.e("SyncManager", "getHighlights failed", it) }
    }

    suspend fun deleteHighlight(
        bookId: Int,
        chapter: Int,
        verse: Int
    ): Result<Unit> {
        val uid = userId() ?: return Result.failure(NotLoggedInException())
        return runCatching {
            highlightsRef(uid).document(docId(bookId, chapter, verse)).delete().await()
            Unit // FIXED
        }.onFailure { Log.e("SyncManager", "deleteHighlight failed", it) }
    }

    // --- READING PROGRESS ---

    suspend fun uploadProgress(
        mode: String,
        book: String,
        chapter: Int,
        verse: Int,
        scrollIndex: Int,
        scrollOffset: Int
    ): Result<Unit> {
        val uid = userId() ?: return Result.failure(NotLoggedInException())
        return runCatching {
            val progress = CloudProgress(
                mode = mode,
                book = book,
                chapter = chapter,
                verse = verse,
                scrollIndex = scrollIndex,
                scrollOffset = scrollOffset,
                updatedAt = System.currentTimeMillis()
            )
            progressRef(uid).document("lastReading")
                .set(progress, SetOptions.merge())
                .await()
            Unit // FIXED
        }.onFailure { Log.e("SyncManager", "uploadProgress failed", it) }
    }

    suspend fun getProgress(): Result<CloudProgress?> {
        val uid = userId() ?: return Result.failure(NotLoggedInException())
        return runCatching {
            progressRef(uid).document("lastReading").get().await()
                .toObject(CloudProgress::class.java)
        }.onFailure { Log.e("SyncManager", "getProgress failed", it) }
    }

    suspend fun syncAll(
        localNotes: List<CloudNote>,
        localHighlights: List<CloudHighlight>
    ): Result<Unit> {
        val uid = userId() ?: return Result.failure(NotLoggedInException())
        return runCatching {
            val batch = db.batch()
            localNotes.forEach { note ->
                val ref = notesRef(uid).document(docId(note.bookId, note.chapter, note.verse))
                batch.set(ref, note, SetOptions.merge())
            }
            localHighlights.forEach { h ->
                val ref = highlightsRef(uid).document(docId(h.bookId, h.chapter, h.verse))
                batch.set(ref, h, SetOptions.merge())
            }
            batch.commit().await()
            Unit // FIXED
        }.onFailure { Log.e("SyncManager", "syncAll failed", it) }
    }

    class NotLoggedInException : Exception("Not logged in - sync skipped")
}