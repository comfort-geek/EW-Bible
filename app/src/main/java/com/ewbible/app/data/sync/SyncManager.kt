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

// --- NEW: READING PROGRESS ---
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
        val uid = userId() ?: return
        val note = CloudNote(bookId, chapter, verse, text, System.currentTimeMillis())
        db.collection("users").document(uid)
            .collection("notes")
            .document("${bookId}_${chapter}_${verse}")
            .set(note).await()
    }

    suspend fun getNotes(): List<CloudNote> {
        val uid = userId() ?: return emptyList()
        val snap = db.collection("users").document(uid)
            .collection("notes").get().await()
        return snap.toObjects(CloudNote::class.java)
    }

    suspend fun deleteNote(bookId: Int, chapter: Int, verse: Int) {
        val uid = userId() ?: return
        db.collection("users").document(uid)
            .collection("notes")
            .document("${bookId}_${chapter}_${verse}")
            .delete().await()
    }

    // --- HIGHLIGHTS ---
    suspend fun uploadHighlight(bookId: Int, chapter: Int, verse: Int, color: String) {
        val uid = userId() ?: return
        val hl = CloudHighlight(bookId, chapter, verse, color)
        db.collection("users").document(uid)
            .collection("highlights")
            .document("${bookId}_${chapter}_${verse}")
            .set(hl).await()
    }

    suspend fun deleteHighlight(bookId: Int, chapter: Int, verse: Int) {
        val uid = userId() ?: return
        db.collection("users").document(uid)
            .collection("highlights")
            .document("${bookId}_${chapter}_${verse}")
            .delete().await()
    }

    suspend fun getHighlights(): List<CloudHighlight> {
        val uid = userId() ?: return emptyList()
        val snap = db.collection("users").document(uid)
            .collection("highlights").get().await()
        return snap.toObjects(CloudHighlight::class.java)
    }

    // --- READING PROGRESS ---
    suspend fun uploadProgress(mode: String, book: String, chapter: Int, verse: Int, scrollIndex: Int, scrollOffset: Int) {
        val uid = userId() ?: return
        val p = CloudProgress(mode, book, chapter, verse, scrollIndex, scrollOffset, System.currentTimeMillis())
        db.collection("users").document(uid)
            .collection("progress").document("lastReading")
            .set(p).await()
    }

    suspend fun getProgress(): CloudProgress? {
        val uid = userId() ?: return null
        val snap = db.collection("users").document(uid)
            .collection("progress").document("lastReading").get().await()
        return snap.toObject(CloudProgress::class.java)
    }
}
