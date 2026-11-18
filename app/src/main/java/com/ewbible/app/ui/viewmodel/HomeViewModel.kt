package com.ewbible.app.ui.viewmodel

import android.util.Log
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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater

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
    val fontSize: Float = 16f,
    val isLoggedIn: Boolean = false,
    val userName: String = "Guest",
    val userEmail: String = "",
    val scrollRestoreKey: Int = 0,
    val showNoteDialog: Boolean = false,
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
    private val currentNoteText = MutableStateFlow("")
    private val commentary = MutableStateFlow("")
    private val commentaryError = MutableStateFlow(false)
    private val summaryState = MutableStateFlow<SummaryEntity?>(null)
    private val _summaryLoading = MutableStateFlow(false)
    val summaryLoading: StateFlow<Boolean> = _summaryLoading.asStateFlow()
    private val isReadingRestored = MutableStateFlow(false)
    private val chapterCounts = MutableStateFlow<Map<Int, Int>>(
        BookCatalog.all.associate { it.id to it.chapters }
    )
    private val searchResults = MutableStateFlow<List<VerseSearchResult>>(emptyList())
    private val dailyVerse = MutableStateFlow("")
    private val scrollRestoreKey = MutableStateFlow(0)
    private val linkedVerseText = MutableStateFlow("")
    private val linkedVerseRef = MutableStateFlow("")
    private var commentaryJob: Job? = null
    private var restoreDone = false

    private val commentaryCache = object : LinkedHashMap<String, String>(30, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, String>?) = size > 30
    }
    private val summaryCache = mutableMapOf<String, SummaryEntity?>()
    private val versesCache = mutableMapOf<String, List<VerseEntity>>()
    private val summaryLoadingKeys = mutableSetOf<String>()

    val books: Flow<List<BookEntity>> = flowOf(BookCatalog.all.map { BookCatalog.toEntity(it) })
    val currentBookId: StateFlow<Int> = selectedBookId.asStateFlow()

    val verses: Flow<List<VerseEntity>> = combine(selectedBookId, selectedChapter) { bookId, chapter ->
        bookId to chapter
    }
        .distinctUntilChanged()
        .flatMapLatest { (bookId, chapter) ->
            val key = "$bookId-$chapter"
            val cached = versesCache[key]
            try {
                dao.getVerses(bookId, chapter)
                    .distinctUntilChanged()
                    .map { list ->
                        if (list.isNotEmpty()) versesCache[key] = list
                        if (list.isEmpty() && cached != null) cached else list
                    }
                    .onStart { if (cached != null) emit(cached) }
            } catch (e: Exception) {
                flowOf(cached ?: emptyList())
            }
        }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    val highlights: Flow<Map<Int, Color>> = combine(selectedBookId, selectedChapter) { bookId, chapter ->
        bookId to chapter
    }
        .distinctUntilChanged()
        .flatMapLatest { (bookId, chapter) ->
            try {
                dao.getHighlights(bookId, chapter).map { list ->
                    list.associate { it.verse to parseHighlightColor(it.color) }
                }
            } catch (e: Exception) { flowOf(emptyMap()) }
        }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    val notes: Flow<List<NoteEntity>> = try { dao.getAllNotes() } catch (e: Exception) { flowOf(emptyList()) }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // FIX: Restore FIRST, then start all book/chapter dependent collectors.
        // This stops Genesis 1 -> Exodus 3 double-load.
        viewModelScope.launch {
            restoreSavedReading()

            launch { books.collect { list -> _uiState.update { it.copy(books = list) } } }
            launch { verses.collect { list -> _uiState.update { it.copy(verses = list) } } }
            launch { highlights.collect { map -> _uiState.update { it.copy(highlights = map) } } }
            launch { notes.collect { list -> _uiState.update { it.copy(notes = list) } } }
            launch {
                selectedBookId.collect { id ->
                    _uiState.update { it.copy(currentBookId = id, currentBook = BookCatalog.name(id), selectedBook = BookCatalog.name(id)) }
                }
            }
            launch { selectedChapter.collect { c -> _uiState.update { it.copy(selectedChapter = c) } } }
            launch { selectedVerse.collect { v -> _uiState.update { it.copy(selectedVerse = v) } } }
            launch { selectedTab.collect { t -> _uiState.update { it.copy(selectedTab = t) } } }
            launch { commentary.collect { h -> _uiState.update { it.copy(commentaryHtml = h) } } }
            launch { commentaryError.collect { e -> _uiState.update { it.copy(commentaryError = e) } } }
            launch {
                summaryState.collect { s ->
                    _uiState.update {
                        it.copy(summary = s, summaryText = s?.summary ?: "", summaryWhatHappened = s?.whatHappened ?: "", summaryApplication = s?.application ?: "", summaryItalicText = s?.summary ?: "")
                    }
                }
            }
            launch { summaryLoading.collect { b -> _uiState.update { it.copy(summaryLoading = b) } } }
            launch { chapterCounts.collect { m -> _uiState.update { it.copy(chapterCounts = m) } } }
            launch { searchResults.collect { r -> _uiState.update { it.copy(searchResults = r) } } }
            launch { searchFilter.collect { f -> _uiState.update { it.copy(searchFilter = f) } } }
            launch { dailyVerse.collect { d -> _uiState.update { it.copy(dailyVerse = d) } } }
            launch { scrollRestoreKey.collect { k -> _uiState.update { it.copy(scrollRestoreKey = k) } } }
            launch { linkedVerseText.collect { t -> _uiState.update { it.copy(linkedVerseText = t) } } }
            launch { linkedVerseRef.collect { r -> _uiState.update { it.copy(linkedVerseRef = r) } } }
            launch { showNoteDialogState.collect { b -> _uiState.update { it.copy(showNoteDialog = b) } } }
            launch { currentNoteText.collect { t -> _uiState.update { it.copy(currentNoteText = t) } } }
            launch { isReadingRestored.collect { b -> _uiState.update { it.copy(isReadingRestored = b) } } }

            // Summary for restored chapter - only once, after restore
            launch {
                combine(currentBookId, selectedChapter) { bookId, chapter -> bookId to chapter }
                    .distinctUntilChanged()
                    .collectLatest { (bookId, chapter) ->
                        loadSummary(bookId, chapter)
                    }
            }

            // Commentary only when on Open EW tab, only after restore
            launch {
                selectedTab.collectLatest { tab ->
                    if (tab == "Open EW") loadCommentary(currentBookId.value, selectedChapter.value)
                }
            }

            loadSummary(selectedBookId.value, selectedChapter.value)
        }

        viewModelScope.launch {
            runCatching {
                prefs.isDarkMode.collect { d ->
                    _uiState.update { it.copy(darkMode = d) }
                }
            }
        }
        viewModelScope.launch {
            runCatching {
                prefs.fontSize.collect { f ->
                    _uiState.update { it.copy(fontSize = f) }
                }
            }
        }
        viewModelScope.launch {
            runCatching {
                prefs.userName.collect { n ->
                    _uiState.update { it.copy(userName = n, isLoggedIn = n != "Guest" && _uiState.value.userEmail.isNotBlank()) }
                }
            }
        }
        viewModelScope.launch {
            runCatching {
                prefs.userEmail.collect { e ->
                    _uiState.update { it.copy(userEmail = e, isLoggedIn = _uiState.value.userName != "Guest" && e.isNotBlank()) }
                }
            }
        }

        viewModelScope.launch {
            runCatching {
                val fromDb = dao.getChapterCounts().associate { it.bookId to it.chapterCount }
                if (fromDb.isNotEmpty()) {
                    val catalog = BookCatalog.all.associate { it.id to it.chapters }
                    chapterCounts.value = catalog.mapValues { (id, catalogCount) -> maxOf(catalogCount, fromDb[id] ?: 0) }
                }
            }.onFailure { Log.e("HomeViewModel", "getChapterCounts failed", it) }
        }

        viewModelScope.launch {
            combine(searchQuery.debounce(300L).distinctUntilChanged(), searchFilter) { q, f -> q to f }
                .collectLatest { (query, filter) ->
                    if (query.isBlank()) { searchResults.value = emptyList(); return@collectLatest }
                    runCatching {
                        val raw = dao.searchVerses(query)
                        searchResults.value = when (filter) {
                            "Old Testament", "OT" -> raw.filter { it.bookId <= 39 }
                            "New Testament", "NT" -> raw.filter { it.bookId >= 40 }
                            "Gospel" -> raw.filter { it.bookId in 40..43 }
                            "Prophecy" -> raw.filter { it.bookId in setOf(23, 24, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 66) }
                            else -> raw
                        }
                    }
                }
        }
        loadDailyVerse()
    }

    fun selectBook(bookId: Int) {
        selectedBookId.value = bookId.coerceIn(1, 66)
        selectedChapter.value = 1
        selectedVerse.value = null
        scrollRestoreKey.update { it + 1 }
        saveReadingProgress()
    }
    fun selectBook(bookName: String) = selectBook(BookCatalog.id(bookName))
    fun selectChapter(chapter: Int) {
        val max = BookCatalog.chapters(currentBookId.value)
        selectedChapter.value = chapter.coerceIn(1, max)
        selectedVerse.value = null
        scrollRestoreKey.update { it + 1 }
        saveReadingProgress()
    }
    fun previousChapter() {
        if (selectedChapter.value > 1) { selectChapter(selectedChapter.value - 1); return }
        val prevBookId = currentBookId.value - 1
        if (prevBookId < 1) return
        selectedBookId.value = prevBookId
        selectedChapter.value = BookCatalog.chapters(prevBookId)
        selectedVerse.value = null
        scrollRestoreKey.update { it + 1 }
        saveReadingProgress()
    }
    fun nextChapter() {
        val max = BookCatalog.chapters(currentBookId.value)
        if (selectedChapter.value < max) { selectChapter(selectedChapter.value + 1); return }
        val nextBookId = currentBookId.value + 1
        if (nextBookId > 66) return
        selectedBookId.value = nextBookId
        selectedChapter.value = 1
        selectedVerse.value = null
        scrollRestoreKey.update { it + 1 }
        saveReadingProgress()
    }
    fun selectVerse(verse: Int?) {
        selectedVerse.value = verse
        if (verse != null) {
            viewModelScope.launch {
                runCatching {
                    val note = dao.getNoteAtVerse(currentBookId.value, selectedChapter.value, verse)
                    currentNoteText.value = note?.noteText ?: ""
                }
            }
        }
    }
    fun clearSelection() { selectedVerse.value = null }
    fun openBook(bookId: Int, chapter: Int) {
        val safeId = bookId.coerceIn(1, 66)
        selectedBookId.value = safeId
        selectedChapter.value = chapter.coerceIn(1, BookCatalog.chapters(safeId))
        selectedVerse.value = null
        scrollRestoreKey.update { it + 1 }
        saveReadingProgress()
    }
    fun openBook(bookName: String, chapter: Int) = openBook(BookCatalog.id(bookName), chapter)
    fun selectTab(tab: String) {
        selectedTab.value = tab
        if (tab == "Open EW") loadCommentary(currentBookId.value, selectedChapter.value)
    }
    fun searchVerses(query: String) { searchQuery.value = query }
    fun setSearchQuery(query: String) { searchQuery.value = query }
    fun setSearchFilter(filter: String) { searchFilter.value = filter }
    fun setDarkMode(enabled: Boolean) { viewModelScope.launch { runCatching { prefs.setDarkMode(enabled) } } }
    fun setFontSize(size: Float) { viewModelScope.launch { runCatching { prefs.setFontSize(size) } } }
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
            Log.e("HomeViewModel", "restoreSavedReading failed, using Genesis 1", e)
            selectedBookId.value = 1
            selectedChapter.value = 1
        } finally {
            restoreDone = true
            isReadingRestored.value = true
        }
    }

    fun loadSummary(bookId: Int = currentBookId.value, chapter: Int = selectedChapter.value) {
        val key = "$bookId-$chapter"
        summaryCache[key]?.let { cached ->
            summaryState.value = cached
            _summaryLoading.value = false
            return
        }
        if (key in summaryLoadingKeys) return
        summaryLoadingKeys.add(key)
        viewModelScope.launch {
            _summaryLoading.value = true
            runCatching {
                val s = dao.getSummary(bookId, chapter)
                summaryCache[key] = s
                summaryState.value = s
            }
            summaryLoadingKeys.remove(key)
            _summaryLoading.value = false
        }
    }
    fun loadSummary() { loadSummary(currentBookId.value, selectedChapter.value) }

    fun loadLinkedVerse(bookName: String, chapter: Int, verse: Int, endVerse: Int = verse) {
        viewModelScope.launch {
            val bookId = runCatching { BookCatalog.id(bookName) }.getOrDefault(1)
            val end = maxOf(verse, endVerse)
            linkedVerseRef.value = if (end > verse) "${BookCatalog.name(bookId)} $chapter:$verse-$end"
                                   else "${BookCatalog.name(bookId)} $chapter:$verse"
            linkedVerseText.value = "Loading..."
            runCatching {
                val list = dao.getVerses(bookId, chapter).first()
                val range = list.filter { it.verse in verse..end }.sortedBy { it.verse }
                linkedVerseText.value = if (range.isEmpty()) "Verse not found."
                    else range.joinToString("\n\n") { "${it.verse}. ${it.text}" }
            }.onFailure { linkedVerseText.value = "Could not load verse." }
        }
    }

    fun loadCommentary(bookId: Int = currentBookId.value, chapter: Int = selectedChapter.value) {
        val key = "$bookId-$chapter"
        commentaryCache[key]?.let {
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
                    if (compressed == null || compressed.isEmpty()) null else decompressCommentary(compressed)
                }
                if (html.isNullOrBlank()) {
                    val fallback = "<html><body><p><i>Commentary for ${BookCatalog.name(bookId)} $chapter is not available yet.</i></p></body></html>"
                    commentaryCache[key] = fallback
                    commentary.value = fallback
                    commentaryError.value = false
                } else {
                    commentaryCache[key] = html
                    commentary.value = html
                    commentaryError.value = false
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                Log.e("HomeViewModel", "loadCommentary failed book=$bookId ch=$chapter", e)
                val fallback = "<html><body><p><i>Commentary for ${BookCatalog.name(bookId)} $chapter is not available yet.</i></p></body></html>"
                commentary.value = fallback
                commentaryError.value = false
            }
        }
    }
    fun loadCommentary() { loadCommentary(currentBookId.value, selectedChapter.value) }

    private fun decompressCommentary(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        if (bytes.size > 2 && bytes[0] == '<'.code.toByte()) return String(bytes, Charsets.UTF_8)
        val isGzip = bytes.size >= 2 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()
        val isZlib = bytes.size >= 2 && bytes[0] == 0x78.toByte()
        return try {
            when {
                isGzip -> GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes().toString(Charsets.UTF_8) }
                isZlib -> {
                    val inflater = Inflater()
                    try {
                        inflater.setInput(bytes)
                        val out = ByteArrayOutputStream(bytes.size * 3)
                        val buf = ByteArray(16384)
                        while (!inflater.finished()) {
                            val n = inflater.inflate(buf)
                            if (n == 0) break
                            out.write(buf, 0, n)
                        }
                        out.toString(Charsets.UTF_8.name())
                    } finally { inflater.end() }
                }
                else -> {
                    val inflater = Inflater(true)
                    try {
                        inflater.setInput(bytes)
                        val out = ByteArrayOutputStream(bytes.size * 3)
                        val buf = ByteArray(16384)
                        while (!inflater.finished()) {
                            val n = inflater.inflate(buf)
                            if (n == 0) break
                            out.write(buf, 0, n)
                        }
                        out.toString(Charsets.UTF_8.name())
                    } finally { inflater.end() }
                }
            }
        } catch (e: Exception) {
            Log.e("HomeViewModel", "decompress failed, returning raw", e)
            String(bytes, Charsets.UTF_8)
        }
    }

    fun loadDailyVerse() {
        viewModelScope.launch {
            runCatching {
                val verse = dao.getRandomVerse()
                dailyVerse.value = verse?.let { "${it.text}\n— ${BookCatalog.name(it.bookId)} ${it.chapter}:${it.verse}" }
                    ?: "Thy word is a lamp unto my feet, and a light unto my path.\n— Psalm 119:105"
            }.onFailure { dailyVerse.value = "Thy word is a lamp unto my feet, and a light unto my path.\n— Psalm 119:105" }
        }
    }
    fun showNoteDialog() { if (selectedVerse.value == null) return; showNoteDialogState.value = true }
    fun dismissNoteDialog() { showNoteDialogState.value = false }
    fun saveNote(noteText: String) {
        val verseNum = selectedVerse.value ?: return
        viewModelScope.launch {
            runCatching {
                val existing = dao.getNoteAtVerse(currentBookId.value, selectedChapter.value, verseNum)
                if (existing != null) dao.updateNote(existing.copy(noteText = noteText, timestamp = System.currentTimeMillis()))
                else dao.saveNote(NoteEntity(bookId = currentBookId.value, chapter = selectedChapter.value, verse = verseNum, noteText = noteText, timestamp = System.currentTimeMillis()))
                currentNoteText.value = noteText
            }
        }
    }
    fun saveHighlight(color: Color) {
        val verseNum = selectedVerse.value ?: return
        viewModelScope.launch { runCatching { dao.saveHighlight(HighlightEntity(bookId = currentBookId.value, chapter = selectedChapter.value, verse = verseNum, color = color.toArgb().toString())) } }
    }
    fun removeHighlight() {
        val verseNum = selectedVerse.value ?: return
        viewModelScope.launch { runCatching { dao.deleteHighlight(currentBookId.value, selectedChapter.value, verseNum) } }
    }
    private fun parseHighlightColor(colorStr: String): Color {
        return runCatching { Color(colorStr.toInt()) }.getOrDefault(Color(0xFFFFF176))
    }
}