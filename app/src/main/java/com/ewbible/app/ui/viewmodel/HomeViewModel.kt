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
    private val showNoteDialog = MutableStateFlow(false)
    private val currentNoteText = MutableStateFlow("")
    private val commentary = MutableStateFlow("")
    private val commentaryError = MutableStateFlow(false)
    private val summaryState = MutableStateFlow<SummaryEntity?>(null)
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

    val books: Flow<List<BookEntity>> = flowOf(BookCatalog.all.map { BookCatalog.toEntity(it) })
    val currentBookId: StateFlow<Int> = selectedBookId.asStateFlow()
    private val selectedBookName: Flow<String> = selectedBookId.map { BookCatalog.name(it) }

    val verses: Flow<List<VerseEntity>> = selectedBookId.flatMapLatest { bookId ->
        selectedChapter.flatMapLatest { chapter -> dao.getVerses(bookId, chapter) }
    }
    val highlights: Flow<Map<Int, Color>> = selectedBookId.flatMapLatest { bookId ->
        selectedChapter.flatMapLatest { chapter ->
            dao.getHighlights(bookId, chapter).map { list ->
                list.associate { it.verse to parseHighlightColor(it.color) }
            }
        }
    }
    val notes: Flow<List<NoteEntity>> = dao.getAllNotes()

    // Split combine into 3 groups because combine() max is 20 flows
    private val readingFlow = combine(
        books, verses, highlights, notes,
        currentBookId, selectedBookName, selectedChapter, selectedVerse, selectedTab
    ) { booksList, versesList, highlightsMap, notesList, bookId, bookName, chapter, verse, tab ->
        // Use Triple / data holder via list to keep types safe - re-packed below
        ReadingPart(booksList, versesList, highlightsMap, notesList, bookId, bookName, chapter, verse, tab)
    }

    private val contentFlow = combine(
        commentary, showNoteDialog, currentNoteText,
        linkedVerseText, linkedVerseRef, commentaryError,
        summaryState, chapterCounts, searchResults, searchFilter,
        dailyVerse, scrollRestoreKey
    ) { commentaryHtml, showNote, noteText, linkedText, linkedRef, commentaryErr, summary, counts, searchRes, filter, daily, scrollKey ->
        ContentPart(commentaryHtml, showNote, noteText, linkedText, linkedRef, commentaryErr, summary, counts, searchRes, filter, daily, scrollKey)
    }

    private val prefsFlow = combine(
        prefs.isDarkMode, prefs.fontSize, prefs.userName, prefs.userEmail, isReadingRestored
    ) { dark, font, user, email, restored ->
        PrefsPart(dark, font, user, email, restored)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        readingFlow, contentFlow, prefsFlow
    ) { reading, content, pref ->
        HomeUiState(
            books = reading.books,
            verses = reading.verses,
            highlights = reading.highlights,
            notes = reading.notes,
            currentBookId = reading.bookId,
            currentBook = reading.bookName,
            selectedBook = reading.bookName,
            selectedChapter = reading.chapter,
            selectedVerse = reading.verse,
            selectedTab = reading.tab,
            commentaryHtml = content.commentaryHtml,
            commentaryError = content.commentaryErr,
            summary = content.summary,
            summaryText = content.summary?.summary ?: "",
            summaryWhatHappened = content.summary?.whatHappened ?: "",
            summaryApplication = content.summary?.application ?: "",
            summaryItalicText = content.summary?.summary ?: "",
            linkedVerseText = content.linkedText,
            linkedVerseRef = content.linkedRef,
            chapterCounts = content.counts,
            searchResults = content.searchRes,
            searchFilter = content.filter,
            dailyVerse = content.daily,
            darkMode = pref.dark,
            fontSize = pref.font,
            userName = pref.user,
            userEmail = pref.email,
            isLoggedIn = pref.user != "Guest" && pref.email.isNotBlank(),
            scrollRestoreKey = content.scrollKey,
            showNoteDialog = content.showNote,
            currentNoteText = content.noteText,
            isReadingRestored = pref.restored
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    private data class ReadingPart(
        val books: List<BookEntity>,
        val verses: List<VerseEntity>,
        val highlights: Map<Int, Color>,
        val notes: List<NoteEntity>,
        val bookId: Int,
        val bookName: String,
        val chapter: Int,
        val verse: Int?,
        val tab: String
    )

    private data class ContentPart(
        val commentaryHtml: String,
        val showNote: Boolean,
        val noteText: String,
        val linkedText: String,
        val linkedRef: String,
        val commentaryErr: Boolean,
        val summary: SummaryEntity?,
        val counts: Map<Int, Int>,
        val searchRes: List<VerseSearchResult>,
        val filter: String,
        val daily: String,
        val scrollKey: Int
    )

    private data class PrefsPart(
        val dark: Boolean?,
        val font: Float,
        val user: String,
        val email: String,
        val restored: Boolean
    )

    init {
        viewModelScope.launch { restoreSavedReading() }
        viewModelScope.launch {
            runCatching {
                val fromDb = dao.getChapterCounts().associate { it.bookId to it.chapterCount }
                if (fromDb.isNotEmpty()) {
                    val catalog = BookCatalog.all.associate { it.id to it.chapters }
                    chapterCounts.value = catalog.mapValues { (id, catalogCount) ->
                        maxOf(catalogCount, fromDb[id] ?: 0)
                    }
                }
            }.onFailure {
                Log.e("HomeViewModel", "getChapterCounts failed, keeping BookCatalog counts", it)
            }
        }
        viewModelScope.launch {
            combine(currentBookId, selectedChapter, selectedTab) { bookId, chapter, tab -> Triple(bookId, chapter, tab) }
                .collectLatest { (bookId, chapter, tab) ->
                    loadSummary(bookId, chapter)
                    if (tab == "Open EW") loadCommentary(bookId, chapter)
                }
        }
        viewModelScope.launch {
            combine(searchQuery.debounce(300L).distinctUntilChanged(), searchFilter) { q, f -> q to f }
                .collectLatest { (query, filter) ->
                    if (query.isBlank()) searchResults.value = emptyList()
                    else {
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
        if (selectedChapter.value > 1) {
            selectChapter(selectedChapter.value - 1)
            return
        }
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
        if (selectedChapter.value < max) {
            selectChapter(selectedChapter.value + 1)
            return
        }
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
                val note = dao.getNoteAtVerse(currentBookId.value, selectedChapter.value, verse)
                currentNoteText.value = note?.noteText ?: ""
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
    fun setDarkMode(enabled: Boolean) { viewModelScope.launch { prefs.setDarkMode(enabled) } }
    fun setFontSize(size: Float) { viewModelScope.launch { prefs.setFontSize(size) } }
    fun onUserSignedIn(name: String, email: String) { viewModelScope.launch { prefs.saveUser(name, email) } }
    fun onUserSignedOut() { viewModelScope.launch { prefs.clearUser() } }
    fun saveReadingProgress(verse: VerseEntity? = null, scrollIndex: Int = 0, scrollOffset: Int = 0) {
        viewModelScope.launch {
            prefs.saveLastReading(bookId = currentBookId.value, chapter = selectedChapter.value, verse = verse?.verse ?: (selectedVerse.value ?: 1), scrollIndex = scrollIndex, scrollOffset = scrollOffset)
        }
    }
    private suspend fun restoreSavedReading() {
        try {
            val lastBookId = prefs.getLastBookId()
            val lastChapter = prefs.getLastChapter()
            if (lastBookId in 1..66) {
                selectedBookId.value = lastBookId
                selectedChapter.value = lastChapter.coerceIn(1, BookCatalog.chapters(lastBookId))
            }
        } finally {
            isReadingRestored.value = true
        }
    }
    fun loadSummary(bookId: Int = currentBookId.value, chapter: Int = selectedChapter.value) {
        viewModelScope.launch { summaryState.value = dao.getSummary(bookId, chapter) }
    }
    fun loadSummary() { loadSummary(currentBookId.value, selectedChapter.value) }
    fun loadLinkedVerse(bookName: String, chapter: Int, verse: Int) {
        viewModelScope.launch {
            val bookId = BookCatalog.id(bookName)
            linkedVerseRef.value = "${BookCatalog.name(bookId)} $chapter:$verse"
            linkedVerseText.value = "Loading..."
            runCatching {
                val list = dao.getVerses(bookId, chapter).first()
                linkedVerseText.value = list.firstOrNull { it.verse == verse }?.text ?: "Verse not found."
            }.onFailure { linkedVerseText.value = "Could not load verse." }
        }
    }

    fun loadCommentary(bookId: Int = currentBookId.value, chapter: Int = selectedChapter.value) {
        commentaryJob?.cancel()
        commentaryJob = viewModelScope.launch {
            commentaryError.value = false
            try {
                val html = withContext(Dispatchers.IO) {
                    val compressed = dao.getCommentaryCompressed(bookId, chapter)
                    if (compressed == null) null else decompressCommentary(compressed)
                }
                if (html.isNullOrBlank()) { commentary.value = ""; commentaryError.value = true }
                else { commentary.value = html; commentaryError.value = false }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                Log.e("HomeViewModel", "loadCommentary failed book=$bookId ch=$chapter", e)
                commentaryError.value = true
                commentary.value = ""
            }
        }
    }
    fun loadCommentary() { loadCommentary(currentBookId.value, selectedChapter.value) }

    private fun decompressCommentary(bytes: ByteArray): String {
        runCatching {
            val inflater = Inflater()
            inflater.setInput(bytes)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0) break
                out.write(buf, 0, n)
            }
            inflater.end()
            out.toString(Charsets.UTF_8.name())
        }.onSuccess { if (it.isNotBlank()) return it }

        runCatching {
            GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.onSuccess { if (it.isNotBlank()) return it }

        runCatching {
            val inflater = Inflater(true)
            inflater.setInput(bytes)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0) break
                out.write(buf, 0, n)
            }
            inflater.end()
            out.toString(Charsets.UTF_8.name())
        }.onSuccess { if (it.isNotBlank()) return it }

        return String(bytes, Charsets.UTF_8)
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
    fun showNoteDialog() { if (selectedVerse.value == null) return; showNoteDialog.value = true }
    fun dismissNoteDialog() { showNoteDialog.value = false }
    fun saveNote(noteText: String) {
        val verseNum = selectedVerse.value ?: return
        viewModelScope.launch {
            val existing = dao.getNoteAtVerse(currentBookId.value, selectedChapter.value, verseNum)
            if (existing != null) dao.updateNote(existing.copy(noteText = noteText, timestamp = System.currentTimeMillis()))
            else dao.saveNote(NoteEntity(bookId = currentBookId.value, chapter = selectedChapter.value, verse = verseNum, noteText = noteText, timestamp = System.currentTimeMillis()))
            currentNoteText.value = noteText
        }
    }
    fun saveHighlight(color: Color) {
        val verseNum = selectedVerse.value ?: return
        viewModelScope.launch { dao.saveHighlight(HighlightEntity(bookId = currentBookId.value, chapter = selectedChapter.value, verse = verseNum, color = color.toArgb().toString())) }
    }
    fun removeHighlight() {
        val verseNum = selectedVerse.value ?: return
        viewModelScope.launch { dao.deleteHighlight(currentBookId.value, selectedChapter.value, verseNum) }
    }
    private fun parseHighlightColor(colorStr: String): Color {
        return runCatching { Color(colorStr.toInt()) }.getOrDefault(Color(0xFFFFF176))
    }
}