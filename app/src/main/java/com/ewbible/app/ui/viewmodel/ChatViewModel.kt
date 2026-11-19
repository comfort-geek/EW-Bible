package com.ewbible.app.ui.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val geminiRepository: GeminiRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    // In-memory daily counter - resets on app restart
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

    fun send(question: String, currentRef: String, image: Bitmap?) {
        val chatId = _state.value.currentChatId ?: return
        if (_state.value.isLoading) return

        // Daily limit 50
        if (dailyCountInternal >= 50) {
            _state.update { it.copy(error = "Daily limit reached (50/50)") }
            return
        }

        viewModelScope.launch {
            // 1. Save user message
            val userMsg = MessageEntity(chatId = chatId, text = question, isUser = true)
            chatDao.insertMessage(userMsg)
            chatDao.updateChatTime(chatId, System.currentTimeMillis())

            val history = chatDao.getMessages(chatId)
            _state.update {
                it.copy(messages = history, isLoading = true, streamingText = "", error = null)
            }

            // 2. Stream from Gemini
            val fullAnswer = StringBuilder()
            try {
                geminiRepository.askStreaming(question, currentRef, history, image)
                    .collect { chunk ->
                        fullAnswer.append(chunk)
                        _state.update { it.copy(streamingText = fullAnswer.toString()) }
                    }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "Failed: ${e.message}") }
                return@launch
            }

            // 3. Save AI answer
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

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}