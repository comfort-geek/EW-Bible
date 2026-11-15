package com.ewbible.app.data.sync

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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

    private fun notesCollection() = db.collection("users")
        .document(userId().orEmpty())
        .collection("notes")

    private fun highlightsCollection() = db.collection("users")
        .document(userId().orEmpty())
        .collection("highlights")

    private fun progressCollection() = db.collection("users")
        .document(userId().orEmpty())
        .collection("progress")

    // --- NOTES ---

    suspend fun uploadNote(
        bookId: Int,
        chapter: Int,
        verse: Int,
        text: String
    ) {
        try {
            val uid = userId() ?: return

            val note = CloudNote(
                bookId = bookId,
                chapter = chapter,
                verse = verse,
                noteText = text,
                timestamp = System.currentTimeMillis()
            )

            db.collection("users")
                .document(uid)
                .collection("notes")
                .document("${bookId}_${chapter}_${verse}")
                .set(note)
                .await()
        } catch (e: Exception) {
            Log.e("SyncManager", "uploadNote failed", e)
        }
    }

    suspend fun getNotes(): List<CloudNote> {
        return try {
            val uid = userId() ?: return emptyList()

            db.collection("users")
                .document(uid)
                .collection("notes")
                .get()
                .await()
                .toObjects(CloudNote::class.java)
        } catch (e: Exception) {
            Log.e("SyncManager", "getNotes failed", e)
            emptyList()
        }
    }

    suspend fun deleteNote(
        bookId: Int,
        chapter: Int,
        verse: Int
    ) {
        try {
            val uid = userId() ?: return

            db.collection("users")
                .document(uid)
                .collection("notes")
                .document("${bookId}_${chapter}_${verse}")
                .delete()
                .await()
        } catch (e: Exception) {
            Log.e("SyncManager", "deleteNote failed", e)
        }
    }

    // --- HIGHLIGHTS ---

    suspend fun uploadHighlight(
        bookId: Int,
        chapter: Int,
        verse: Int,
        color: String
    ) {
        try {
            val uid = userId() ?: return

            val highlight = CloudHighlight(
                bookId = bookId,
                chapter = chapter,
                verse = verse,
                color = color
            )

            db.collection("users")
                .document(uid)
                .collection("highlights")
                .document("${bookId}_${chapter}_${verse}")
                .set(highlight)
                .await()
        } catch (e: Exception) {
            Log.e("SyncManager", "uploadHighlight failed", e)
        }
    }

    suspend fun getHighlights(): List<CloudHighlight> {
        return try {
            val uid = userId() ?: return emptyList()

            db.collection("users")
                .document(uid)
                .collection("highlights")
                .get()
                .await()
                .toObjects(CloudHighlight::class.java)
        } catch (e: Exception) {
            Log.e("SyncManager", "getHighlights failed", e)
            emptyList()
        }
    }

    suspend fun deleteHighlight(
        bookId: Int,
        chapter: Int,
        verse: Int
    ) {
        try {
            val uid = userId() ?: return

            db.collection("users")
                .document(uid)
                .collection("highlights")
                .document("${bookId}_${chapter}_${verse}")
                .delete()
                .await()
        } catch (e: Exception) {
            Log.e("SyncManager", "deleteHighlight failed", e)
        }
    }

    // --- READING PROGRESS ---

    suspend fun uploadProgress(
        mode: String,
        book: String,
        chapter: Int,
        verse: Int,
        scrollIndex: Int,
        scrollOffset: Int
    ) {
        try {
            val uid = userId() ?: return

            val progress = CloudProgress(
                mode = mode,
                book = book,
                chapter = chapter,
                verse = verse,
                scrollIndex = scrollIndex,
                scrollOffset = scrollOffset,
                updatedAt = System.currentTimeMillis()
            )

            db.collection("users")
                .document(uid)
                .collection("progress")
                .document("lastReading")
                .set(progress)
                .await()
        } catch (e: Exception) {
            Log.e("SyncManager", "uploadProgress failed", e)
        }
    }

    suspend fun getProgress(): CloudProgress? {
        return try {
            val uid = userId() ?: return null

            db.collection("users")
                .document(uid)
                .collection("progress")
                .document("lastReading")
                .get()
                .await()
                .toObject(CloudProgress::class.java)
        } catch (e: Exception) {
            Log.e("SyncManager", "getProgress failed", e)
            null
        }
    }
}