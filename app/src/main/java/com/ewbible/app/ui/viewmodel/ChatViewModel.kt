package com.ewbible.app.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ewbible.app.data.database.BibleDao
import com.ewbible.app.data.database.BookCatalog
import com.ewbible.app.data.database.ChatEntity
import com.ewbible.app.data.database.CommentaryHelper
import com.ewbible.app.data.database.MessageEntity
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

data class ChatItem(val id: Long, val title: String, val ref: String)

data class ChatState(
    val chats: List<ChatItem> = emptyList(),
    val chatsLoaded: Boolean = false,
    val currentChatId: Long? = null,
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val streamingText: String = "",
    val currentBibleRef: String = "",
    val currentTranslation: String = "NKJV", // AI chat only, NOT Bible tab
    val availableModels: List<String> = emptyList()
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val bibleDao: BibleDao, // ONLY DAO - Bible + Chat are both here
    private val aiRepository: AiRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    // Bible tab: NKJV only. Only gives ref, e.g. "Matthew 26"
    private var bibleRef: String = ""
    // AiChatScreen: gives translation for AI, e.g. "NIV"
    private var chatTranslation: String = "NKJV"

    // Called from Bible tab / parent when book/chapter changes
    // Bible tab has ONLY NKJV, so we do NOT touch translation here
    fun setBibleRef(ref: String) {
        val cleanRef = ref.trim()
        if (cleanRef == bibleRef) return
        bibleRef = cleanRef
        _state.update { it.copy(currentBibleRef = cleanRef) }
        saveToCurrentChat(cleanRef, chatTranslation)
    }

    // Called ONLY from AiChatScreen translation picker
    fun setChatTranslation(translation: String) {
        val clean = translation.ifBlank { "NKJV" }.uppercase()
        if (clean == chatTranslation) return
        chatTranslation = clean
        _state.update { it.copy(currentTranslation = clean) }
        saveToCurrentChat(bibleRef, clean)
    }

    fun setScreenContext(ref: String, translation: String) {
        setBibleRef(ref)
        if (translation.isNotBlank()) setChatTranslation(translation)
    }

    private fun saveToCurrentChat(ref: String, translation: String) {
        val chatId = _state.value.currentChatId ?: return
        viewModelScope.launch {
            runCatching {
                val existing = bibleDao.getChatById(chatId) ?: return@launch
                bibleDao.updateChat(existing.copy(ref = ref, translation = translation))
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
            // Your DAO: getAllChatsSync(), not getAllChats()
            val entities = runCatching { bibleDao.getAllChatsSync() }.getOrDefault(emptyList())
            _state.update { it.copy(chats = entities.map { ChatItem(it.id, it.title, it.ref) }, chatsLoaded = true) }
        }
    }

    fun startNewChat(ref: String, translation: String = chatTranslation) {
        val cleanRef = ref.trim()
        val cleanTrans = translation.ifBlank { "NKJV" }.uppercase()
        bibleRef = cleanRef
        chatTranslation = cleanTrans
        viewModelScope.launch {
            // Your DAO: insertChat returns Long
            val id = runCatching {
                bibleDao.insertChat(
                    ChatEntity(
                        id = 0L,
                        title = if (cleanRef.isNotBlank()) cleanRef else "New Chat",
                        ref = cleanRef,
                        translation = cleanTrans,
                        createdAt = System.currentTimeMillis()
                    )
                )
            }.getOrDefault(System.currentTimeMillis())
            _state.update {
                it.copy(
                    currentChatId = id, messages = emptyList(), streamingText = "",
                    isLoading = false, currentBibleRef = cleanRef, currentTranslation = cleanTrans
                )
            }
            loadChats()
        }
    }

    fun openChat(chatId: Long) {
        viewModelScope.launch {
            // Your DAO: getChatById(Long) + getMessagesForChatSync(Long)
            val entity = runCatching { bibleDao.getChatById(chatId) }.getOrNull() ?: return@launch
            val msgs = runCatching { bibleDao.getMessagesForChatSync(chatId) }.getOrDefault(emptyList())
            bibleRef = entity.ref
            chatTranslation = entity.translation.ifBlank { "NKJV" }
            _state.update {
                it.copy(
                    currentChatId = chatId,
                    messages = msgs.map { m -> ChatMessage(m.id.toString(), m.text, m.isUser) },
                    currentBibleRef = entity.ref,
                    currentTranslation = chatTranslation,
                    isLoading = false, streamingText = ""
                )
            }
        }
    }

    fun deleteChat(chatId: Long) {
        viewModelScope.launch {
            runCatching {
                bibleDao.deleteMessagesForChat(chatId)
                bibleDao.deleteChat(chatId)
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
        val useRef = ref.ifBlank { bibleRef }
        val useTranslation = translation.ifBlank { chatTranslation }.uppercase()
        if (useRef.isNotBlank()) setBibleRef(useRef)
        setChatTranslation(useTranslation)

        val chatId = _state.value.currentChatId ?: run {
            startNewChat(useRef, useTranslation)
            return
        }

        val userMsg = ChatMessage(text = q, isUser = true)
        _state.update { it.copy(messages = it.messages + userMsg, isLoading = true, streamingText = "") }

        viewModelScope.launch {
            // Your DAO: insertMessage(MessageEntity)
            runCatching {
                bibleDao.insertMessage(
                    MessageEntity(chatId = chatId, text = q, isUser = true, timestamp = System.currentTimeMillis())
                )
            }

            val type = detectType(q, useRef)
            val bibleContext = if (type == ChatType.BIBLE || type == ChatType.BIBLE_WITH_REF) {
                buildBibleContext(useRef, q, useTranslation)
            } else ""

            val systemPrompt = buildSystemPrompt(type, useTranslation)
            val fullPrompt = buildString {
                if (bibleContext.isNotBlank()) { append(bibleContext); append("\n\n") }
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
            runCatching {
                bibleDao.insertMessage(
                    MessageEntity(chatId = chatId, text = aiText, isUser = false, timestamp = System.currentTimeMillis())
                )
            }
            runCatching {
                val existing = bibleDao.getChatById(chatId) ?: return@runCatching
                if (existing.title == "New Chat" || existing.title.isBlank()) {
                    bibleDao.updateChat(existing.copy(title = q.take(50)))
                }
            }
            loadChats()
        }
    }

    private enum class ChatType { GENERAL, NEWS, BIBLE, BIBLE_WITH_REF }

    private fun detectType(q: String, ref: String): ChatType {
        val lower = q.lowercase()
        if (lower.contains("news") || lower.contains("today") || lower.contains("latest") ||
            lower.contains("what happened") || lower.contains("breaking")) return ChatType.NEWS
        if (Regex("""\b(1|2|3)?\s?[a-z]+\s+\d+:\d+""", RegexOption.IGNORE_CASE).containsMatchIn(q)) return ChatType.BIBLE_WITH_REF
        if (lower.contains("explain this") || lower.contains("what does this mean") ||
            lower.contains("this verse") || lower.contains("this chapter") ||
            lower.contains("this passage")) return ChatType.BIBLE
        val bibleWords = listOf("bible", "verse", "scripture", "god", "jesus", "psalm", "proverb", "gospel", "apostle", "pray")
        if (bibleWords.any { lower.contains(it) }) return ChatType.BIBLE
        if (ref.isNotBlank() && (lower.contains("explain") || lower.contains("meaning") || lower.length < 30)) return ChatType.BIBLE
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
                but keep meaning identical to NKJV text below. Do not invent verse text.
                Use commentary/summary only as background.
            """.trimIndent()
            ChatType.NEWS -> "You are a news assistant. Do NOT force a Bible answer."
            ChatType.GENERAL -> "You are a helpful general assistant. Do NOT force a Bible answer unless user asked."
        }
    }

    // Same entities as BibleContent.kt: VerseEntity + SummaryEntity
    private suspend fun buildBibleContext(ref: String, question: String, requestedTranslation: String): String {
        val targetRef = extractVerseRef(question) ?: ref.ifBlank { bibleRef }
        if (targetRef.isBlank()) return ""
        val parsed = parseRef(targetRef) ?: return ""
        val (bookId, bookName, chapter, verseFrom, verseTo) = parsed

        return runCatching {
            // Your DAO: getVerseRange(bookId, chapter, startVerse, endVerse)
            // For whole chapter, use 1..200 - same as BibleContent loads whole chapter
            val verses = if (verseFrom != null) {
                bibleDao.getVerseRange(bookId, chapter, verseFrom, verseTo ?: verseFrom)
            } else {
                bibleDao.getVerseRange(bookId, chapter, 1, 200)
            }.joinToString("\n") { "${it.verse}: ${it.text}" }

            // Your DAO: getSummary(bookId, chapter) -> SummaryEntity?
            val summary = runCatching { bibleDao.getSummary(bookId, chapter) }.getOrNull()
            val summaryText = if (summary != null) {
                "Summary: ${summary.summary}\nWhat happened: ${summary.whatHappened ?: ""}\nApplication: ${summary.application ?: ""}"
            } else ""

            // Your DAO: getCommentaryCompressed(bookId, chapter) -> ByteArray?
            val compressed = runCatching { bibleDao.getCommentaryCompressed(bookId, chapter) }.getOrNull()
            val commentaryHtml = if (compressed != null) {
                CommentaryHelper.getHtmlCommentary(bookId, chapter, compressed)
            } else ""
            val commentaryPlain = stripHtml(commentaryHtml).take(2500)

            val translationInstruction = if (requestedTranslation == "NKJV") {
                "Translation: NKJV. Quote verses exactly from text below."
            } else {
                "User requested translation for AI answer: $requestedTranslation. " +
                "Verses below are NKJV (Bible tab / local DB only has NKJV) for grounding. " +
                "You already know $requestedTranslation. " +
                "Answer/quote in $requestedTranslation, but do NOT claim NKJV text below is $requestedTranslation."
            }

            """
            $translationInstruction

            PASSAGE: $bookName $chapter${if (verseFrom != null) ":$verseFrom${if (verseTo != null && verseTo != verseFrom) "-$verseTo" else ""}" else ""}
            VERSES (NKJV, from local DB):
            $verses

            $summaryText
            COMMENTARY (plain text, from zlib compressed DB):
            $commentaryPlain
            """.trimIndent()
        }.getOrDefault("")
    }

    private fun stripHtml(html: String): String {
        if (html.isBlank()) return ""
        return html
            .replace(Regex("(?i)<br[^>]*>"), "\n")
            .replace(Regex("(?i)</(p|div|h[1-6]|li)>"), "\n")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"")
            .replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
            .replace(Regex("\\s+"), " ").trim()
    }

    private data class ParsedRef(val bookId: Int, val bookName: String, val chapter: Int, val verseFrom: Int?, val verseTo: Int?)

    private fun extractVerseRef(text: String): String? {
        return Regex("""\b((?:1|2|3)\s+)?([A-Za-z]+)\s+(\d+)(?::(\d+)(?:-(\d+))?)?""").find(text)?.value
    }

    private fun parseRef(ref: String): ParsedRef? {
        val m = Regex("""^\s*(?:(1|2|3)\s+)?([A-Za-z]+)\s+(\d+)(?::(\d+)(?:-(\d+))?)?\s*$""").find(ref.trim()) ?: return null
        val prefix = m.groups[1]?.value
        val nameRaw = (if (prefix != null) "$prefix ${m.groups[2]?.value}" else m.groups[2]?.value) ?: return null
        val chapter = m.groups[3]?.value?.toIntOrNull() ?: return null
        val verseFrom = m.groups[4]?.value?.toIntOrNull()
        val verseTo = m.groups[5]?.value?.toIntOrNull()
        // Same as BibleContent: BookCatalog.id(String)
        val bookId = BookCatalog.id(nameRaw)
        return ParsedRef(bookId, BookCatalog.name(bookId), chapter, verseFrom, verseTo)
    }
}