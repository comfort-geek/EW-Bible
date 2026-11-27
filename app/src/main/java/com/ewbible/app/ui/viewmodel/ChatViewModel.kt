package com.example.bibleapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bibleapp.BuildConfig
import com.example.bibleapp.data.*
import com.example.bibleapp.network.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ChatViewModel : ViewModel() {

    private val api = RetrofitInstance.api
    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    private var sendJob: Job? = null

    var currentRef: String = ""
        private set

    var currentChatId: String = ""
        private set

    private var isNewChat: Boolean = true
    private var currentChat: Chat? = null

    // Explicit verse typed by user: John 3:16, Romans 8:28, Genesis 1:1 etc.
    private val VERSE_REGEX = Regex("""\b(?:Genesis|Exodus|Leviticus|Numbers|Deuteronomy|Joshua|Judges|Ruth|1 Samuel|2 Samuel|1 Kings|2 Kings|1 Chronicles|2 Chronicles|Ezra|Nehemiah|Esther|Job|Psalms?|Proverbs|Ecclesiastes|Song of Songs|Isaiah|Jeremiah|Lamentations|Ezekiel|Daniel|Hosea|Joel|Amos|Obadiah|Jonah|Micah|Nahum|Habakkuk|Zephaniah|Haggai|Zechariah|Malachi|Matthew|Mark|Luke|John|Acts|Romans|1 Corinthians|2 Corinthians|Galatians|Ephesians|Philippians|Colossians|1 Thessalonians|2 Thessalonians|1 Timothy|2 Timothy|Titus|Philemon|Hebrews|James|1 Peter|2 Peter|1 John|2 John|3 John|Jude|Revelation)\s+\d+:\d+""", RegexOption.IGNORE_CASE)

    fun loadChats() {
        val chats = StorageHelper.loadChats()
        _state.value = _state.value.copy(chats = chats)
    }

    fun setCurrentRef(ref: String) {
        currentRef = ref
    }

    fun startNewChat(
        currentRef: String = "",
        title: String = "New Chat",
        type: String = "general",
        context: String = ""
    ) {
        // Keep currentRef for later use, but DO NOT force type = bible
        // Type stays general. currentRef is lowest priority fallback,
        // but still callable when user refers to it.
        this.currentRef = currentRef
        val newChat = Chat(
            id = UUID.randomUUID().toString(),
            title = title,
            type = type,
            context = context
        )
        currentChatId = newChat.id
        currentChat = newChat
        isNewChat = true
        val chats = StorageHelper.loadChats().toMutableList()
        chats.add(0, newChat)
        StorageHelper.saveChats(chats)
        _state.value = _state.value.copy(
            messages = emptyList(),
            chats = chats,
            currentChat = newChat,
            isTyping = false
        )
    }

    fun openChat(chat: Chat) {
        currentChatId = chat.id
        currentChat = chat
        isNewChat = false
        val messages = StorageHelper.loadMessages(chat.id)
        _state.value = _state.value.copy(messages = messages, currentChat = chat)
    }

    fun openChatFromHistory(chat: Chat) {
        currentChatId = chat.id
        currentChat = chat
        isNewChat = false
        val messages = StorageHelper.loadMessages(chat.id)
        _state.value = _state.value.copy(messages = messages, currentChat = chat)
    }

    fun clearError() { _errorMessage.value = null }

    fun deleteChat(chatId: String) {
        StorageHelper.deleteChat(chatId)
        if (chatId == currentChatId) {
            currentChatId = ""
            currentChat = null
            _state.value = _state.value.copy(messages = emptyList(), currentChat = null)
        }
        loadChats()
    }

    fun deleteAllChats() {
        StorageHelper.deleteAllChats()
        currentChatId = ""
        currentChat = null
        _state.value = _state.value.copy(messages = emptyList(), currentChat = null, chats = emptyList())
    }

    fun clearCurrentChat() {
        currentChatId = ""
        currentChat = null
        _state.value = _state.value.copy(messages = emptyList(), currentChat = null)
    }

    fun sendMessage(
        message: String,
        context: String = "",
        type: String = "general",
        imageFile: File? = null
    ) {
        if (message.isBlank()) return
        sendJob?.cancel()
        _state.value = _state.value.copy(isTyping = true)
        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            chatId = currentChatId,
            text = message,
            isUser = true,
            timestamp = System.currentTimeMillis()
        )
        val updated = _state.value.messages + userMsg
        _state.value = _state.value.copy(messages = updated)
        StorageHelper.saveMessages(currentChatId, updated)
        if (isNewChat) updateChatTitle(currentChatId, message)

        sendJob = viewModelScope.launch {
            try {
                val now = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                // --- PRIORITY LOGIC ---
                // 1. Explicit verse > 2. News > 3. General with topic > 4. currentRef (lowest, but callable)
                val bibleContext = getBibleContext(message, context)
                val newsContext = if (isNewsQuestion(message)) getNewsContext(message) else ""
                val generalContext = getGeneralContext(message)

                val messagesList = mutableListOf<Message>()
                messagesList.add(
                    Message(
                        "system",
                        "You are a helpful assistant. Today is $now. " +
                        "Answer ONLY in the language the user uses. " +
                        "If newsContext is not empty, answer ONLY from newsContext. " +
                        "If bibleContext is not empty, answer ONLY from bibleContext as Bible expert. " +
                        "If both are empty, use generalContext / your own knowledge. " +
                        "BIBLE CONTEXT: $bibleContext " +
                        "NEWS CONTEXT: $newsContext " +
                        "GENERAL CONTEXT: $generalContext"
                    )
                )
                messagesList.add(Message("user", message))

                val key = "Bearer " + BuildConfig.OPENROUTER_API_KEY
                val req = ChatRequest(
                    model = "openai/gpt-4o-mini",
                    messages = messagesList,
                    temperature = 0.7
                )
                val res = api.sendMessage(key, req)
                val botText = res.choices.firstOrNull()?.message?.content ?: "No response"
                val botMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    chatId = currentChatId,
                    text = botText,
                    isUser = false,
                    timestamp = System.currentTimeMillis()
                )
                val finalList = _state.value.messages + botMsg
                _state.value = _state.value.copy(messages = finalList, isTyping = false)
                StorageHelper.saveMessages(currentChatId, finalList)
            } catch (e: Exception) {
                val botMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    chatId = currentChatId,
                    text = "Error: ${e.message}",
                    isUser = false,
                    timestamp = System.currentTimeMillis()
                )
                val finalList = _state.value.messages + botMsg
                _state.value = _state.value.copy(messages = finalList, isTyping = false)
                StorageHelper.saveMessages(currentChatId, finalList)
            }
        }
    }

    // --- CORE FIX ---
    private fun getBibleContext(question: String, context: String): String {
        // 1. HIGHEST: Explicit verse typed in question -> always wins
        if (VERSE_REGEX.containsMatchIn(question)) {
            return buildBibleContext(question, currentRef)
        }
        // 2. News wins over currentRef
        if (isNewsQuestion(question)) return ""
        // 3. General question with its own topic wins over currentRef
        if (isGeneralQuestionWithTopic(question)) return ""
        // 4. LOWEST: currentRef - only if question refers to it
        // e.g. "explain this", "what does this mean?", "summarize this chapter"
        // This is the main purpose of the app, so keep it callable
        if (shouldUseCurrentRef(question)) {
            return buildBibleContext(question, currentRef)
        }
        // 5. If context passed from old Bible chat, use it
        if (context.isNotBlank()) return context
        return ""
    }

    // FIXED: Only true when user actually refers to current passage
    // Removed generic "this" / "it" / length < 30 - that matched everything
    private fun shouldUseCurrentRef(question: String): Boolean {
        if (currentRef.isBlank()) return false
        val q = question.lowercase().trim()
        return q.contains("this verse") || q.contains("this chapter") ||
               q.contains("this passage") || q.contains("this psalm") ||
               q.contains("this scripture") || q.contains("this book") ||
               q.contains("explain this") || q.contains("summarize this") ||
               q.contains("what does this mean") || q.contains("what does it mean") ||
               q.contains("explain it") || q.contains("meaning of this") ||
               q == "explain" || q == "explain?" || q == "summarize" ||
               q == "what does this mean?" || q == "what is this about?"
    }

    // FIXED: Check shouldUseCurrentRef first, so Bible can still win when referred to
    private fun isGeneralQuestionWithTopic(question: String): Boolean {
        val q = question.lowercase()
        if (shouldUseCurrentRef(question)) return false
        return q.startsWith("who is") || q.startsWith("what is") ||
               q.startsWith("tell me about") || q.startsWith("who was") ||
               q.contains("elon musk") || q.contains("america") ||
               q.contains("malawi") || q.contains("football")
    }

    private fun isNewsQuestion(q: String): Boolean {
        val lower = q.lowercase()
        return lower.contains("news") || lower.contains("latest") ||
               lower.contains("breaking") || lower.contains("headline") ||
               lower.contains("today") || lower.contains("happening")
    }

    private fun buildBibleContext(question: String, currentRef: String): String {
        val verseMatch = VERSE_REGEX.find(question)
        val refToUse = verseMatch?.value ?: currentRef.ifBlank { "" }
        if (refToUse.isBlank()) return ""
        return try {
            val verseText = BibleData.getVerse(refToUse) ?: ""
            val summary = BibleData.getSummary(refToUse) ?: ""
            "BIBLE PASSAGE: $refToUse\nVERSE TEXT: $verseText\nSUMMARY: $summary\nUser is reading $refToUse. Explain it in simple, clear language."
        } catch (e: Exception) { "" }
    }

    private fun getNewsContext(question: String): String {
        return try {
            val query = when {
                question.contains("malawi", true) -> "Malawi"
                question.contains("america", true) || question.contains("usa", true) || question.contains("us ", true) -> "United States"
                question.contains("world", true) -> "world"
                else -> "world"
            }
            val res = kotlinx.coroutines.runBlocking { api.getNews(query, BuildConfig.NEWS_API_KEY) }
            val articles = res.articles.take(5)
            if (articles.isEmpty()) return ""
            articles.joinToString("\n") { "- ${it.title}: ${it.description ?: ""}" }
        } catch (e: Exception) { "" }
    }

    private fun getGeneralContext(question: String): String {
        return "General question: $question"
    }

    private fun updateChatTitle(chatId: String, firstMessage: String) {
        val chats = StorageHelper.loadChats().toMutableList()
        val index = chats.indexOfFirst { it.id == chatId }
        if (index != -1) {
            val title = firstMessage.take(40)
            chats[index] = chats[index].copy(title = title)
            StorageHelper.saveChats(chats)
            _state.value = _state.value.copy(chats = chats)
        }
    }
}