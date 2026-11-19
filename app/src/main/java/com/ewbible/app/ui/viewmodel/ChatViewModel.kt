package com.ewbible.app.ui.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ewbible.app.data.database.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ChatUiState(
    val chats: List<ChatEntity> = emptyList(),
    val messages: List<MessageEntity> = emptyList(),
    val currentChatId: Long? = null,
    val isLoading: Boolean = false,
    val streamingText: String = "",
    val dailyCount: Int = 0
)

class ChatViewModel(
    private val dao: ChatDao,
    private val repo: GeminiRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state = _state.asStateFlow()

    fun loadChats() = viewModelScope.launch {
        _state.value = _state.value.copy(chats = dao.getChats())
    }

    fun startNewChat(ref: String) = viewModelScope.launch {
        val id = dao.insertChat(ChatEntity(title = ref, ref = ref))
        _state.value = _state.value.copy(currentChatId = id, messages = emptyList())
        loadChats()
    }

    fun openChat(chatId: Long) = viewModelScope.launch {
        _state.value = _state.value.copy(
            currentChatId = chatId,
            messages = dao.getMessages(chatId)
        )
    }

    fun send(question: String, ref: String, image: Bitmap?) = viewModelScope.launch {
        val chatId = _state.value.currentChatId ?: return@launch
        if (question.isBlank()) return@launch
        if (_state.value.dailyCount >= 50) {
            // Local per-device limit, no login needed
            dao.insertMessage(MessageEntity(chatId = chatId, text = "Daily limit reached (50). Try tomorrow.", isUser = false))
            _state.value = _state.value.copy(messages = dao.getMessages(chatId))
            return@launch
        }

        dao.insertMessage(MessageEntity(chatId = chatId, text = question, isUser = true))
        val messagesWithUser = dao.getMessages(chatId)
        _state.value = _state.value.copy(messages = messagesWithUser, isLoading = true, streamingText = "")

        var fullAnswer = ""
        try {
            // FIXED: dropLast(1) so Gemini doesn't see the question twice
            // messagesWithUser already contains the question, history should be BEFORE it
            repo.askStreaming(question, ref, messagesWithUser.dropLast(1), image).collect { chunk ->
                fullAnswer += chunk
                _state.value = _state.value.copy(streamingText = fullAnswer)
            }
        } catch (e: Exception) {
            fullAnswer = if (fullAnswer.isBlank()) "Sorry, I couldn't reach the AI. Check your connection and try again." else fullAnswer
        }

        if (fullAnswer.isNotBlank()) {
            dao.insertMessage(MessageEntity(chatId = chatId, text = fullAnswer, isUser = false))
        }
        _state.value = _state.value.copy(
            messages = dao.getMessages(chatId),
            isLoading = false,
            streamingText = "",
            dailyCount = _state.value.dailyCount + 1
        )
    }

    fun deleteChat(chatId: Long) = viewModelScope.launch {
        dao.deleteMessages(chatId)
        dao.deleteChat(chatId)
        if (_state.value.currentChatId == chatId) {
            _state.value = _state.value.copy(currentChatId = null, messages = emptyList())
        }
        loadChats()
    }
}