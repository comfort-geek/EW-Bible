package com.ewbible.app.data.sync

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

    // --- NOTES ---
    suspend fun uploadNote(bookId: Int, chapter: Int, verse: Int, text: String) {
        try {
            val uid = userId() ?: return
            val note = CloudNote(bookId, chapter, verse, text, System.currentTimeMillis())
            db.collection("users").document(uid)
                .collection("notes")
                .document("${bookId}_${chapter}_${verse}")
                .set(note).await()
        } catch (e: Exception) { /* offline - keep local, don't crash */ }
    }

    suspend fun getNotes(): List<CloudNote> {
        return try {
            val uid = userId() ?: return emptyList()
            val snap = db.collection("users").document(uid)
                .collection("notes").get().await()
            snap.toObjects(CloudNote::class.java)
        } catch (e: Exception) { emptyList() }
    }

    suspend fun deleteNote(bookId: Int, chapter: Int, verse: Int) {
        try {
            val uid = userId() ?: return
            db.collection("users").document(uid)
                .collection("notes")
                .document("${bookId}_${chapter}_${verse}")
                .delete().await()
        } catch (e: Exception) { }
    }

    // --- HIGHLIGHTS ---
    suspend fun uploadHighlight(bookId: Int, chapter: Int, verse: Int, color: String) {
        try {
            val uid = userId() ?: return
            val hl = CloudHighlight(bookId, chapter, verse, color)
            db.collection("users").document(uid)
                .collection("highlights")
                .document("${bookId}_${chapter}_${verse}")
                .set(hl).await()
        } catch (e: Exception) { }
    }

    suspend fun deleteHighlight(bookId: Int, chapter: Int, verse: Int) {
        try {
            val uid = userId() ?: return
            db.collection("users").document(uid)
                .collection("highlights")
                .document("${bookId}_${chapter}_${verse}")
                .delete().await()
        } catch (e: Exception) { }
    }

    suspend fun getHighlights(): List<CloudHighlight> {
        return try {
            val uid = userId() ?: return emptyList()
            val snap = db.collection("users").document(uid)
                .collection("highlights").get().await()
            snap.toObjects(CloudHighlight::class.java)
        } catch (e: Exception) { emptyList() }
    }

    // --- READING PROGRESS ---
    suspend fun uploadProgress(mode: String, book: String, chapter: Int, verse: Int, scrollIndex: Int, scrollOffset: Int) {
        try {
            val uid = userId() ?: return
            val p = CloudProgress(mode, book, chapter, verse, scrollIndex, scrollOffset, System.currentTimeMillis())
            db.collection("users").document(uid)
                .collection("progress").document("lastReading")
                .set(p).await()
        } catch (e: Exception) { }
    }

    suspend fun getProgress(): CloudProgress? {
        return try {
            val uid = userId() ?: return null
            val snap = db.collection("users").document(uid)
                .collection("progress").document("lastReading").get().await()
            snap.toObject(CloudProgress::class.java)
        } catch (e: Exception) { null }
    }
}