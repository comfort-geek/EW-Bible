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
    private val bibleDao: BibleDao, // ONLY DAO NOW - ChatDao deleted
    private val geminiRepository: GeminiRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var dailyCountInternal = 0

    fun loadChats() {
        viewModelScope.launch {
            val chats = bibleDao.getAllChats()
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
            loadChats()
        }
    }

    fun openChat(chatId: Long) {
        viewModelScope.launch {
            val messages = bibleDao.getMessages(chatId)
            _state.update { it.copy(currentChatId = chatId, messages = messages, streamingText = "") }
        }
    }

    fun deleteChat(chatId: Long) {
        viewModelScope.launch {
            bibleDao.deleteMessagesForChat(chatId)
            bibleDao.deleteChat(chatId)
            if (_state.value.currentChatId == chatId) {
                _state.update { it.copy(currentChatId = null, messages = emptyList()) }
            }
            loadChats()
        }
    }

    fun send(question: String, currentRef: String, image: Bitmap?, translation: String = "NKJV") {
        val chatId = _state.value.currentChatId ?: return
        if (_state.value.isLoading) return

        if (dailyCountInternal >= 50) {
            _state.update { it.copy(error = "Daily limit reached (50/50)") }
            return
        }

        viewModelScope.launch {
            val userMsg = MessageEntity(chatId = chatId, text = question, isUser = true)
            bibleDao.insertMessage(userMsg)

            val history = bibleDao.getMessages(chatId)
            _state.update {
                it.copy(messages = history, isLoading = true, streamingText = "", error = null)
            }

            val bibleContext = buildBibleContext(question, currentRef)

            val fullAnswer = StringBuilder()
            try {
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
            bibleDao.insertMessage(aiMsg)

            dailyCountInternal++
            val updatedMessages = bibleDao.getMessages(chatId)
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

    private suspend fun buildBibleContext(question: String, currentRef: String): String {
        return try {
            val verseRegex = Regex("""([1-3]?\s?[A-Za-z]+)\s+(\d+):(\d+)""")
            val match = verseRegex.find(question)
            if (match != null) {
                val bookName = match.groupValues[1].trim()
                val chapter = match.groupValues[2].toIntOrNull() ?: 1
                val verse = match.groupValues[3].toIntOrNull() ?: 1
                val bookId = BookCatalog.id(bookName)
                // FIXED: getVerse returns VerseEntity, take .text
                val text = bibleDao.getVerse(bookId, chapter, verse)?.text ?: ""
                val summary = bibleDao.getSummary(bookId, chapter) ?: ""
                return "USER ASKED ABOUT $bookName $chapter:$verse\nNKJV TEXT: $text\nSUMMARY: $summary\n"
            }

            val parts = currentRef.trim().split(" ")
            val chapter = parts.lastOrNull()?.toIntOrNull() ?: 1
            val bookName = parts.dropLast(1).joinToString(" ")
            val bookId = BookCatalog.id(bookName)
            val chapterText = bibleDao.getChapterText(bookId, chapter).joinToString("\n")
            val summary = bibleDao.getSummary(bookId, chapter) ?: ""
            // FIXED: getCommentaryCompressed + Helper, not getCommentary()
            val compressed = bibleDao.getCommentaryCompressed(bookId, chapter)
            val commentary = CommentaryHelper.clean(compressed).take(3000)
            return "USER IS READING $bookName $chapter (NKJV):\n$chapterText\n\nSUMMARY: $summary\nCOMMENTARY: $commentary\n"
        } catch (e: Exception) {
            ""
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}