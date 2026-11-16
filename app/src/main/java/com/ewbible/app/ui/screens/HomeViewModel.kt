package com.ewbible.app.ui.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ewbible.app.data.database.*
import com.ewbible.app.data.preferences.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    val summaryText: String = "",
    val commentaryHtml: String = "",
    val commentaryError: Boolean = false,
    val chapterCounts: Map<Int, Int> = emptyMap(),
    val searchResults: List<VerseSearchResult> = emptyList(),
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
    val currentNoteText: String = ""
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class HomeViewModel(
    private val dao: BibleDao,
    private val prefs: AppPreferences
) : ViewModel() {

    private val selectedBook = MutableStateFlow("Genesis")
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
    private val chapterCounts = MutableStateFlow<Map<Int, Int>>(emptyMap())
    private val searchResults = MutableStateFlow<List<VerseSearchResult>>(emptyList())
    private val dailyVerse = MutableStateFlow("")
    private val scrollRestoreKey = MutableStateFlow(0)

    private var commentaryJob: Job? = null

    val books: Flow<List<BookEntity>> = dao.getAllBooks()

    val currentBookId: StateFlow<Int> = combine(
        books,
        selectedBook
    ) { bookList, bookName ->
        bookList.firstOrNull {
            it.name.equals(bookName, ignoreCase = true) ||
                it.key.equals(bookName, ignoreCase = true)
        }?.id ?: bookList.firstOrNull()?.id ?: 1
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = 1
    )

    val verses: Flow<List<VerseEntity>> = currentBookId
        .flatMapLatest { bookId ->
            selectedChapter.flatMapLatest { chapter ->
                dao.getVerses(bookId, chapter)
            }
        }

    val highlights: Flow<Map<Int, Color>> = currentBookId
        .flatMapLatest { bookId ->
            selectedChapter.flatMapLatest { chapter ->
                dao.getHighlights(bookId, chapter).map { highlightList ->
                    highlightList.associate { highlight ->
                        highlight.verse to parseHighlightColor(highlight.color)
                    }
                }
            }
        }

    val notes: Flow<List<NoteEntity>> = dao.getAllNotes()

    val uiState: StateFlow<HomeUiState> = combine(
        books, verses, highlights, notes, currentBookId,
        selectedBook, selectedChapter, selectedVerse, selectedTab,
        commentary, commentaryError, summaryState, chapterCounts,
        searchResults, dailyVerse, scrollRestoreKey,
        showNoteDialog, currentNoteText,
        prefs.isDarkMode, prefs.fontSize, prefs.userName, prefs.userEmail
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val books = args[0] as List<BookEntity>
        val verses = args[1] as List<VerseEntity>
        val highlights = args[2] as Map<Int, Color>
        val notes = args[3] as List<NoteEntity>
        val bookId = args[4] as Int
        val selBook = args[5] as String
        val selChapter = args[6] as Int
        val selVerse = args[7] as Int?
        val selTab = args[8] as String
        val comm = args[9] as String
        val commErr = args[10] as Boolean
        val summary = args[11] as SummaryEntity?
        val counts = args[12] as Map<Int, Int>
        val searchRes = args[13] as List<VerseSearchResult>
        val daily = args[14] as String
        val restoreKey = args[15] as Int
        val showNote = args[16] as Boolean
        val noteText = args[17] as String
        val dark = args[18] as Boolean?
        val font = args[19] as Float
        val uName = args[20] as String
        val uEmail = args[21] as String
        HomeUiState(
            books = books,
            verses = verses,
            highlights = highlights,
            notes = notes,
            currentBookId = bookId,
            currentBook = selBook,
            selectedBook = selBook,
            selectedChapter = selChapter,
            selectedVerse = selVerse,
            selectedTab = selTab,
            commentaryHtml = comm,
            commentaryError = commErr,
            summary = summary,
            summaryText = summary?.summaryText ?: summary?.text ?: "",
            chapterCounts = counts,
            searchResults = searchRes,
            dailyVerse = daily,
            darkMode = dark,
            fontSize = font,
            userName = uName,
            userEmail = uEmail,
            isLoggedIn = uName != "Guest" && uEmail.isNotBlank(),
            scrollRestoreKey = restoreKey,
            showNoteDialog = showNote,
            currentNoteText = noteText
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    init {
        viewModelScope.launch {
            restoreSavedReading()
        }
        viewModelScope.launch {
            chapterCounts.value = dao.getChapterCounts().associate { it.bookId to it.chapterCount }
        }
        viewModelScope.launch {
            combine(currentBookId, selectedChapter, selectedTab) { bookId, chapter, tab ->
                Triple(bookId, chapter, tab)
            }.collectLatest { (bookId, chapter, tab) ->
                loadSummary(bookId, chapter)
                if (tab == "Open EW") {
                    loadCommentary(bookId, chapter)
                }
            }
        }
        viewModelScope.launch {
            combine(
                searchQuery.debounce(300L).distinctUntilChanged(),
                searchFilter
            ) { query, filter -> query to filter }
                .collectLatest { (query, filter) ->
                    if (query.isBlank()) {
                        searchResults.value = emptyList()
                    } else {
                        val rawResults = dao.searchVerses(query)
                        searchResults.value = when (filter) {
                            "OT" -> rawResults.filter { it.bookId <= 39 }
                            "NT" -> rawResults.filter { it.bookId >= 40 }
                            else -> rawResults
                        }
                    }
                }
        }
        viewModelScope.launch {
            loadDailyVerse()
        }
    }

    fun selectBook(bookName: String) {
        selectedBook.value = bookName
        selectedChapter.value = 1
        selectedVerse.value = null
        scrollRestoreKey.update { it + 1 }
        saveReadingProgress()
    }

    fun selectChapter(chapter: Int) {
        selectedChapter.value = chapter
        selectedVerse.value = null
        scrollRestoreKey.update { it + 1 }
        saveReadingProgress()
    }

    // FIXED: Added - HomeScreen chapter +/- buttons call these
    fun previousChapter() {
        if (selectedChapter.value > 1) selectChapter(selectedChapter.value - 1)
    }

    fun nextChapter() {
        val max = chapterCounts.value[currentBookId.value] ?: 1
        if (selectedChapter.value < max) selectChapter(selectedChapter.value + 1)
    }

    fun selectVerse(verse: Int?) {
        selectedVerse.value = verse
        // Load existing note text for this verse so dialog shows it
        if (verse != null) {
            viewModelScope.launch {
                val note = dao.getNoteAtVerse(currentBookId.value, selectedChapter.value, verse)
                currentNoteText.value = note?.noteText ?: ""
            }
        }
    }

    // FIXED: Added - HomeScreen calls this to clear selection
    fun clearSelection() {
        selectedVerse.value = null
    }

    fun openBook(bookName: String, chapter: Int) {
        selectedBook.value = bookName
        selectedChapter.value = chapter
        selectedVerse.value = null
        scrollRestoreKey.update { it + 1 }
        saveReadingProgress()
    }

    fun selectTab(tab: String) {
        selectedTab.value = tab
        if (tab == "Open EW") {
            loadCommentary(currentBookId.value, selectedChapter.value)
        }
    }

    // FIXED: Added - HomeScreen Search tab calls this directly
    fun searchVerses(query: String) {
        searchQuery.value = query
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setSearchFilter(filter: String) {
        searchFilter.value = filter
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { prefs.setDarkMode(enabled) }
    }

    fun setFontSize(size: Float) {
        viewModelScope.launch { prefs.setFontSize(size) }
    }

    fun saveReadingProgress(verse: VerseEntity? = null, scrollIndex: Int = 0, scrollOffset: Int = 0) {
        viewModelScope.launch {
            prefs.saveLastReading(
                bookId = currentBookId.value,
                chapter = selectedChapter.value,
                verse = verse?.verse ?: (selectedVerse.value ?: 1),
                scrollIndex = scrollIndex,
                scrollOffset = scrollOffset
            )
        }
    }

    private suspend fun restoreSavedReading() {
        val lastBookId = prefs.getLastBookId()
        val lastChapter = prefs.getLastChapter()
        val allBooks = dao.getAllBooksSync()
        val book = allBooks.firstOrNull { it.id == lastBookId }
        if (book != null) {
            selectedBook.value = book.name
            selectedChapter.value = lastChapter
        }
    }

    fun loadSummary(bookId: Int = currentBookId.value, chapter: Int = selectedChapter.value) {
        viewModelScope.launch {
            summaryState.value = dao.getSummary(bookId, chapter)
        }
    }

    fun loadSummary() {
        loadSummary(currentBookId.value, selectedChapter.value)
    }

    fun loadCommentary(bookId: Int = currentBookId.value, chapter: Int = selectedChapter.value) {
        commentaryJob?.cancel()
        commentaryJob = viewModelScope.launch {
            commentaryError.value = false
            runCatching {
                val html = withContext(Dispatchers.IO) {
                    val comm = dao.getCommentary(bookId, chapter)
                    comm?.getHtml().orEmpty()
                }
                commentary.value = if (html.isBlank()) {
                    "<p>No commentary available for this chapter.</p>"
                } else {
                    html
                }
            }.onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                commentaryError.value = true
                commentary.value = "<p>Failed to load commentary.</p>"
            }
        }
    }

    fun loadCommentary() {
        loadCommentary(currentBookId.value, selectedChapter.value)
    }

    private suspend fun loadDailyVerse() {
        runCatching {
            val verse = dao.getRandomVerse()
            dailyVerse.value = verse?.let { "${it.text} — ${it.bookName} ${it.chapter}:${it.verse}" }
                ?: "Thy word is a lamp unto my feet, and a light unto my path. — Psalm 119:105"
        }.onFailure {
            dailyVerse.value = "Thy word is a lamp unto my feet, and a light unto my path. — Psalm 119:105"
        }
    }

    // FIXED: Added - Note dialog controls
    fun showNoteDialog() {
        if (selectedVerse.value == null) return
        showNoteDialog.value = true
    }

    fun dismissNoteDialog() {
        showNoteDialog.value = false
    }

    fun saveNote(noteText: String) {
        val verseNum = selectedVerse.value ?: return
        viewModelScope.launch {
            dao.saveNote(
                NoteEntity(
                    bookId = currentBookId.value,
                    chapter = selectedChapter.value,
                    verse = verseNum,
                    noteText = noteText,
                    timestamp = System.currentTimeMillis()
                )
            )
            currentNoteText.value = noteText
        }
    }

    // FIXED: Added - HomeScreen Highlight button calls this (toggle)
    fun toggleHighlight() {
        val verseNum = selectedVerse.value ?: return
        viewModelScope.launch {
            val existing = dao.getHighlights(currentBookId.value, selectedChapter.value).first()
                .firstOrNull { it.verse == verseNum }
            if (existing != null) {
                dao.deleteHighlight(currentBookId.value, selectedChapter.value, verseNum)
            } else {
                dao.saveHighlight(
                    HighlightEntity(
                        bookId = currentBookId.value,
                        chapter = selectedChapter.value,
                        verse = verseNum,
                        color = Color(0xFFFFF176).toArgb().toString()
                    )
                )
            }
        }
    }

    fun saveHighlight(color: Color) {
        val verseNum = selectedVerse.value ?: return
        viewModelScope.launch {
            dao.saveHighlight(
                HighlightEntity(
                    bookId = currentBookId.value,
                    chapter = selectedChapter.value,
                    verse = verseNum,
                    color = color.toArgb().toString()
                )
            )
        }
    }

    fun removeHighlight() {
        val verseNum = selectedVerse.value ?: return
        viewModelScope.launch {
            dao.deleteHighlight(currentBookId.value, selectedChapter.value, verseNum)
        }
    }

    private fun parseHighlightColor(colorStr: String): Color {
        return runCatching {
            Color(colorStr.toInt())
        }.getOrDefault(Color(0xFFFFF176))
    }
}