package com.ewbible.app.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ewbible.app.data.database.BibleDao
import com.ewbible.app.data.database.ChatDao
import com.ewbible.app.data.database.ChatEntity
import com.ewbible.app.data.database.CommentaryHelper
import com.ewbible.app.data.repository.AiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatMessage(
    val id: String = System.currentTimeMillis().toString() + "_" + (0..99999).random(),
    val text: String,
    val isUser: Boolean
)

data class ChatItem(
    val id: String,
    val title: String,
    val ref: String
)

data class ChatState(
    val chats: List<ChatItem> = emptyList(),
    val chatsLoaded: Boolean = false,
    val currentChatId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val streamingText: String = "",
    val currentBibleRef: String = "",
    val currentTranslation: String = "NKJV", // AI chat translation only, NOT Bible tab
    val availableModels: List<String> = emptyList()
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatDao: ChatDao,
    private val bibleDao: BibleDao,
    private val aiRepository: AiRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    // Bible tab: NKJV only. Only gives us ref, e.g. "Matthew 26"
    private var bibleRef: String = ""
    // AiChatScreen: gives us translation for AI, e.g. "NIV"
    private var chatTranslation: String = "NKJV"

    // --- Called from Bible tab / AiChatScreen when ref changes ---
    // Bible tab has ONLY NKJV, so we do NOT touch translation here
    fun setBibleRef(ref: String) {
        val cleanRef = ref.trim()
        if (cleanRef == bibleRef) return
        bibleRef = cleanRef
        _state.update { it.copy(currentBibleRef = cleanRef) }
        saveRefToCurrentChat(cleanRef, chatTranslation)
    }

    // --- Called ONLY from AiChatScreen translation picker ---
    // Bible tab does NOT call this. Bible tab stays NKJV.
    fun setChatTranslation(translation: String) {
        val cleanTrans = translation.ifBlank { "NKJV" }.uppercase()
        if (cleanTrans == chatTranslation) return
        chatTranslation = cleanTrans
        _state.update { it.copy(currentTranslation = cleanTrans) }
        saveRefToCurrentChat(bibleRef, cleanTrans)
    }

    // Kept for old calls: ref = from Bible tab, translation = from AiChatScreen
    fun setScreenContext(ref: String, translation: String) {
        setBibleRef(ref)
        // Only update translation if caller is AiChatScreen and it actually passed one
        // Bible tab callers should pass "" or "NKJV" - we ignore it to keep Bible tab NKJV-only logic clean
        if (translation.isNotBlank()) {
            setChatTranslation(translation)
        }
    }

    private fun saveRefToCurrentChat(ref: String, translation: String) {
        val chatId = _state.value.currentChatId ?: return
        viewModelScope.launch {
            runCatching {
                val existing = chatDao.getChatById(chatId) ?: return@launch
                chatDao.updateChat(existing.copy(ref = ref, translation = translation))
            }
        }
    }

    fun refreshModels(context: Context) {
        viewModelScope.launch {
            val models = runCatching { aiRepository.getAvailableModels(context) }.getOrDefault(emptyList())
            _state.update { it.copy(availableModels = models) }
        }
    }

    fun loadChats() {
        viewModelScope.launch {
            val entities = runCatching { chatDao.getAllChats() }.getOrDefault(emptyList())
            val items = entities.map { ChatItem(it.id, it.title, it.ref) }
            _state.update { it.copy(chats = items, chatsLoaded = true) }
        }
    }

    fun startNewChat(ref: String, translation: String = chatTranslation) {
        val cleanRef = ref.trim()
        val cleanTrans = translation.ifBlank { "NKJV" }.uppercase()
        bibleRef = cleanRef
        chatTranslation = cleanTrans

        viewModelScope.launch {
            val id = System.currentTimeMillis().toString()
            val entity = ChatEntity(
                id = id,
                title = if (cleanRef.isNotBlank()) cleanRef else "New Chat",
                ref = cleanRef,
                translation = cleanTrans,
                createdAt = System.currentTimeMillis()
            )
            runCatching { chatDao.insertChat(entity) }
            _state.update {
                it.copy(
                    currentChatId = id,
                    messages = emptyList(),
                    streamingText = "",
                    isLoading = false,
                    currentBibleRef = cleanRef,
                    currentTranslation = cleanTrans
                )
            }
            loadChats()
        }
    }

    fun openChat(chatId: String) {
        viewModelScope.launch {
            val entity = runCatching { chatDao.getChatById(chatId) }.getOrNull() ?: return@launch
            val msgs = runCatching { chatDao.getMessages(chatId) }.getOrDefault(emptyList())
            // Restore: ref came from Bible tab, translation came from AiChatScreen
            bibleRef = entity.ref
            chatTranslation = entity.translation.ifBlank { "NKJV" }
            _state.update {
                it.copy(
                    currentChatId = chatId,
                    messages = msgs.map { m -> ChatMessage(m.id, m.text, m.isUser) },
                    currentBibleRef = entity.ref,
                    currentTranslation = chatTranslation,
                    isLoading = false,
                    streamingText = ""
                )
            }
        }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            runCatching {
                chatDao.deleteMessagesForChat(chatId)
                chatDao.deleteChat(chatId)
            }
            if (_state.value.currentChatId == chatId) {
                _state.update { it.copy(currentChatId = null, messages = emptyList()) }
            }
            loadChats()
        }
    }

    fun send(question: String, ref: String, image: Bitmap?, translation: String) {
        val q = question.trim()
        if (q.isBlank()) return
        // ref = from Bible tab (NKJV only). translation = from AiChatScreen picker.
        val useRef = ref.ifBlank { bibleRef }
        val useTranslation = translation.ifBlank { chatTranslation }.uppercase()

        // Update both separately - do not let Bible tab overwrite translation
        if (useRef.isNotBlank()) setBibleRef(useRef)
        setChatTranslation(useTranslation)

        val chatId = _state.value.currentChatId ?: run {
            startNewChat(useRef, useTranslation)
            return
        }

        val userMsg = ChatMessage(text = q, isUser = true)
        _state.update { it.copy(messages = it.messages + userMsg, isLoading = true, streamingText = "") }

        viewModelScope.launch {
            runCatching { chatDao.insertMessage(chatId, q, true) }

            val type = detectType(q, useRef)
            val bibleContext = if (type == ChatType.BIBLE || type == ChatType.BIBLE_WITH_REF) {
                buildBibleContext(useRef, q, useTranslation)
            } else ""

            val systemPrompt = buildSystemPrompt(type, useTranslation)

            val fullPrompt = buildString {
                if (bibleContext.isNotBlank()) {
                    append(bibleContext)
                    append("\n\n")
                }
                append("USER QUESTION: ").append(q)
            }

            var aiText = ""
            try {
                aiRepository.sendStream(
                    systemPrompt = systemPrompt,
                    prompt = fullPrompt,
                    image = image,
                    onChunk = { chunk ->
                        aiText += chunk
                        _state.update { it.copy(streamingText = aiText) }
                    }
                )
            } catch (e: Exception) {
                aiText = aiText.ifBlank { "Sorry, I couldn't get an answer. Please try again." }
            }

            val aiMsg = ChatMessage(text = aiText, isUser = false)
            _state.update { it.copy(messages = it.messages + aiMsg, isLoading = false, streamingText = "") }
            runCatching { chatDao.insertMessage(chatId, aiText, false) }

            runCatching {
                val existing = chatDao.getChatById(chatId) ?: return@runCatching
                if (existing.title == "New Chat" || existing.title.isBlank()) {
                    chatDao.updateChat(existing.copy(title = q.take(50)))
                }
            }
            loadChats()
        }
    }

    // ---------------- TYPES ----------------

    private enum class ChatType { GENERAL, NEWS, BIBLE, BIBLE_WITH_REF }

    private fun detectType(q: String, ref: String): ChatType {
        val lower = q.lowercase()
        if (lower.contains("news") || lower.contains("today") || lower.contains("latest") ||
            lower.contains("what happened") || lower.contains("breaking")) return ChatType.NEWS

        if (Regex("""\b(1|2|3)?\s?[a-z]+\s+\d+:\d+""", RegexOption.IGNORE_CASE).containsMatchIn(q)) {
            return ChatType.BIBLE_WITH_REF
        }
        if (lower.contains("explain this") || lower.contains("what does this mean") ||
            lower.contains("this verse") || lower.contains("this chapter") ||
            lower.contains("this passage")) return ChatType.BIBLE

        val bibleWords = listOf("bible", "verse", "scripture", "god", "jesus", "psalm", "proverb", "gospel", "apostle", "pray")
        if (bibleWords.any { lower.contains(it) }) return ChatType.BIBLE

        if (ref.isNotBlank() && (lower.contains("explain") || lower.contains("meaning") || lower.length < 30)) {
            return ChatType.BIBLE
        }
        return ChatType.GENERAL
    }

    private fun buildSystemPrompt(type: ChatType, requestedTranslation: String): String {
        return when (type) {
            ChatType.BIBLE, ChatType.BIBLE_WITH_REF -> """
                You are a Bible study assistant in the EWBible app.
                Answer from the passage provided below.
                User requested translation for AI answer: $requestedTranslation.
                You know ALL translations (NKJV, KJV, NIV, ESV, CSB).
                The verses below are NKJV because Bible tab / local DB only has NKJV.
                If user asked for $requestedTranslation, answer/quote in $requestedTranslation,
                but keep meaning identical to the NKJV text below. Do not invent verse text.
                Use the commentary/summary only as background, not as your main answer.
                At the end, add Sources as markdown links if you used web knowledge.
            """.trimIndent()
            ChatType.NEWS -> """
                You are a news assistant. Answer the current news question.
                Do NOT force a Bible answer. Only mention Bible if user asked.
            """.trimIndent()
            ChatType.GENERAL -> """
                You are a helpful general assistant in the EWBible app.
                Answer anything: general knowledge, science, history, daily life, etc.
                Do NOT force a Bible answer unless user asked for it.
            """.trimIndent()
        }
    }

    // ---------------- BIBLE CONTEXT - NKJV DB + AI TRANSLATIONS ----------------

    private suspend fun buildBibleContext(ref: String, question: String, requestedTranslation: String): String {
        val targetRef = extractVerseRef(question) ?: ref.ifBlank { bibleRef }
        if (targetRef.isBlank()) return ""

        val parsed = parseRef(targetRef) ?: return ""
        val (bookId, bookName, chapter, verseFrom, verseTo) = parsed

        return runCatching {
            // ALWAYS NKJV from local DB - Bible tab only has NKJV
            val verses = if (verseFrom != null) {
                bibleDao.getVerseRange(bookId, chapter, verseFrom, verseTo ?: verseFrom)
            } else {
                bibleDao.getVerseRange(bookId, chapter, 1, 200)
            }.joinToString("\n") { "${it.verse}: ${it.text}" }

            val summary = runCatching { bibleDao.getSummary(bookId, chapter) }.getOrNull() ?: ""

            val compressed = runCatching { bibleDao.getCommentaryCompressed(bookId, chapter) }.getOrNull()
            val commentaryHtml = if (compressed != null) {
                CommentaryHelper.getHtmlCommentary(bookId, chapter, compressed)
            } else ""
            val commentaryPlain = stripHtml(commentaryHtml).take(2500)

            val translationInstruction = if (requestedTranslation == "NKJV") {
                "Translation: NKJV. Quote verses exactly from the text below."
            } else {
                "User requested translation for AI answer: $requestedTranslation. " +
                "Verses below are NKJV (Bible tab / local DB only has NKJV) for grounding. " +
                "You already know $requestedTranslation. " +
                "Answer/quote in $requestedTranslation, but do NOT claim NKJV text below is $requestedTranslation. " +
                "Keep meaning identical to NKJV text below."
            }

            """
            $translationInstruction

            PASSAGE: $bookName $chapter${if (verseFrom != null) ":$verseFrom${if (verseTo != null && verseTo != verseFrom) "-$verseTo" else ""}" else ""}
            VERSES (NKJV, from local DB):
            $verses

            SUMMARY: $summary
            COMMENTARY (plain text, from zlib compressed DB):
            $commentaryPlain
            """.trimIndent()
        }.getOrDefault("")
    }

    private fun stripHtml(html: String): String {
        if (html.isBlank()) return ""
        var t = html
            .replace(Regex("(?i)<br[^>]*>"), "\n")
            .replace(Regex("(?i)</(p|div|h[1-6]|li)>"), "\n")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
        return t.replace(Regex("\\s+"), " ").trim()
    }

    // ---------------- REF PARSING ----------------

    private data class ParsedRef(val bookId: Int, val bookName: String, val chapter: Int, val verseFrom: Int?, val verseTo: Int?)

    private fun extractVerseRef(text: String): String? {
        val m = Regex("""\b((?:1|2|3)\s+)?([A-Za-z]+)\s+(\d+)(?::(\d+)(?:-(\d+))?)?""").find(text)
        return m?.value
    }

    private fun parseRef(ref: String): ParsedRef? {
        val m = Regex("""^\s*(?:(1|2|3)\s+)?([A-Za-z]+)\s+(\d+)(?::(\d+)(?:-(\d+))?)?\s*$""").find(ref.trim()) ?: return null
        val prefix = m.groups[1]?.value
        val nameRaw = (if (prefix != null) "$prefix ${m.groups[2]?.value}" else m.groups[2]?.value) ?: return null
        val chapter = m.groups[3]?.value?.toIntOrNull() ?: return null
        val verseFrom = m.groups[4]?.value?.toIntOrNull()
        val verseTo = m.groups[5]?.value?.toIntOrNull()
        val bookId = BookCatalog.idFor(nameRaw) ?: return null
        return ParsedRef(bookId, BookCatalog.name(bookId), chapter, verseFrom, verseTo)
    }
}