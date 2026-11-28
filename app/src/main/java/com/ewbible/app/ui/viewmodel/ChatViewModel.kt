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
import com.ewbible.app.data.sync.SyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val currentTranslation: String = "NKJV",
    val availableModels: List<String> = emptyList()
)

// Matches ChatViewModelFactory exactly: bibleDao, geminiRepository, syncManager
// No @HiltViewModel - created manually via Factory
class ChatViewModel(
    private val bibleDao: BibleDao,
    private val geminiRepository: AiRepository,
    private val syncManager: SyncManager? = null
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var bibleRef: String = ""
    private var chatTranslation: String = "NKJV"

    fun setBibleRef(ref: String) {
        val cleanRef = ref.trim()
        if (cleanRef == bibleRef) return
        bibleRef = cleanRef
        _state.update { it.copy(currentBibleRef = cleanRef) }
        saveRefToCurrentChat(cleanRef)
    }

    fun setChatTranslation(translation: String) {
        val clean = translation.ifBlank { "NKJV" }.uppercase()
        if (clean == chatTranslation) return
        chatTranslation = clean
        _state.update { it.copy(currentTranslation = clean) }
    }

    fun setScreenContext(ref: String, translation: String) {
        setBibleRef(ref)
        if (translation.isNotBlank()) setChatTranslation(translation)
    }

    private fun saveRefToCurrentChat(ref: String) {
        val chatId = _state.value.currentChatId ?: return
        viewModelScope.launch {
            runCatching {
                val existing = bibleDao.getChatById(chatId) ?: return@launch
                bibleDao.updateChat(existing.copy(ref = ref))
            }
        }
    }

    // FIXED: AiRepository has refreshModels(context), NOT getAvailableModels()
    fun refreshModels(context: Context) {
        viewModelScope.launch {
            runCatching { geminiRepository.refreshModels(context) }
        }
    }

    fun loadChats() {
        viewModelScope.launch {
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
            val id = runCatching {
                bibleDao.insertChat(
                    ChatEntity(id = 0L, title = if (cleanRef.isNotBlank()) cleanRef else "New Chat", ref = cleanRef, createdAt = System.currentTimeMillis())
                )
            }.getOrDefault(System.currentTimeMillis())
            _state.update {
                it.copy(currentChatId = id, messages = emptyList(), streamingText = "", isLoading = false, currentBibleRef = cleanRef, currentTranslation = cleanTrans)
            }
            loadChats()
        }
    }

    fun openChat(chatId: Long) {
        viewModelScope.launch {
            val entity = runCatching { bibleDao.getChatById(chatId) }.getOrNull() ?: return@launch
            val msgs = runCatching { bibleDao.getMessagesForChatSync(chatId) }.getOrDefault(emptyList())
            bibleRef = entity.ref
            chatTranslation = "NKJV"
            _state.update {
                it.copy(
                    currentChatId = chatId,
                    messages = msgs.map { m -> ChatMessage(m.id.toString(), m.text, m.isUser) },
                    currentBibleRef = entity.ref,
                    currentTranslation = "NKJV",
                    isLoading = false, streamingText = ""
                )
            }
        }
    }

    fun deleteChat(chatId: Long) {
        viewModelScope.launch {
            runCatching { bibleDao.deleteMessagesForChat(chatId); bibleDao.deleteChat(chatId) }
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

        viewModelScope.launch {
            var chatId = _state.value.currentChatId
            if (chatId == null) {
                chatId = runCatching {
                    bibleDao.insertChat(
                        ChatEntity(id = 0L, title = if (useRef.isNotBlank()) useRef else "New Chat", ref = useRef, createdAt = System.currentTimeMillis())
                    )
                }.getOrDefault(System.currentTimeMillis())
                _state.update {
                    it.copy(currentChatId = chatId, messages = emptyList(), currentBibleRef = useRef, currentTranslation = useTranslation, isLoading = false, streamingText = "")
                }
                loadChats()
            }
            val finalChatId = chatId ?: return@launch

            val userMsg = ChatMessage(text = q, isUser = true)
            _state.update { it.copy(messages = it.messages + userMsg, isLoading = true, streamingText = "") }

            runCatching {
                bibleDao.insertMessage(MessageEntity(chatId = finalChatId, text = q, isUser = true, timestamp = System.currentTimeMillis()))
            }

            // History for AiRepository - must be MessageEntity list
            val history = runCatching { bibleDao.getMessagesForChatSync(finalChatId) }.getOrDefault(emptyList())

            // FIX: No more isBibleQuestion() keyword gate.
            // Questions change - "why did he say that?", "what's the lesson here?",
            // "summarize it", "verse 5?" would all fail the keyword list and make AI blind.
            // Now: if we have a ref, ALWAYS pass bibleContext.
            // buildBibleContext() itself tells the model when to use it vs ignore it.
            // So Bible questions work with ANY phrasing, and news/general questions
            // still work because the model is told to ignore the passage context.
            val wantsWebSources = userAskedForWebSources(q)

            val bibleContext = if (useRef.isNotBlank()) {
                buildBibleContext(useRef, q, useTranslation, wantsWebSources)
            } else if (wantsWebSources) {
                // General question, no Bible ref, but user wants sources
                "User asked for web sources. You may include a Sources section with real links at the end."
            } else {
                // General question, no Bible ref, no sources asked
                "Do NOT include any Sources, links, or web references. Answer directly."
            }

            var aiText = ""
            try {
                // FIXED: real method is askStreaming(question, currentRef, history, image, bibleContext, translation)
                geminiRepository.askStreaming(
                    question = q,
                    currentRef = useRef,
                    history = history,
                    image = image,
                    bibleContext = bibleContext,
                    translation = useTranslation
                ).collect { chunk ->
                    if (chunk.isNotEmpty()) {
                        aiText += chunk
                        _state.update { it.copy(streamingText = aiText) }
                    }
                }
            } catch (e: Exception) {
                if (aiText.isBlank()) aiText = "Sorry, I couldn't get an answer. Please try again."
            }

            val aiMsg = ChatMessage(text = aiText, isUser = false)
            _state.update { it.copy(messages = it.messages + aiMsg, isLoading = false, streamingText = "") }
            runCatching {
                bibleDao.insertMessage(MessageEntity(chatId = finalChatId, text = aiText, isUser = false, timestamp = System.currentTimeMillis()))
            }
            runCatching {
                val existing = bibleDao.getChatById(finalChatId) ?: return@runCatching
                if (existing.title == "New Chat" || existing.title.isBlank()) {
                    bibleDao.updateChat(existing.copy(title = q.take(50)))
                }
            }
            loadChats()
        }
    }

    // DELETED: isBibleQuestion() - keyword list can never cover all question phrasings.
    // Replaced by always-send-context + relevance rule inside buildBibleContext().

    // NEW: Web sources / links only if user explicitly asks for them.
    // Bible is from local DB, never a web link.
    private fun userAskedForWebSources(question: String): Boolean {
        val t = question.lowercase()
        return listOf(
            "source", "sources", "link", "links", "reference", "references",
            "cite", "citation", "where did you get", "where is this from",
            "give me the link", "send link", "show me the source",
            "web", "online", "google", "search"
        ).any { t.contains(it) }
    }

    private suspend fun buildBibleContext(ref: String, question: String, requestedTranslation: String, wantsWebSources: Boolean = false): String {
        val targetRef = extractVerseRef(question) ?: ref.ifBlank { bibleRef }
        if (targetRef.isBlank()) return ""
        val parsed = parseRef(targetRef) ?: return ""
        val (bookId, bookName, chapter, verseFrom, verseTo) = parsed

        return runCatching {
            val verses = if (verseFrom != null) {
                bibleDao.getVerseRange(bookId, chapter, verseFrom, verseTo ?: verseFrom)
            } else {
                bibleDao.getVerseRange(bookId, chapter, 1, 200)
            }.joinToString("\n") { "${it.verse}: ${it.text}" }

            val summary = runCatching { bibleDao.getSummary(bookId, chapter) }.getOrNull()
            val summaryText = if (summary != null) {
                "Summary: ${summary.summary}\nWhat happened: ${summary.whatHappened}\nApplication: ${summary.application}"
            } else ""

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

            val sourceRule = if (wantsWebSources) {
                "User explicitly asked for web sources / links. You MAY add a 'Sources' section at the very end with up to 3 real web links."
            } else {
                "User did NOT ask for web sources / links. Do NOT add any Sources section, do NOT add any links, do NOT add any URLs, do NOT add any web references."
            }

            """
            $translationInstruction

            SOURCE RULE - VERY IMPORTANT:
            $sourceRule

            BIBLE SOURCE RULE:
            - The verses / summary / commentary below come from the LOCAL DATABASE (NKJV DB on the device). They are NOT from the web.
            - NEVER present Bible verses as web links. NEVER invent a biblegateway.com / biblehub.com / gotquestions.org link for a Bible verse.
            - If no web sources were asked for, just answer using the DB text below. No links needed.

            CURRENT SCREEN CONTEXT:
            User is currently reading: $bookName $chapter${if (verseFrom != null) ":$verseFrom${if (verseTo != null && verseTo != verseFrom) "-$verseTo" else ""}" else ""} ($requestedTranslation)

            RELEVANCE RULE - READ THIS FIRST:
            - If the user's question is about this passage / Bible / verse / meaning / explanation / lesson / summary - even if phrased vaguely like "what does this mean?", "why did he say that?", "what's the lesson here?", "summarize it", "verse 5?" - it refers to THIS passage. Answer about THIS passage using the verses below.
            - Do NOT ask "which passage?" - you already know it: it is the CURRENT SCREEN passage above.
            - If the question is unrelated (news, weather, programming, general knowledge, sports, etc.) - IGNORE this passage context completely and answer normally. Do NOT force a Bible answer.

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
        val bookId = BookCatalog.id(nameRaw)
        return ParsedRef(bookId, BookCatalog.name(bookId), chapter, verseFrom, verseTo)
    }
}