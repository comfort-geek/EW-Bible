package com.ewbible.app.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ewbible.app.data.database.BibleDao
import com.ewbible.app.data.database.BookCatalog
import com.ewbible.app.data.database.CommentaryHelper
import com.ewbible.app.data.local.ChatDao
import com.ewbible.app.data.local.ChatEntity
import com.ewbible.app.data.local.ChatMessageEntity
import com.ewbible.app.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// ---------- Models ----------

data class ChatItem(
    val id: String,
    val title: String,
    val ref: String
)

data class ChatMessage(
    val id: String,
    val text: String,
    val isUser: Boolean
)

data class ChatState(
    val chats: List<ChatItem> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val currentChatId: String? = null,
    val chatsLoaded: Boolean = false,
    val isLoading: Boolean = false,
    val streamingText: String = "",
    // Bible context from screen / Bible tab
    val screenRef: String = "",
    val screenTranslation: String = "NKJV",
    val models: List<String> = emptyList(),
    val selectedModel: String = ""
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatDao: ChatDao,
    private val bibleDao: BibleDao,
    private val repository: ChatRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    // ============================================================
    // 1. SCREEN SYNC - Bible Tab -> AiChatScreen -> ViewModel -> DB
    // Anything can change: book, chapter, translation
    // ============================================================

    fun setScreenContext(ref: String, translation: String) {
        val cleanRef = ref.trim()
        val cleanTrans = translation.ifBlank { "NKJV" }.uppercase()
        if (cleanRef == _state.value.screenRef && cleanTrans == _state.value.screenTranslation) return

        _state.update { it.copy(screenRef = cleanRef, screenTranslation = cleanTrans) }

        // Save to current chat in DB so reopening chat restores same ref + translation
        val chatId = _state.value.currentChatId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val existing = chatDao.getChatById(chatId) ?: return@runCatching
                chatDao.updateChat(
                    existing.copy(
                        ref = cleanRef,
                        translation = cleanTrans
                    )
                )
            }
        }
    }

    // Called from AiChatScreen when translation chips change, or ref changes
    fun setTranslation(translation: String) {
        setScreenContext(_state.value.screenRef, translation)
    }

    // ============================================================
    // 2. CHATS
    // ============================================================

    fun loadChats() {
        viewModelScope.launch(Dispatchers.IO) {
            val chats = chatDao.getAllChats()
            val items = chats.map { ChatItem(it.id, it.title, it.ref) }
            withContext(Dispatchers.Main) {
                _state.update { it.copy(chats = items, chatsLoaded = true) }
            }
        }
    }

    fun startNewChat(ref: String = "", translation: String = "NKJV") {
        viewModelScope.launch(Dispatchers.IO) {
            val cleanRef = ref.trim()
            val cleanTrans = translation.ifBlank { _state.value.screenTranslation }.uppercase()
            val id = System.currentTimeMillis().toString()
            val title = if (cleanRef.isNotBlank()) cleanRef else "New Chat"
            val entity = ChatEntity(
                id = id,
                title = title,
                ref = cleanRef,
                translation = cleanTrans,
                createdAt = System.currentTimeMillis()
            )
            chatDao.insertChat(entity)
            withContext(Dispatchers.Main) {
                _state.update {
                    it.copy(
                        currentChatId = id,
                        messages = emptyList(),
                        screenRef = cleanRef,
                        screenTranslation = cleanTrans,
                        chats = listOf(ChatItem(id, title, cleanRef)) + it.chats
                    )
                }
            }
        }
    }

    fun openChat(chatId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val chat = chatDao.getChatById(chatId) ?: return@launch
            val msgs = chatDao.getMessagesForChat(chatId).map {
                ChatMessage(it.id, it.text, it.isUser)
            }
            withContext(Dispatchers.Main) {
                _state.update {
                    it.copy(
                        currentChatId = chatId,
                        messages = msgs,
                        // RESTORE ref + translation from DB - this is the fix
                        // Old code lost this and fell back to "" / general
                        screenRef = chat.ref,
                        screenTranslation = chat.translation.ifBlank { "NKJV" }
                    )
                }
            }
        }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            chatDao.deleteChat(chatId)
            chatDao.deleteMessagesForChat(chatId)
            withContext(Dispatchers.Main) {
                val remaining = _state.value.chats.filter { it.id != chatId }
                _state.update { it.copy(chats = remaining) }
                if (_state.value.currentChatId == chatId) {
                    _state.update { it.copy(currentChatId = null, messages = emptyList()) }
                }
            }
        }
    }

    fun refreshModels(context: Context) {
        viewModelScope.launch {
            val models = runCatching { repository.getModels(context) }.getOrDefault(emptyList())
            if (models.isNotEmpty()) {
                _state.update { it.copy(models = models, selectedModel = models.first()) }
            }
        }
    }

    // ============================================================
    // 3. SEND - Anything can change: Bible / News / General
    // ============================================================

    fun send(
        question: String,
        ref: String,
        image: Bitmap?,
        translation: String
    ) {
        val q = question.trim()
        if (q.isBlank()) return
        val latestRef = ref.ifBlank { _state.value.screenRef }
        val requestedTranslation = translation.ifBlank { _state.value.screenTranslation }.uppercase()

        // Update screen context first - so DB + state stay in sync
        setScreenContext(latestRef, requestedTranslation)

        val chatId = _state.value.currentChatId ?: run {
            startNewChat(latestRef, requestedTranslation)
            return
        }

        viewModelScope.launch {
            // 1. Save user message
            val userMsg = ChatMessage(System.currentTimeMillis().toString(), q, true)
            _state.update { it.copy(messages = it.messages + userMsg, isLoading = true, streamingText = "") }
            withContext(Dispatchers.IO) {
                chatDao.insertMessage(ChatMessageEntity(userMsg.id, chatId, q, true, System.currentTimeMillis()))
            }

            try {
                // 2. Decide type - NOT forced to Bible, NOT forced to general
                val type = detectType(q, latestRef)
                val bibleContext = if (type == ChatType.BIBLE) {
                    buildBibleContext(latestRef, q, requestedTranslation)
                } else ""

                val prompt = buildPrompt(q, type, bibleContext, requestedTranslation, latestRef)

                // 3. Call AI - AI knows ALL translations, DB only has NKJV
                var fullText = ""
                repository.sendStreaming(
                    prompt = prompt,
                    image = image,
                    model = _state.value.selectedModel
                ) { chunk ->
                    fullText += chunk
                    _state.update { it.copy(streamingText = fullText) }
                }

                val aiMsg = ChatMessage((System.currentTimeMillis() + 1).toString(), fullText, false)
                _state.update { it.copy(messages = it.messages + aiMsg, isLoading = false, streamingText = "") }
                withContext(Dispatchers.IO) {
                    chatDao.insertMessage(ChatMessageEntity(aiMsg.id, chatId, fullText, false, System.currentTimeMillis()))
                    // Update chat title from first question if still "New Chat"
                    val chat = chatDao.getChatById(chatId)
                    if (chat != null && (chat.title == "New Chat" || chat.title.isBlank())) {
                        chatDao.updateChat(chat.copy(title = q.take(50), ref = latestRef, translation = requestedTranslation))
                    }
                }
            } catch (e: Exception) {
                val err = "Sorry, I couldn't get an answer. ${e.message ?: ""}"
                val aiMsg = ChatMessage((System.currentTimeMillis() + 1).toString(), err, false)
                _state.update { it.copy(messages = it.messages + aiMsg, isLoading = false, streamingText = "") }
            }
        }
    }

    // ============================================================
    // 4. TYPE DETECTION - Explicit verse > News > General > CurrentRef
    // ============================================================

    private enum class ChatType { BIBLE, NEWS, GENERAL }

    private fun detectType(q: String, currentRef: String): ChatType {
        val lower = q.lowercase()

        // A. EXPLICIT VERSE in question -> Bible, always
        // "John 3:16", "Explain Romans 8", "What does Matthew 26 mean"
        val verseRegex = Regex("""\b(1|2|3)?\s?(genesis|exodus|leviticus|numbers|deuteronomy|joshua|judges|ruth|samuel|kings|chronicles|ezra|nehemiah|esther|job|psalm|proverbs|ecclesiastes|song|isaiah|jeremiah|lamentations|ezekiel|daniel|hosea|joel|amos|obadiah|jonah|micah|nahum|habakkuk|zephaniah|haggai|zechariah|malachi|matthew|mark|luke|john|acts|romans|corinthians|galatians|ephesians|philippians|colossians|thessalonians|timothy|titus|philemon|hebrews|james|peter|jude|revelation)\b""")
        if (verseRegex.containsMatchIn(lower)) return ChatType.BIBLE

        // B. NEWS - words that mean news, not Bible
        val newsWords = listOf("news", "today", "breaking", "election", "war", "president", "malawi", "football", "score")
        if (newsWords.any { lower.contains(it) }) return ChatType.NEWS

        // C. "This / that / explain this" -> use currentRef from Bible tab
        val thisWords = listOf("this", "that", "explain", "what does it mean", "what is this", "this verse", "this passage", "this chapter")
        if (currentRef.isNotBlank() && thisWords.any { lower.contains(it) }) return ChatType.BIBLE

        // D. Bible keywords
        val bibleWords = listOf("bible", "verse", "scripture", "god", "jesus", "pray", "faith", "chapter", "book of")
        if (bibleWords.any { lower.contains(it) }) return ChatType.BIBLE

        // E. If we came from a verse and question is short / general, still Bible?
        // NO - if no signal, it's GENERAL. This is the fix: don't force Bible mode.
        // General = "How to cook rice", "What is AI", etc.
        return ChatType.GENERAL
    }

    // ============================================================
    // 5. BIBLE CONTEXT - DB = NKJV ONLY, AI = ALL TRANSLATIONS
    // ============================================================

    private suspend fun buildBibleContext(
        screenRef: String,
        question: String,
        requestedTranslation: String
    ): String = withContext(Dispatchers.IO) {
        // Which ref to use? Question verse wins, else screenRef (Bible tab)
        val refToUse = extractRefFromQuestion(question) ?: screenRef.ifBlank { return@withContext "" }
        val parsed = parseRef(refToUse) ?: return@withContext ""

        val (bookId, chapter, fromVerse, toVerse) = parsed

        // --- ALWAYS NKJV from local DB ---
        val verses = runCatching {
            if (fromVerse != null && toVerse != null) {
                bibleDao.getVerseRange(bookId, chapter, fromVerse, toVerse)
            } else {
                bibleDao.getVerseRange(bookId, chapter, 1, BookCatalog.verses(bookId, chapter))
            }
        }.getOrDefault(emptyList())

        val versesText = verses.joinToString("\n") { "${it.verse}: ${it.text}" }

        val summary = runCatching { bibleDao.getSummary(bookId, chapter) ?: "" }.getOrDefault("")

        // --- Commentary: zlib compressed in DB -> CommentaryHelper -> plain text for AI ---
        val commentaryPlain = runCatching {
            val compressed = bibleDao.getCommentaryCompressed(bookId, chapter)
            if (compressed == null) "" else {
                // getHtmlCommentary does: decompress (Inflater = zlib) + cleanHtml + cache
                val html = CommentaryHelper.getHtmlCommentary(bookId, chapter, compressed)
                stripHtml(html).take(2500)
            }
        }.getOrDefault("").take(2500)

        // --- Translation instruction: AI knows all translations ---
        val translationInstruction = if (requestedTranslation == "NKJV") {
            "Translation: NKJV. Quote verses exactly from the NKJV text below."
        } else {
            """
            User requested translation: $requestedTranslation.
            The verses below are provided in NKJV (the ONLY translation stored locally in the app) for grounding.
            You already know $requestedTranslation.
            Answer and quote in $requestedTranslation.
            Do NOT claim the NKJV text below is $requestedTranslation.
            Keep meaning identical to the NKJV text below. Do NOT invent verse text.
            """.trimIndent()
        }

        """
        $translationInstruction

        PASSAGE: $refToUse
        BOOK: ${BookCatalog.name(bookId)} CHAPTER: $chapter
        VERSES (NKJV, from local DB):
        $versesText

        SUMMARY:
        $summary

        COMMENTARY (for context, do not quote as Scripture):
        $commentaryPlain
        """.trimIndent()
    }

    private fun buildPrompt(
        q: String,
        type: ChatType,
        bibleContext: String,
        translation: String,
        ref: String
    ): String {
        return when (type) {
            ChatType.BIBLE -> """
                You are a helpful Bible assistant.
                $bibleContext
                User is currently reading: $ref (if any).
                User translation preference: $translation.
                User question: $q
                Answer clearly, grounded in the passage above. If you use commentary, say it is commentary, not Scripture.
                At the end, add Sources section with 1-3 links if relevant.
            """.trimIndent()
            ChatType.NEWS -> """
                You are a helpful news assistant. Answer with latest information you know.
                User question: $q
                If you are unsure of a fact, say so. Add Sources section with 1-3 links.
            """.trimIndent()
            ChatType.GENERAL -> """
                You are a helpful general assistant. Answer anything.
                User translation preference (only use if Bible is asked): $translation.
                User question: $q
                Do NOT force Bible mode. If question is not about Bible, do not bring Bible verses unless user asks.
                Add Sources section with 1-3 links if relevant.
            """.trimIndent()
        }
    }

    // ============================================================
    // 6. HELPERS - Ref parsing + HTML strip
    // ============================================================

    private data class ParsedRef(val bookId: Int, val chapter: Int, val fromVerse: Int?, val toVerse: Int?)

    private fun extractRefFromQuestion(q: String): String? {
        // "Explain John 3:16" -> "John 3:16"
        val regex = Regex("""\b(1|2|3)?\s?([A-Za-z]+)\s+(\d+)(?::(\d+)(?:-(\d+))?)?\b""")
        val m = regex.find(q) ?: return null
        val bookName = (m.groups[1]?.value?.trim() + " " + (m.groups[2]?.value ?: "")).trim()
        val bookId = BookCatalog.idByName(bookName) ?: return null
        if (bookId == 0) return null
        val chapter = m.groups[3]?.value?.toIntOrNull() ?: return null
        val verse = m.groups[4]?.value
        return if (verse != null) "$bookName $chapter:$verse" else "$bookName $chapter"
    }

    private fun parseRef(ref: String): ParsedRef? {
        // "Matthew 26", "John 3:16", "John 3:16-18", "1 John 2:3"
        val regex = Regex("""^\s*(1|2|3)?\s?([A-Za-z]+)\s+(\d+)(?::(\d+)(?:-(\d+))?)?\s*$""")
        val m = regex.matchEntire(ref.trim()) ?: return null
        val bookName = ((m.groups[1]?.value?.trim() ?: "") + " " + (m.groups[2]?.value ?: "")).trim()
        val bookId = BookCatalog.idByName(bookName)
        if (bookId == 0) return null
        val chapter = m.groups[3]?.value?.toIntOrNull() ?: return null
        val from = m.groups[4]?.value?.toIntOrNull()
        val to = m.groups[5]?.value?.toIntOrNull() ?: from
        return ParsedRef(bookId, chapter, from, to)
    }

    private fun stripHtml(html: String): String {
        if (html.isBlank()) return ""
        var t = html
        t = t.replace(Regex("(?i)<(script|style)[^>]*>.*?</\\1>"), " ")
        t = t.replace(Regex("<[^>]+>"), " ")
        t = t.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
        return t.replace(Regex("\\s+"), " ").trim()
    }
}