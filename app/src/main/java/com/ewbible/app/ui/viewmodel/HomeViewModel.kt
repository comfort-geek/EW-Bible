package com.ewbible.app.ui.viewmodel

import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ewbible.app.data.database.*
import com.ewbible.app.data.preferences.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeUiState(
    val books: List<BookEntity> = emptyList(),
    val verses: List<VerseEntity> = emptyList(),
    val highlights: Map<Int, Color> = emptyMap(),
    val notes: List<NoteEntity> = emptyList(),
    val summary: SummaryEntity? = null,
    val summaryLoading: Boolean = false,
    val summaryText: String = "",
    val summaryWhatHappened: String = "",
    val summaryApplication: String = "",
    val summaryItalicText: String = "",
    val commentaryHtml: String = "",
    val commentaryError: Boolean = false,
    val linkedVerseText: String = "",
    val linkedVerseRef: String = "",
    val chapterCounts: Map<Int, Int> = BookCatalog.all.associate { it.id to it.chapters },
    val searchResults: List<VerseSearchResult> = emptyList(),
    val searchFilter: String = "All",
    val dailyVerse: String = "",
    val currentBookId: Int = 1,
    val currentBook: String = "Genesis",
    val selectedBook: String = "Genesis",
    val selectedChapter: Int = 1,
    val selectedVerse: Int? = null,
    val selectedTab: String = "Bible",
    val darkMode: Boolean? = null,
    val fontSize: Float = 18f,
    val isLoggedIn: Boolean = false,
    val userName: String = "Guest",
    val userEmail: String = "",
    val scrollRestoreKey: Int = 0,
    val showNoteDialog: Boolean = false,
    val showAiChat: Boolean = false,
    val currentNoteText: String = "",
    val isReadingRestored: Boolean = false
)

@OptIn(FlowPreview::class)
class HomeViewModel(
    private val dao: BibleDao,
    private val prefs: AppPreferences
) : ViewModel() {

    private val selectedBookId = MutableStateFlow(1)
    private val selectedChapter = MutableStateFlow(1)
    private val selectedVerse = MutableStateFlow<Int?>(null)
    private val selectedTab = MutableStateFlow("Bible")
    private val searchQuery = MutableStateFlow("")
    private val searchFilter = MutableStateFlow("All")
    private val showNoteDialogState = MutableStateFlow(false)
    private val showAiChatState = MutableStateFlow(false)
    private val currentNoteText = MutableStateFlow("")
    private val commentary = MutableStateFlow("")
    private val commentaryError = MutableStateFlow(false)
    private val summaryState = MutableStateFlow<SummaryEntity?>(null)
    private val _summaryLoading = MutableStateFlow(false)
    val summaryLoading: StateFlow<Boolean> = _summaryLoading.asStateFlow()
    private val isReadingRestored = MutableStateFlow(false)
    private val chapterCounts = MutableStateFlow(BookCatalog.all.associate { it.id to it.chapters })
    private val searchResults = MutableStateFlow<List<VerseSearchResult>>(emptyList())
    private val dailyVerse = MutableStateFlow("")
    private val scrollRestoreKey = MutableStateFlow(0)
    private val linkedVerseText = MutableStateFlow("")
    private val linkedVerseRef = MutableStateFlow("")
    private var commentaryJob: Job? = null
    private var restoreDone = false

    // MAX: LruCache is thread-safe, LinkedHashMap was not + manual size check
    private val commentaryCache = LruCache<String, String>(30)
    private val summaryCache = LruCache<String, SummaryEntity?>(60)
    private val summaryLoadingKeys = mutableSetOf<String>()
    private val versesCache = LruCache<String, List<VerseEntity>>(30)

    val books: Flow<List<BookEntity>> = flowOf(BookCatalog.all.map { BookCatalog.toEntity(it) })
    val currentBookId: StateFlow<Int> = selectedBookId.asStateFlow()

    // MAX: Single source, shared, no double collect
    private val bookChapterKey = combine(selectedBookId, selectedChapter) { b, c -> b to c }
        .distinctUntilChanged()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    val verses: Flow<List<VerseEntity>> = bookChapterKey
        .flatMapLatest { (bookId, chapter) ->
            val key = "$bookId-$chapter"
            val cached = versesCache.get(key)
            dao.getVerses(bookId, chapter)
                .distinctUntilChanged()
                .map { list ->
                    if (list.isNotEmpty()) versesCache.put(key, list)
                    if (list.isEmpty() && cached != null) cached else list
                }
                .onStart { if (cached != null) emit(cached) }
                .catch { emit(cached ?: emptyList()) }
                .flowOn(Dispatchers.IO)
        }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    val highlights: Flow<Map<Int, Color>> = bookChapterKey
        .flatMapLatest { (bookId, chapter) ->
            dao.getHighlights(bookId, chapter)
                .map { list -> list.associate { it.verse to Color(it.color) } }
                .catch { emit(emptyMap()) }
                .flowOn(Dispatchers.IO)
        }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    val notes: Flow<List<NoteEntity>> = dao.getAllNotes()
        .catch { emit(emptyList()) }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    // MAX: ONE uiState combine = 1 recomposition per change, not 24
    val uiState: StateFlow<HomeUiState> = combine(
        books, verses, highlights, notes, selectedBookId, selectedChapter,
        selectedVerse, selectedTab, commentary, commentaryError, summaryState,
        _summaryLoading, chapterCounts, searchResults, searchFilter, dailyVerse,
        scrollRestoreKey, linkedVerseText, linkedVerseRef,
        showNoteDialogState, showAiChatState, currentNoteText, isReadingRestored,
        prefs.isDarkMode, prefs.fontSize, prefs.userName, prefs.userEmail
    ) { v ->
        @Suppress("UNCHECKED_CAST")
        val booksL = v[0] as List<BookEntity>
        val versesL = v[1] as List<VerseEntity>
        val highL = v[2] as Map<Int, Color>
        val notesL = v[3] as List<NoteEntity>
        val bookId = v[4] as Int
        val chapter = v[5] as Int
        val verse = v[6] as Int?
        val tab = v[7] as String
        val comm = v[8] as String
        val commErr = v[9] as Boolean
        val summ = v[10] as SummaryEntity?
        val summLoad = v[11] as Boolean
        val counts = v[12] as Map<Int, Int>
        val searchR = v[13] as List<VerseSearchResult>
        val filter = v[14] as String
        val daily = v[15] as String
        val scrollK = v[16] as Int
        val linkT = v[17] as String
        val linkR = v[18] as String
        val showNote = v[19] as Boolean
        val showAi = v[20] as Boolean
        val noteT = v[21] as String
        val restored = v[22] as Boolean
        val dark = v[23] as Boolean?
        val font = (v[24] as Float).coerceAtLeast(18f)
        val name = v[25] as String
        val email = v[26] as String
        HomeUiState(
            books = booksL, verses = versesL, highlights = highL, notes = notesL,
            summary = summ, summaryLoading = summLoad,
            summaryText = summ?.summary ?: "", summaryWhatHappened = summ?.whatHappened ?: "",
            summaryApplication = summ?.application ?: "", summaryItalicText = summ?.summary ?: "",
            commentaryHtml = comm, commentaryError = commErr,
            linkedVerseText = linkT, linkedVerseRef = linkR,
            chapterCounts = counts, searchResults = searchR, searchFilter = filter,
            dailyVerse = daily, currentBookId = bookId,
            currentBook = BookCatalog.name(bookId), selectedBook = BookCatalog.name(bookId),
            selectedChapter = chapter, selectedVerse = verse, selectedTab = tab,
            darkMode = dark, fontSize = font,
            isLoggedIn = name != "Guest" && email.isNotBlank(),
            userName = name, userEmail = email,
            scrollRestoreKey = scrollK, showNoteDialog = showNote, showAiChat = showAi,
            currentNoteText = noteT, isReadingRestored = restored
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    init {
        viewModelScope.launch { restoreSavedReading() }

        // MAX: Load summary + commentary ONLY when needed, once per chapter
        viewModelScope.launch {
            bookChapterKey.collectLatest { (bookId, chapter) ->
                loadSummary(bookId, chapter)
                // Commentary: only if user is on Open EW tab, not on Bible tab
                if (selectedTab.value == "Open EW") loadCommentary(bookId, chapter)
            }
        }
        viewModelScope.launch {
            selectedTab.distinctUntilChanged().collectLatest { tab ->
                if (tab == "Open EW") loadCommentary(selectedBookId.value, selectedChapter.value)
            }
        }

        viewModelScope.launch {
            runCatching {
                val fromDb = withContext(Dispatchers.IO) { dao.getChapterCounts().associate { it.bookId to it.chapterCount } }
                if (fromDb.isNotEmpty()) {
                    val catalog = BookCatalog.all.associate { it.id to it.chapters }
                    chapterCounts.value = catalog.mapValues { (id, c) -> maxOf(c, fromDb[id] ?: 0) }
                }
            }
        }

        // MAX: Search on IO, debounce, cancel previous, escape once
        viewModelScope.launch {
            combine(searchQuery.debounce(250L).distinctUntilChanged(), searchFilter) { q, f -> q to f }
                .distinctUntilChanged()
                .collectLatest { (query, filter) ->
                    if (query.isBlank()) { searchResults.value = emptyList(); return@collectLatest }
                    val escaped = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
                    val raw = runCatching { withContext(Dispatchers.IO) { dao.searchVerses(escaped) } }.getOrDefault(emptyList())
                    searchResults.value = when (filter) {
                        "Old Testament", "OT" -> raw.filter { it.bookId <= 39 }
                        "New Testament", "NT" -> raw.filter { it.bookId >= 40 }
                        "Gospel" -> raw.filter { it.bookId in 40..43 }
                        "Prophecy" -> raw.filter { it.bookId in setOf(23,24,26,27,28,29,30,31,32,33,34,35,36,37,38,39,66) }
                        else -> raw
                    }
                }
        }
        loadDailyVerse()
    }

    fun selectBook(bookId: Int) {
        selectedBookId.value = bookId.coerceIn(1, 66)
        selectedChapter.value = 1
        selectedVerse.value = null
        scrollRestoreKey.value += 1
        saveReadingProgress()
    }
    fun selectBook(bookName: String) = selectBook(BookCatalog.id(bookName))
    fun selectChapter(chapter: Int) {
        val max = BookCatalog.chapters(currentBookId.value)
        selectedChapter.value = chapter.coerceIn(1, max)
        selectedVerse.value = null
        scrollRestoreKey.value += 1
        saveReadingProgress()
    }
    fun previousChapter() {
        if (selectedChapter.value > 1) { selectChapter(selectedChapter.value - 1); return }
        val prev = currentBookId.value - 1
        if (prev < 1) return
        selectedBookId.value = prev
        selectedChapter.value = BookCatalog.chapters(prev)
        selectedVerse.value = null
        scrollRestoreKey.value += 1
        saveReadingProgress()
    }
    fun nextChapter() {
        val max = BookCatalog.chapters(currentBookId.value)
        if (selectedChapter.value < max) { selectChapter(selectedChapter.value + 1); return }
        val next = currentBookId.value + 1
        if (next > 66) return
        selectedBookId.value = next
        selectedChapter.value = 1
        selectedVerse.value = null
        scrollRestoreKey.value += 1
        saveReadingProgress()
    }
    fun selectVerse(verse: Int?) {
        selectedVerse.value = verse
        if (verse != null) {
            viewModelScope.launch {
                val note = runCatching { withContext(Dispatchers.IO) { dao.getNoteAtVerse(currentBookId.value, selectedChapter.value, verse) } }.getOrNull()
                currentNoteText.value = note?.text ?: ""
            }
        }
    }
    fun clearSelection() { selectedVerse.value = null }
    fun openBook(bookId: Int, chapter: Int) {
        val safeId = bookId.coerceIn(1, 66)
        selectedBookId.value = safeId
        selectedChapter.value = chapter.coerceIn(1, BookCatalog.chapters(safeId))
        selectedVerse.value = null
        scrollRestoreKey.value += 1
        saveReadingProgress()
    }
    fun openBook(bookName: String, chapter: Int) = openBook(BookCatalog.id(bookName), chapter)
    fun selectTab(tab: String) { selectedTab.value = tab }
    fun searchVerses(query: String) { searchQuery.value = query }
    fun setSearchQuery(query: String) { searchQuery.value = query }
    fun setSearchFilter(filter: String) { searchFilter.value = filter }
    fun setDarkMode(enabled: Boolean) { viewModelScope.launch { runCatching { prefs.setDarkMode(enabled) } } }
    fun setFontSize(size: Float) { viewModelScope.launch { runCatching { prefs.setFontSize(size.coerceAtLeast(18f)) } } }
    fun onUserSignedIn(name: String, email: String) { viewModelScope.launch { runCatching { prefs.saveUser(name, email) } } }
    fun onUserSignedOut() { viewModelScope.launch { runCatching { prefs.clearUser() } } }
    fun saveReadingProgress(verse: VerseEntity? = null, scrollIndex: Int = 0, scrollOffset: Int = 0) {
        viewModelScope.launch {
            runCatching { prefs.saveLastReading(bookId = currentBookId.value, chapter = selectedChapter.value, verse = verse?.verse ?: (selectedVerse.value ?: 1), scrollIndex = scrollIndex, scrollOffset = scrollOffset) }
        }
    }
    private suspend fun restoreSavedReading() {
        if (restoreDone) return
        try {
            val lastBookId = runCatching { prefs.getLastBookId() }.getOrDefault(1)
            val lastChapter = runCatching { prefs.getLastChapter() }.getOrDefault(1)
            if (lastBookId in 1..66) {
                selectedBookId.value = lastBookId
                selectedChapter.value = lastChapter.coerceIn(1, BookCatalog.chapters(lastBookId))
            }
        } catch (e: Exception) {
            selectedBookId.value = 1; selectedChapter.value = 1
        } finally {
            restoreDone = true; isReadingRestored.value = true
        }
    }

    fun loadSummary(bookId: Int = currentBookId.value, chapter: Int = selectedChapter.value) {
        val key = "$bookId-$chapter"
        summaryCache.get(key)?.let { cached ->
            if (bookId == currentBookId.value && chapter == selectedChapter.value) summaryState.value = cached
            return
        }
        if (key in summaryLoadingKeys) return
        summaryLoadingKeys.add(key)
        viewModelScope.launch {
            val isCurrent = bookId == currentBookId.value && chapter == selectedChapter.value
            if (isCurrent) _summaryLoading.value = true
            val s = runCatching { withContext(Dispatchers.IO) { dao.getSummary(bookId, chapter) } }.getOrNull()
            summaryCache.put(key, s)
            if (isCurrent) summaryState.value = s
            summaryLoadingKeys.remove(key)
            if (summaryLoadingKeys.isEmpty()) _summaryLoading.value = false
        }
    }
    fun loadSummary() { loadSummary(currentBookId.value, selectedChapter.value) }

    fun loadLinkedVerse(bookName: String, chapter: Int, verse: Int, endVerse: Int = verse) {
        viewModelScope.launch {
            val bookId = runCatching { BookCatalog.id(bookName) }.getOrDefault(1)
            val end = maxOf(verse, endVerse)
            linkedVerseRef.value = if (end > verse) "${BookCatalog.name(bookId)} $chapter:$verse-$end" else "${BookCatalog.name(bookId)} $chapter:$verse"
            linkedVerseText.value = "Loading..."
            val range = runCatching { withContext(Dispatchers.IO) { dao.getVerseRange(bookId, chapter, verse, end) } }.getOrDefault(emptyList())
            linkedVerseText.value = if (range.isEmpty()) "Verse not found." else range.joinToString("\n\n") { "${it.verse}. ${it.text}" }
        }
    }

    // MAX: Commentary via CommentaryHelper, cached, IO, cancel previous
    fun loadCommentary(bookId: Int = currentBookId.value, chapter: Int = selectedChapter.value) {
        val key = "$bookId-$chapter"
        commentaryCache.get(key)?.let {
            commentaryJob?.cancel()
            commentaryError.value = false
            commentary.value = it
            return
        }
        commentaryJob?.cancel()
        commentaryJob = viewModelScope.launch {
            try {
                val html = withContext(Dispatchers.IO) {
                    val compressed = runCatching { dao.getCommentaryCompressed(bookId, chapter) }.getOrNull()
                    if (compressed.isNullOrEmpty()) null else CommentaryHelper.decompress(compressed)
                }
                val finalHtml = if (html.isNullOrBlank()) {
                    "<html><body><p><i>Commentary for ${BookCatalog.name(bookId)} $chapter is not available yet.</i></p></body></html>"
                } else html
                if (!html.isNullOrBlank()) commentaryCache.put(key, html)
                commentary.value = finalHtml
                commentaryError.value = false
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                commentary.value = "<html><body><p><i>Commentary for ${BookCatalog.name(bookId)} $chapter is not available yet.</i></p></body></html>"
                commentaryError.value = false
            }
        }
    }
    fun loadCommentary() { loadCommentary(currentBookId.value, selectedChapter.value) }

    fun loadDailyVerse() {
        viewModelScope.launch {
            val verse = runCatching { withContext(Dispatchers.IO) { dao.getRandomVerse() } }.getOrNull()
            dailyVerse.value = verse?.let { "${it.text}\n— ${BookCatalog.name(it.bookId)} ${it.chapter}:${it.verse}" }
                ?: "Thy word is a lamp unto my feet, and a light unto my path.\n— Psalm 119:105"
        }
    }
    fun showNoteDialog() { if (selectedVerse.value == null) return; showNoteDialogState.value = true }
    fun dismissNoteDialog() { showNoteDialogState.value = false }
    fun openAiChat() { showAiChatState.value = true }
    fun closeAiChat() { showAiChatState.value = false }
    fun saveNote(noteText: String) {
        val verseNum = selectedVerse.value ?: return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val existing = dao.getNoteAtVerse(currentBookId.value, selectedChapter.value, verseNum)
                    if (existing != null) dao.updateNote(existing.copy(text = noteText, timestamp = System.currentTimeMillis()))
                    else dao.saveNote(NoteEntity(bookId = currentBookId.value, chapter = selectedChapter.value, verse = verseNum, text = noteText, timestamp = System.currentTimeMillis()))
                }
                currentNoteText.value = noteText
            }
        }
    }
    fun saveHighlight(color: Color) {
        val verseNum = selectedVerse.value ?: return
        viewModelScope.launch { runCatching { withContext(Dispatchers.IO) { dao.saveHighlight(HighlightEntity(bookId = currentBookId.value, chapter = selectedChapter.value, verse = verseNum, color = color.toArgb())) } } }
    }
    fun removeHighlight() {
        val verseNum = selectedVerse.value ?: return
        viewModelScope.launch { runCatching { withContext(Dispatchers.IO) { dao.deleteHighlight(currentBookId.value, selectedChapter.value, verseNum) } } }
    }
}