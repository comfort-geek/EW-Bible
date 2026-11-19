package com.ewbible.app.ui.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ewbible.app.data.database.BibleDao
import com.ewbible.app.data.database.BookCatalog
import com.ewbible.app.data.database.ChatDao
import com.ewbible.app.data.database.ChatEntity
import com.ewbible.app.data.database.GeminiRepository
import com.ewbible.app.data.database.MessageEntity
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
    private val chatDao: ChatDao,
    private val geminiRepository: GeminiRepository,
    private val bibleDao: BibleDao // WIRED - NEW
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var dailyCountInternal = 0

    fun loadChats() {
        viewModelScope.launch {
            val chats = chatDao.getChats()
            _state.update { it.copy(chats = chats) }
        }
    }

    fun startNewChat(currentRef: String) {
        viewModelScope.launch {
            val chat = ChatEntity(title = "New Chat", ref = currentRef)
            val id = chatDao.insertChat(chat)
            _state.update {
                it.copy(currentChatId = id, messages = emptyList(), streamingText = "", isLoading = false)
            }
            loadChats()
        }
    }

    fun openChat(chatId: Long) {
        viewModelScope.launch {
            val messages = chatDao.getMessages(chatId)
            _state.update { it.copy(currentChatId = chatId, messages = messages, streamingText = "") }
        }
    }

    fun deleteChat(chatId: Long) {
        viewModelScope.launch {
            chatDao.deleteMessages(chatId)
            chatDao.deleteChat(chatId)
            if (_state.value.currentChatId == chatId) {
                _state.update { it.copy(currentChatId = null, messages = emptyList()) }
            }
            loadChats()
        }
    }

    // WIRED - added translation param
    fun send(question: String, currentRef: String, image: Bitmap?, translation: String = "NKJV") {
        val chatId = _state.value.currentChatId ?: return
        if (_state.value.isLoading) return

        if (dailyCountInternal >= 50) {
            _state.update { it.copy(error = "Daily limit reached (50/50)") }
            return
        }

        viewModelScope.launch {
            val userMsg = MessageEntity(chatId = chatId, text = question, isUser = true)
            chatDao.insertMessage(userMsg)
            chatDao.updateChatTime(chatId, System.currentTimeMillis())

            val history = chatDao.getMessages(chatId)
            _state.update {
                it.copy(messages = history, isLoading = true, streamingText = "", error = null)
            }

            // WIRED - build NKJV context from your DB
            val bibleContext = buildBibleContext(question, currentRef)

            val fullAnswer = StringBuilder()
            try {
                // WIRED - passes bibleContext + translation
                geminiRepository.askStreaming(
                    question = question,
                    currentRef = currentRef,
                    history = history,
                    image = image,
                    bibleContext = bibleContext,
                    translation = translation
                ).collect { chunk ->
                    fullAnswer.append(chunk)
                    _state.update { it.copy(streamingText = fullAnswer.toString()) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "Failed: ${e.message}") }
                return@launch
            }

            val answerText = fullAnswer.toString().ifBlank { "Sorry, I couldn't answer that." }
            val aiMsg = MessageEntity(chatId = chatId, text = answerText, isUser = false)
            chatDao.insertMessage(aiMsg)
            chatDao.updateChatTime(chatId, System.currentTimeMillis())

            dailyCountInternal++
            val updatedMessages = chatDao.getMessages(chatId)
            _state.update {
                it.copy(
                    messages = updatedMessages,
                    isLoading = false,
                    streamingText = "",
                    dailyCount = dailyCountInternal
                )
            }
            loadChats()
        }
    }

    // WIRED - reads NKJV from ewbible.db
    private suspend fun buildBibleContext(question: String, currentRef: String): String {
        return try {
            // Case 1: User asked a specific verse like "John 3:16" or "Psalm 23:1"
            val verseRegex = Regex("""([1-3]?\s?[A-Za-z]+)\s+(\d+):(\d+)""")
            val match = verseRegex.find(question)
            if (match != null) {
                val bookName = match.groupValues[1].trim()
                val chapter = match.groupValues[2].toIntOrNull() ?: 1
                val verse = match.groupValues[3].toIntOrNull() ?: 1
                val bookId = BookCatalog.id(bookName)
                val text = bibleDao.getVerse(bookId, chapter, verse) ?: ""
                val summary = bibleDao.getSummary(bookId, chapter) ?: ""
                return "USER ASKED ABOUT $bookName $chapter:$verse\nNKJV TEXT: $text\nSUMMARY: $summary\n"
            }

            // Case 2: Use current chapter user is reading (from HomeScreen)
            val parts = currentRef.trim().split(" ")
            val chapter = parts.lastOrNull()?.toIntOrNull() ?: 1
            val bookName = parts.dropLast(1).joinToString(" ")
            val bookId = BookCatalog.id(bookName)
            val chapterText = bibleDao.getChapterText(bookId, chapter).joinToString("\n")
            val summary = bibleDao.getSummary(bookId, chapter) ?: ""
            val commentary = bibleDao.getCommentary(bookId, chapter) ?: ""
            return "USER IS READING $bookName $chapter (NKJV):\n$chapterText\n\nSUMMARY: $summary\nCOMMENTARY: ${commentary.take(3000)}\n"
        } catch (e: Exception) {
            ""
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}