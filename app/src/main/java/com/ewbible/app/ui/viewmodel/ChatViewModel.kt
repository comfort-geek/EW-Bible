package com.ewbible.app.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ewbible.app.data.ai.AiRepository
import com.ewbible.app.data.database.BibleDao
import com.ewbible.app.data.database.BookCatalog
import com.ewbible.app.data.database.ChatEntity
import com.ewbible.app.data.database.CommentaryHelper
import com.ewbible.app.data.database.MessageEntity
import com.ewbible.app.data.sync.CloudMessage
import com.ewbible.app.data.sync.SyncManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatState(
    val chats: List<ChatEntity> = emptyList(),
    val messages: List<MessageEntity> = emptyList(),
    val currentChatId: Long? = null,
    val currentChatType: String = "general",
    val isLoading: Boolean = false,
    val streamingText: String = "",
    val dailyCount: Int = 0,
    val error: String? = null,
    val aiProvider: String = "Gemini",
    val chatsLoaded: Boolean = false
)

class ChatViewModel(
    private val bibleDao: BibleDao,
    private val geminiRepository: AiRepository,
    private val syncManager: SyncManager? = null
) : ViewModel() {

    private val VERSE_REGEX = Regex("""((?:[1-3]\s+)?[A-Za-z]+(?:\s+[A-Za-z]+){0,2})\s+(\d+):(\d+)""")
    private val NUM_DOT_REGEX = Regex("^\\d+\\.\\s*")
    private val NUM_PAREN_REGEX = Regex("^\\d+\\)\\s*")
    private val SOURCE_BOLD_REGEX = Regex("^\\*\\*Source\\s*\\d*\\s*[-:.]?\\s*\\*\\*\\s*")
    private val SOURCE_DASH_REGEX = Regex("^Source\\s*\\d*\\s*[-:.]\\s*")
    private val SOURCE_PLAIN_REGEX = Regex("^Source\\s*\\d*\\s*")

    private val contextCache = object : LinkedHashMap<String, String>(20, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, String>?) = size > 20
    }

    private val chatTypes = mutableMapOf<Long, String>()
    private val chatContexts = mutableMapOf<Long, String>()

    private var sendJob: Job? = null
    private var lastStreamEmit = 0L
    private var chatsLoading = false

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            geminiRepository.lastProvider.collect { provider ->
                _state.update { it.copy(aiProvider = provider) }
            }
        }
    }

    fun refreshModels(context: Context) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                geminiRepository.refreshModels(context)
            }
        }
    }

    private suspend fun getGlobalTodayCount(): Int =
        runCatching {
            withContext(Dispatchers.IO) { bibleDao.getTodayUserMessageCountGlobal() }
        }.getOrDefault(0)

    private fun isStillCurrent(chatId: Long): Boolean {
        return _state.value.currentChatId == chatId
    }

    fun loadChats(force: Boolean = false) {
        if (chatsLoading) return
        if (_state.value.chatsLoaded && !force) return
        chatsLoading = true
        viewModelScope.launch {
            try {
                val chats = withContext(Dispatchers.IO) { bibleDao.getAllChatsSync() }
                _state.update { it.copy(chats = chats, chatsLoaded = true) }
                val cloud = withContext(Dispatchers.IO) { syncManager?.getChatList()?.getOrNull() } ?: return@launch
                for (c in cloud) {
                    val localId = c.chatId.toLongOrNull() ?: continue
                    chatTypes[localId] = c.type.ifBlank { "general" }
                    chatContexts[localId] = c.context.ifBlank { c.bibleRef }
                }
                _state.value.currentChatId?.let { id ->
                    _state.update { it.copy(currentChatType = chatTypes[id] ?: it.currentChatType) }
                }
            } finally {
                chatsLoading = false
            }
        }
    }

    fun startNewChat(title: String = "New Chat", type: String = "general", context: String = "", currentRef: String = "") {
        sendJob?.cancel()
        sendJob = null
        viewModelScope.launch {
            val ref = if (type == "bible") context.ifBlank { currentRef } else ""
            val id = withContext(Dispatchers.IO) {
                val chat = ChatEntity(title = title, ref = ref)
                bibleDao.insertChat(chat)
            }
            chatTypes[id] = type
            chatContexts[id] = context.ifBlank { ref }
            _state.update {
                it.copy(
                    currentChatId = id,
                    currentChatType = type,
                    messages = emptyList(),
                    streamingText = "",
                    isLoading = false,
                    chats = listOf(ChatEntity(id = id, title = title, ref = ref)) + it.chats
                )
            }
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    syncManager?.createChat(
                        chatId = id.toString(),
                        title = title,
                        type = type,
                        context = context.ifBlank { ref },
                        bibleRef = if (type == "bible") ref else "",
                        translation = ""
                    )
                }
            }
        }
    }

    fun startNewChat(currentRef: String) {
        val type = if (currentRef.isBlank()) "general" else "bible"
        startNewChat(title = "New Chat", type = type, context = currentRef, currentRef = currentRef)
    }

    fun openChat(chatId: Long) {
        if (_state.value.currentChatId == chatId && _state.value.messages.isNotEmpty()) return
        if (_state.value.currentChatId != null && _state.value.currentChatId != chatId) {
            sendJob?.cancel()
            sendJob = null
        }
        viewModelScope.launch {
            val messages = withContext(Dispatchers.IO) { bibleDao.getMessagesForChatSync(chatId) }
            val todayCount = getGlobalTodayCount()
            val local = withContext(Dispatchers.IO) {
                _state.value.chats.find { it.id == chatId } ?: bibleDao.getChatById(chatId)
            }
            val type = chatTypes[chatId]
                ?: local?.let { if (it.ref.isBlank()) "general" else "bible" }
                ?: "general"
            if (messages.isNotEmpty()) {
                _state.update {
                    it.copy(
                        currentChatId = chatId,
                        currentChatType = type,
                        messages = messages,
                        streamingText = "",
                        isLoading = false,
                        dailyCount = todayCount
                    )
                }
                return@launch
            }
            _state.update {
                it.copy(
                    currentChatId = chatId,
                    currentChatType = type,
                    messages = emptyList(),
                    streamingText = "",
                    isLoading = false,
                    dailyCount = todayCount
                )
            }
            val cloudMsgs = withContext(Dispatchers.IO) { syncManager?.getMessages(chatId.toString())?.getOrNull() } ?: return@launch
            if (cloudMsgs.isNotEmpty()) {
                val restored = withContext(Dispatchers.IO) {
                    val list = mutableListOf<MessageEntity>()
                    for (cm in cloudMsgs) {
                        val m = MessageEntity(chatId = chatId, text = cm.text, isUser = cm.role == "user", timestamp = cm.timestamp)
                        val newId = bibleDao.insertMessage(m)
                        list.add(m.copy(id = newId))
                    }
                    list
                }
                if (isStillCurrent(chatId)) {
                    _state.update { it.copy(messages = restored) }
                }
            }
        }
    }

    fun deleteChat(chatId: Long) {
        if (_state.value.currentChatId == chatId) {
            sendJob?.cancel()
            sendJob = null
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                bibleDao.deleteMessagesForChat(chatId)
                bibleDao.deleteChat(chatId)
            }
            chatTypes.remove(chatId)
            chatContexts.remove(chatId)
            _state.update {
                if (it.currentChatId == chatId) it.copy(
                    currentChatId = null,
                    messages = emptyList(),
                    streamingText = "",
                    isLoading = false,
                    chats = it.chats.filterNot { c -> c.id == chatId }
                )
                else it.copy(chats = it.chats.filterNot { c -> c.id == chatId })
            }
            viewModelScope.launch(Dispatchers.IO) { runCatching { syncManager?.deleteChat(chatId.toString()) } }
        }
    }

    fun renameChat(chatId: Long, newTitle: String) {
        viewModelScope.launch {
            val chat = withContext(Dispatchers.IO) {
                _state.value.chats.find { it.id == chatId } ?: bibleDao.getChatById(chatId)
            } ?: return@launch
            val updated = chat.copy(title = newTitle)
            withContext(Dispatchers.IO) { bibleDao.updateChat(updated) }
            _state.update { it.copy(chats = it.chats.map { c -> if (c.id == chatId) updated else c }) }
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    syncManager?.createChat(
                        chatId = chatId.toString(),
                        title = newTitle,
                        type = chatTypes[chatId] ?: "general",
                        context = chatContexts[chatId] ?: chat.ref,
                        bibleRef = if ((chatTypes[chatId] ?: "general") == "bible") chat.ref else "",
                        translation = ""
                    )
                }
            }
        }
    }

    fun send(question: String, currentRef: String, image: Bitmap?, translation: String = "NKJV") {
        val chatId = _state.value.currentChatId ?: return
        sendJob?.cancel()
        sendJob = viewModelScope.launch {
            val todayCount = getGlobalTodayCount()
            doSend(chatId, question, currentRef, image, translation, todayCount)
        }
    }

    fun stopStreaming() {
        sendJob?.cancel()
        sendJob = null
    }

    private suspend fun doSend(chatId: Long, question: String, currentRef: String, image: Bitmap?, translation: String, todayCount: Int) {
        val historySnapshot = _state.value.messages
        val chatType = _state.value.currentChatType
        val chatContext = chatContexts[chatId] ?: currentRef

        val userMsg = MessageEntity(chatId = chatId, text = question, isUser = true)
        val userId = withContext(Dispatchers.IO) { bibleDao.insertMessage(userMsg) }
        if (isStillCurrent(chatId)) {
            _state.update {
                it.copy(messages = it.messages + userMsg.copy(id = userId), isLoading = true, streamingText = "", error = null, dailyCount = todayCount)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                syncManager?.uploadMessage(
                    chatId.toString(),
                    CloudMessage(
                        messageId = userId.toString(),
                        chatId = chatId.toString(),
                        role = "user",
                        type = if (image != null) "image" else "text",
                        text = question,
                        context = chatContext,
                        bibleRef = if (chatType == "bible") chatContext else "",
                        imageUrl = "",
                        timestamp = userMsg.timestamp
                    )
                )
            }
        }

        val bibleContext = withContext(Dispatchers.IO) {
            if (chatType == "bible" || chatType == "study" || chatType == "prayer") {
                buildBibleContext(question, currentRef)
            } else ""
        }

        val fullAnswer = StringBuilder(2000)
        lastStreamEmit = System.currentTimeMillis()
        try {
            geminiRepository.askStreaming(
                question = question,
                currentRef = currentRef,
                history = historySnapshot,
                image = image,
                bibleContext = bibleContext,
                translation = translation
            ).collect { chunk ->
                fullAnswer.append(chunk)
                if (!isStillCurrent(chatId)) return@collect
                val now = System.currentTimeMillis()
                if (now - lastStreamEmit >= 120) {
                    lastStreamEmit = now
                    _state.update { it.copy(streamingText = fullAnswer.toString()) }
                }
            }
            if (isStillCurrent(chatId)) {
                _state.update { it.copy(streamingText = fullAnswer.toString()) }
            }
        } catch (e: CancellationException) {
            if (fullAnswer.isNotBlank()) {
                val partialText = cleanMarkdown(fullAnswer.toString())
                val partialMsg = MessageEntity(chatId = chatId, text = partialText, isUser = false)
                val partialId = withContext(Dispatchers.IO) { bibleDao.insertMessage(partialMsg) }
                if (isStillCurrent(chatId)) {
                    _state.update {
                        it.copy(
                            messages = it.messages + partialMsg.copy(id = partialId),
                            isLoading = false,
                            streamingText = ""
                        )
                    }
                }
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching {
                        syncManager?.uploadMessage(
                            chatId.toString(),
                            CloudMessage(
                                messageId = partialId.toString(),
                                chatId = chatId.toString(),
                                role = "ai",
                                type = "text",
                                text = partialText,
                                context = chatContext,
                                bibleRef = if (chatType == "bible") chatContext else "",
                                imageUrl = "",
                                timestamp = partialMsg.timestamp
                            )
                        )
                    }
                }
            } else {
                if (isStillCurrent(chatId)) {
                    _state.update { it.copy(isLoading = false, streamingText = "") }
                }
            }
            throw e
        } catch (e: Exception) {
            val msg = e.message ?: ""
            val friendly = if (msg.contains("429") || msg.contains("quota", ignoreCase = true) || msg.contains("Rate limit", ignoreCase = true) || msg.contains("rate limit", ignoreCase = true)) {
                "Free AI limit reached for today. Please try again tomorrow - it resets at midnight."
            } else {
                "Failed: ${e.message}"
            }
            if (isStillCurrent(chatId)) {
                _state.update { it.copy(isLoading = false, streamingText = "", error = friendly) }
            }
            return
        }

        val answerText = cleanMarkdown(fullAnswer.toString()).ifBlank { "Sorry, I couldn't answer that." }
        val aiMsg = MessageEntity(chatId = chatId, text = answerText, isUser = false)
        val aiId = withContext(Dispatchers.IO) { bibleDao.insertMessage(aiMsg) }
        if (isStillCurrent(chatId)) {
            _state.update {
                it.copy(
                    messages = it.messages + aiMsg.copy(id = aiId),
                    isLoading = false,
                    streamingText = "",
                    dailyCount = todayCount + 1
                )
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                syncManager?.uploadMessage(
                    chatId.toString(),
                    CloudMessage(
                        messageId = aiId.toString(),
                        chatId = chatId.toString(),
                        role = "ai",
                        type = "text",
                        text = answerText,
                        context = chatContext,
                        bibleRef = if (chatType == "bible") chatContext else "",
                        imageUrl = "",
                        timestamp = aiMsg.timestamp
                    )
                )
            }
        }
    }

    private suspend fun buildBibleContext(question: String, currentRef: String): String {
        return try {
            val match = VERSE_REGEX.find(question)
            if (match != null) {
                val bookName = match.groupValues[1].trim()
                val chapter = match.groupValues[2].toIntOrNull() ?: 1
                val verse = match.groupValues[3].toIntOrNull() ?: 1
                val cacheKey = "v:$bookName-$chapter:$verse"
                contextCache[cacheKey]?.let { return it }
                val bookId = BookCatalog.id(bookName)
                val text = bibleDao.getVerse(bookId, chapter, verse)?.text ?: ""
                val summary = bibleDao.getSummary(bookId, chapter)?.summary ?: ""
                val result = "USER ASKED ABOUT $bookName $chapter:$verse\nNKJV TEXT: $text\nSUMMARY: $summary\n"
                contextCache[cacheKey] = result
                return result
            }
            val cacheKey = "c:$currentRef"
            contextCache[cacheKey]?.let { return it }
            val parts = currentRef.trim().split(" ")
            val chapter = parts.lastOrNull()?.toIntOrNull() ?: 1
            val bookName = parts.dropLast(1).joinToString(" ")
            val bookId = BookCatalog.id(bookName)
            val chapterText = bibleDao.getVerseRange(bookId, chapter, 1, 30)
                .joinToString("\n") { "${it.verse}. ${it.text}" }
            val summary = bibleDao.getSummary(bookId, chapter)?.summary ?: ""
            val compressed = bibleDao.getCommentaryCompressed(bookId, chapter)
            val commentary = runCatching {
                compressed?.let { CommentaryHelper.decompress(it) } ?: ""
            }.getOrDefault("").take(3000)
            val result = "USER IS READING $bookName $chapter (NKJV):\n$chapterText\n\nSUMMARY: $summary\nCOMMENTARY: $commentary\n"
            contextCache[cacheKey] = result
            result
        } catch (e: Exception) {
            ""
        }
    }

    // FIXED: Strip single * completely, keep **bold**
    private fun cleanMarkdown(text: String): String {
        var cleaned = text
            .replace("**Bold Title:**", "")
            .replace("**Bold Title**", "")
            .replace("Bold Title:", "")
            .replace("Bold Title", "")
            .replace("### ", "").replace("## ", "").replace("# ", "").replace("---", "")
            .replace("`", "")

        // 1. Protect **bold** so we don't kill it
        val bolds = mutableListOf<String>()
        cleaned = Regex("\\*\\*(.+?)\\*\\*").replace(cleaned) { m ->
            bolds.add(m.groupValues[1])
            "\u0000BOLD${bolds.size - 1}\u0000"
        }

        // 2. Strip ALL leftover single * and _italic_
        // *"quote"* -> "quote", *italic* -> italic
        cleaned = cleaned.replace("*", "")
        cleaned = cleaned.replace(Regex("_(.+?)_"), "$1")
        cleaned = cleaned.replace("__", "")

        // 3. Restore **bold**
        bolds.forEachIndexed { i, b ->
            cleaned = cleaned.replace("\u0000BOLD$i\u0000", "**$b**")
        }

        return cleaned.lines().joinToString("\n") { line ->
            var l = line.trimStart()
            l = l.replace(NUM_DOT_REGEX, "")
            l = l.replace(NUM_PAREN_REGEX, "")
            l = l.replace(SOURCE_BOLD_REGEX, "")
            l = l.replace(SOURCE_DASH_REGEX, "")
            l = l.replace(SOURCE_PLAIN_REGEX, "")
            l = l.removePrefix("- ").removePrefix("• ").removePrefix("> ")
            l = l.replace(SOURCE_BOLD_REGEX, "")
            l = l.replace(SOURCE_DASH_REGEX, "")
            l
        }.trim()
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}