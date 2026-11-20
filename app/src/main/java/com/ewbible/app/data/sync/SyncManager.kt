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
    val mode: String = "New King James Version",
    val book: String = "Genesis",
    val chapter: Int = 1,
    val verse: Int = 1,
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
    val updatedAt: Long = 0
)

// --- GENERAL AI CHAT - NOT Bible-only ---

data class CloudChat(
    val chatId: String = "",
    val title: String = "",
    // General: "general", "bible", "prayer", "study", "life", "devotional", etc.
    val type: String = "general",
    // Generic context for this chat: e.g. "John 3:16", "Prayer for family", "Image question", "" if none
    val context: String = "",
    // Kept for old Bible chats - optional now, use `context` for new chats
    val bibleRef: String = "",
    val translation: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)

data class CloudMessage(
    val messageId: String = "",
    val chatId: String = "",
    val role: String = "user", // "user" or "ai"
    val type: String = "text", // "text", "image"
    val text: String = "",
    // Generic context for this message, not just Bible
    val context: String = "",
    val bibleRef: String = "",
    val imageUrl: String = "", // optional, if user sent an image
    val timestamp: Long = 0
)

class SyncManager {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private fun userId(): String? = auth.currentUser?.uid

    private fun notesRef(uid: String) = db.collection("users").document(uid).collection("notes")
    private fun highlightsRef(uid: String) = db.collection("users").document(uid).collection("highlights")
    private fun progressRef(uid: String) = db.collection("users").document(uid).collection("progress")
    private fun chatsRef(uid: String) = db.collection("users").document(uid).collection("aiChats")
    private fun messagesRef(uid: String, chatId: String) =
        chatsRef(uid).document(chatId).collection("messages")

    private fun docId(bookId: Int, chapter: Int, verse: Int) = "${bookId}_${chapter}_${verse}"

    // --- NOTES ---

    suspend fun uploadNote(bookId: Int, chapter: Int, verse: Int, text: String): Result<Unit> {
        val uid = userId() ?: return Result.failure(NotLoggedInException())
        return runCatching {
            val note = CloudNote(bookId, chapter, verse, text, System.currentTimeMillis())
            notesRef(uid).document(docId(bookId, chapter, verse))
                .set(note, SetOptions.merge()).await()
            Unit
        }.onFailure { Log.e("SyncManager", "uploadNote failed", it) }
    }

    suspend fun getNotes(): Result<List<CloudNote>> {
        val uid = userId() ?: return Result.failure(NotLoggedInException())
        return runCatching {
            notesRef(uid).get().await().toObjects(CloudNote::class.java)
        }.onFailure { Log.e("SyncManager", "getNotes failed", it) }
    }

    suspend fun deleteNote(bookId: Int, chapter: Int, verse: Int): Result<Unit> {
        val uid = userId() ?: return Result.failure(NotLoggedInException())
        return runCatching {
            notesRef(uid).document(docId(bookId, chapter, verse)).delete().await()
            Unit
        }.onFailure { Log.e("SyncManager", "deleteNote failed", it) }
    }

    // --- HIGHLIGHTS ---

    suspend fun uploadHighlight(bookId: Int, chapter: Int, verse: Int, color: String): Result<Unit> {
        val uid = userId() ?: return Result.failure(NotLoggedInException())
        return runCatching {
            val highlight = CloudHighlight(bookId, chapter, verse, color)
            highlightsRef(uid).document(docId(bookId, chapter, verse))
                .set(highlight, SetOptions.merge()).await()
            Unit
        }.onFailure { Log.e("SyncManager", "uploadHighlight failed", it) }
    }

    suspend fun getHighlights(): Result<List<CloudHighlight>> {
        val uid = userId() ?: return Result.failure(NotLoggedInException())
        return runCatching {
            highlightsRef(uid).get().await().toObjects(CloudHighlight::class.java)
        }.onFailure { Log.e("SyncManager", "getHighlights failed", it) }
    }

    suspend fun deleteHighlight(bookId: Int, chapter: Int, verse: Int): Result<Unit> {
        val uid = userId() ?: return Result.failure(NotLoggedInException())
        return runCatching {
            highlightsRef(uid).document(docId(bookId, chapter, verse)).delete().await()
            Unit
        }.onFailure { Log.e("SyncManager", "deleteHighlight failed", it) }
    }

    // --- READING PROGRESS ---

    suspend fun uploadProgress(
        mode: String, book: String, chapter: Int, verse: Int,
        scrollIndex: Int, scrollOffset: Int
    ): Result<Unit> {
        val uid = userId() ?: return Result.failure(NotLoggedInException())
        return runCatching {
            val progress = CloudProgress(mode, book, chapter, verse, scrollIndex, scrollOffset, System.currentTimeMillis())
            progressRef(uid).document("lastReading").set(progress, SetOptions.merge()).await()
            Unit
        }.onFailure { Log.e("SyncManager", "uploadProgress failed", it) }
    }

    suspend fun getProgress(): Result<CloudProgress?> {
        val uid = userId() ?: return Result.failure(NotLoggedInException())
        return runCatching {
            progressRef(uid).document("lastReading").get().await().toObject(CloudProgress::class.java)
        }.onFailure { Log.e("SyncManager", "getProgress failed", it) }
    }

    // --- GENERAL AI CHAT / MESSAGES ---

    suspend fun createChat(
        chatId: String,
        title: String,
        type: String = "general",
        context: String = "",
        bibleRef: String = "",
        translation: String = ""
    ): Result<Unit> {
        val uid = userId() ?: return Result.failure(NotLoggedInException())
        return runCatching {
            val now = System.currentTimeMillis()
            val chat = CloudChat(chatId, title, type, context, bibleRef, translation, now, now)
            chatsRef(uid).document(chatId).set(chat, SetOptions.merge()).await()
            Unit
        }.onFailure { Log.e("SyncManager", "createChat failed", it) }
    }

    suspend fun uploadMessage(chatId: String, message: CloudMessage): Result<Unit> {
        val uid = userId() ?: return Result.failure(NotLoggedInException())
        return runCatching {
            val msgId = message.messageId.ifBlank { messagesRef(uid, chatId).document().id }
            val toSave = message.copy(
                messageId = msgId,
                chatId = chatId,
                timestamp = if (message.timestamp == 0L) System.currentTimeMillis() else message.timestamp
            )
            messagesRef(uid, chatId).document(msgId).set(toSave, SetOptions.merge()).await()
            chatsRef(uid).document(chatId).update("updatedAt", toSave.timestamp).await()
            Unit
        }.onFailure { Log.e("SyncManager", "uploadMessage failed", it) }
    }

    suspend fun getChatList(): Result<List<CloudChat>> {
        val uid = userId() ?: return Result.failure(NotLoggedInException())
        return runCatching {
            chatsRef(uid).orderBy("updatedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get().await().toObjects(CloudChat::class.java)
        }.onFailure { Log.e("SyncManager", "getChatList failed", it) }
    }

    suspend fun getMessages(chatId: String): Result<List<CloudMessage>> {
        val uid = userId() ?: return Result.failure(NotLoggedInException())
        return runCatching {
            messagesRef(uid, chatId).orderBy("timestamp")
                .get().await().toObjects(CloudMessage::class.java)
        }.onFailure { Log.e("SyncManager", "getMessages failed", it) }
    }

    suspend fun deleteChat(chatId: String): Result<Unit> {
        val uid = userId() ?: return Result.failure(NotLoggedInException())
        return runCatching {
            val msgs = messagesRef(uid, chatId).get().await().documents
            val batch = db.batch()
            msgs.forEach { batch.delete(it.reference) }
            batch.delete(chatsRef(uid).document(chatId))
            batch.commit().await()
            Unit
        }.onFailure { Log.e("SyncManager", "deleteChat failed", it) }
    }

    suspend fun syncMessages(chatId: String, messages: List<CloudMessage>): Result<Unit> {
        val uid = userId() ?: return Result.failure(NotLoggedInException())
        return runCatching {
            messages.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { msg ->
                    val msgId = msg.messageId.ifBlank { messagesRef(uid, chatId).document().id }
                    batch.set(messagesRef(uid, chatId).document(msgId), msg.copy(messageId = msgId, chatId = chatId), SetOptions.merge())
                }
                batch.commit().await()
            }
            Unit
        }.onFailure { Log.e("SyncManager", "syncMessages failed", it) }
    }

    // --- SYNC ALL ---

    suspend fun syncAll(localNotes: List<CloudNote>, localHighlights: List<CloudHighlight>): Result<Unit> {
        val uid = userId() ?: return Result.failure(NotLoggedInException())
        return runCatching {
            val allWrites: List<Pair<com.google.firebase.firestore.DocumentReference, Any>> =
                localNotes.map { notesRef(uid).document(docId(it.bookId, it.chapter, it.verse)) to it } +
                localHighlights.map { highlightsRef(uid).document(docId(it.bookId, it.chapter, it.verse)) to it }
            allWrites.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { (ref, data) -> batch.set(ref, data, SetOptions.merge()) }
                batch.commit().await()
            }
            Unit
        }.onFailure { Log.e("SyncManager", "syncAll failed", it) }
    }

    class NotLoggedInException : Exception("Not logged in - sync skipped")
}