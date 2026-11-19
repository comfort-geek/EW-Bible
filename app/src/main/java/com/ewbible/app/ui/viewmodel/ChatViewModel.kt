package com.ewbible.app.ui.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ewbible.app.data.database.BibleDao
import com.ewbible.app.data.database.BookCatalog
import com.ewbible.app.data.database.ChatEntity
import com.ewbible.app.data.database.CommentaryHelper
import com.ewbible.app.data.database.GeminiRepository
import com.ewbible.app.data.database.MessageEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatState(
    val chats: List<ChatEntity> = emptyList(),
    val messages: List<MessageEntity> = emptyList(),
    val currentChatId: Long? = null,
    val isLoading: Boolean = false,
    val streamingText: String = "",
    val dailyCount: Int = 0,
    val error: String? = null
)

class ChatViewModel(
    private val bibleDao: BibleDao,
    private val geminiRepository: GeminiRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    // MAX: Pre-compile once. Before: Regex compiled on EVERY question.
    private val VERSE_REGEX = Regex("""([1-3]?\s?[A-Za-z]+)\s+(\d+):(\d+)""")

    // MAX: Cache bible context. Same chapter asked twice = 0 DB calls.
    // Key = question-verse OR currentRef
    private val contextCache = object : LinkedHashMap<String, String>(20, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, String>?) = size > 20
    }

    private var sendJob: Job? = null
    private var lastStreamEmit = 0L

    fun loadChats() {
        viewModelScope.launch {
            // Keep sync for one-shot, but don't reload after every send -
            // we now update chats list locally in startNewChat/send
            val chats = bibleDao.getAllChatsSync()
            _state.update { it.copy(chats = chats) }
        }
    }

    fun startNewChat(currentRef: String) {
        viewModelScope.launch {
            val chat = ChatEntity(title = "New Chat", ref = currentRef)
            val id = bibleDao.insertChat(chat)
            _state.update {
                it.copy(currentChatId = id, messages = emptyList(), streamingText = "", isLoading = false)
            }
            // MAX: No full DB reload - prepend locally
            _state.update { it.copy(chats = listOf(chat.copy(id = id)) + it.chats) }
        }
    }

    fun openChat(chatId: Long) {
        viewModelScope.launch {
            val messages = bibleDao.getMessagesForChatSync(chatId)
            // MAX: Real daily count from DB, not in-memory counter that resets on restart
            val todayCount = runCatching { bibleDao.getTodayUserMessageCount(chatId) }.getOrDefault(0)
            _state.update { it.copy(currentChatId = chatId, messages = messages, streamingText = "", dailyCount = todayCount) }
        }
    }

    fun deleteChat(chatId: Long) {
        viewModelScope.launch {
            bibleDao.deleteMessagesForChat(chatId)
            bibleDao.deleteChat(chatId)
            _state.update {
                if (it.currentChatId == chatId) it.copy(currentChatId = null, messages = emptyList(), chats = it.chats.filterNot { c -> c.id == chatId })
                else it.copy(chats = it.chats.filterNot { c -> c.id == chatId })
            }
        }
    }

    fun send(question: String, currentRef: String, image: Bitmap?, translation: String = "NKJV") {
        val chatId = _state.value.currentChatId ?: return
        if (_state.value.isLoading) return

        // MAX: Check real DB count, not volatile variable
        viewModelScope.launch {
            val todayCount = runCatching { bibleDao.getTodayUserMessageCount(chatId) }.getOrDefault(_state.value.dailyCount)
            if (todayCount >= 50) {
                _state.update { it.copy(error = "Daily limit reached (50/50)", dailyCount = todayCount) }
                return@launch
            }
            doSend(chatId, question, currentRef, image, translation, todayCount)
        }
    }

    private fun doSend(chatId: Long, question: String, currentRef: String, image: Bitmap?, translation: String, todayCount: Int) {
        sendJob?.cancel()
        sendJob = viewModelScope.launch {
            val userMsg = MessageEntity(chatId = chatId, text = question, isUser = true)
            val userId = bibleDao.insertMessage(userMsg)
            // MAX: Append locally, DON'T reload whole history from DB
            _state.update {
                it.copy(messages = it.messages + userMsg.copy(id = userId), isLoading = true, streamingText = "", error = null, dailyCount = todayCount)
            }

            val bibleContext = buildBibleContext(question, currentRef)

            val fullAnswer = StringBuilder(2000)
            lastStreamEmit = System.currentTimeMillis()
            try {
                // Use snapshot of history - don't re-query DB inside streaming loop
                val historySnapshot = _state.value.messages
                geminiRepository.askStreaming(
                    question = question,
                    currentRef = currentRef,
                    history = historySnapshot,
                    image = image,
                    bibleContext = bibleContext,
                    translation = translation
                ).collect { chunk ->
                    fullAnswer.append(chunk)
                    // MAX: Throttle UI updates - before: update on EVERY token = 100+ recompositions/sec = jank
                    // Now: max 1 update per 120ms
                    val now = System.currentTimeMillis()
                    if (now - lastStreamEmit >= 120) {
                        lastStreamEmit = now
                        _state.update { it.copy(streamingText = fullAnswer.toString()) }
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "Failed: ${e.message}") }
                return@launch
            }

            val answerText = fullAnswer.toString().ifBlank { "Sorry, I couldn't answer that." }
            val aiMsg = MessageEntity(chatId = chatId, text = answerText, isUser = false)
            val aiId = bibleDao.insertMessage(aiMsg)

            // MAX: Append locally, no full reload, no second loadChats() DB hit
            _state.update {
                it.copy(
                    messages = it.messages + aiMsg.copy(id = aiId),
                    isLoading = false,
                    streamingText = "",
                    dailyCount = todayCount + 1
                )
            }
        }
    }

    private suspend fun buildBibleContext(question: String, currentRef: String): String {
        return try {
            // 1. User asked about a specific verse: John 3:16
            val match = VERSE_REGEX.find(question)
            if (match != null) {
                val bookName = match.groupValues[1].trim()
                val chapter = match.groupValues[2].toIntOrNull() ?: 1
                val verse = match.groupValues[3].toIntOrNull() ?: 1
                val cacheKey = "v:$bookName-$chapter:$verse"
                contextCache[cacheKey]?.let { return it }

                val bookId = BookCatalog.id(bookName)
                // MAX: getVerse() = 1 row. Before: whole chapter via getVerses().first()
                val text = bibleDao.getVerse(bookId, chapter, verse)?.text ?: ""
                val summary = bibleDao.getSummary(bookId, chapter)?.summary ?: ""
                val result = "USER ASKED ABOUT $bookName $chapter:$verse\nNKJV TEXT: $text\nSUMMARY: $summary\n"
                contextCache[cacheKey] = result
                return result
            }

            // 2. Fallback: user is reading currentRef
            val cacheKey = "c:$currentRef"
            contextCache[cacheKey]?.let { return it }

            val parts = currentRef.trim().split(" ")
            val chapter = parts.lastOrNull()?.toIntOrNull() ?: 1
            val bookName = parts.dropLast(1).joinToString(" ")
            val bookId = BookCatalog.id(bookName)

            // MAX: Don't send whole 176-verse chapter to Gemini.
            // Psalms 119 = 176 verses = ~12KB prompt = slow + expensive.
            // Send max 30 verses + summary + commentary. Gemini doesn't need more.
            val chapterText = bibleDao.getVerseRange(bookId, chapter, 1, 30)
                .joinToString("\n") { "${it.verse}. ${it.text}" }

            val summary = bibleDao.getSummary(bookId, chapter)?.summary ?: ""
            val compressed = bibleDao.getCommentaryCompressed(bookId, chapter)
            // MAX: cleanCached() uses bookId*1000+chapter key, no 50KB hash.
            // Before: clean() hashed the whole BLOB every time.
            val commentary = CommentaryHelper.cleanCached(bookId, chapter, compressed).take(3000)

            val result = "USER IS READING $bookName $chapter (NKJV):\n$chapterText\n\nSUMMARY: $summary\nCOMMENTARY: $commentary\n"
            contextCache[cacheKey] = result
            result
        } catch (e: Exception) {
            ""
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}