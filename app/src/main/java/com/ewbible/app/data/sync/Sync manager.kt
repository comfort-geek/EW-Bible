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
}
