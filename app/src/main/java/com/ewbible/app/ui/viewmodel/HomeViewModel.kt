package com.ewbible.app.ui.viewmodel

import android.util.Log
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
    val summaryWhatHappened: String = "",
    val summaryApplication: String = "",
    val summaryItalicText: String = "",
    val commentaryHtml: String = "",
    val commentaryError: Boolean = false,
    val linkedVerseText: String = "",
    val linkedVerseRef: String = "",
    val chapterCounts: Map<Int, Int> = emptyMap(),
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
    val currentNoteText: String = ""
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class HomeViewModel(
    private val dao: BibleDao,
    private val prefs: AppPreferences
) : ViewModel() {

    // WIRED: ID is source of truth. Never resolve ID from DB books by name again.
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
    private val chapterCounts = MutableStateFlow<Map<Int, Int>>(emptyMap())
    private val searchResults = MutableStateFlow<List<VerseSearchResult>>(emptyList())
    private val dailyVerse = MutableStateFlow("")
    private val scrollRestoreKey = MutableStateFlow(0)
    private val linkedVerseText = MutableStateFlow("")
    private val linkedVerseRef = MutableStateFlow("")

    private var commentaryJob: Job? = null

    // Books for Drawer - now from Catalog, not DB. Drawer works even if DB books wiped.
    val books: Flow<List<BookEntity>> = flowOf(BookCatalog.all.map { BookCatalog.toEntity(it) })

    // WIRED: Direct StateFlow, no combine(books, name) lookup
    val currentBookId: StateFlow<Int> = selectedBookId.asStateFlow()

    // WIRED: Name always derived from Catalog ID
    private val selectedBookName: Flow<String> = selectedBookId.map { BookCatalog.name(it) }

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

    private data class ReadingState(
        val books: List<BookEntity>,
        val verses: List<VerseEntity>,
        val highlights: Map<Int, Color>,
        val notes: List<NoteEntity>,
        val currentBookId: Int
    )
    private data class NavState(
        val selectedBook: String,
        val selectedChapter: Int,
        val selectedVerse: Int?,
        val selectedTab: String,
        val commentaryHtml: String,
        val showNoteDialog: Boolean,
        val currentNoteText: String,
        val linkedVerseText: String,
        val linkedVerseRef: String
    )
    private data class ContentState(
        val commentaryError: Boolean,
        val summary: SummaryEntity?,
        val chapterCounts: Map<Int, Int>,
        val searchResults: List<VerseSearchResult>,
        val searchFilter: String,
        val dailyVerse: String,
        val scrollRestoreKey: Int
    )
    private data class SettingsState(
        val darkMode: Boolean?,
        val fontSize: Float,
        val userName: String,
        val userEmail: String
    )

    @Suppress("UNCHECKED_CAST")
    private val readingState = combine(books, verses, highlights, notes, currentBookId) { args ->
        ReadingState(
            args[0] as List<BookEntity>,
            args[1] as List<VerseEntity>,
            args[2] as Map<Int, Color>,
            args[3] as List<NoteEntity>,
            args[4] as Int
        )
    }
    @Suppress("UNCHECKED_CAST")
    private val navState = combine(selectedBookName, selectedChapter, selectedVerse, selectedTab, commentary, showNoteDialog, currentNoteText, linkedVerseText, linkedVerseRef) { args ->
        NavState(
            args[0] as String,
            args[1] as Int,
            args[2] as Int?,
            args[3] as String,
            args[4] as String,
            args[5] as Boolean,
            args[6] as String,
            args[7] as String,
            args[8] as String
        )
    }
    @Suppress("UNCHECKED_CAST")
    private val contentState = combine(commentaryError, summaryState, chapterCounts, searchResults, searchFilter, dailyVerse, scrollRestoreKey) { args ->
        ContentState(
            args[0] as Boolean,
            args[1] as SummaryEntity?,
            args[2] as Map<Int, Int>,
            args[3] as List<VerseSearchResult>,
            args[4] as String,
            args[5] as String,
            args[6] as Int
        )
    }
    @Suppress("UNCHECKED_CAST")
    private val settingsState = combine(prefs.isDarkMode, prefs.fontSize, prefs.userName, prefs.userEmail) { args ->
        SettingsState(
            args[0] as Boolean?,
            args[1] as Float,
            args[2] as String,
            args[3] as String
        )
    }

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<HomeUiState> = combine(
        readingState, navState, contentState, settingsState
    ) { args ->
        val reading = args[0] as ReadingState
        val nav = args[1] as NavState
        val content = args[2] as ContentState
        val settings = args[3] as SettingsState
        HomeUiState(
            books = reading.books,
            verses = reading.verses,
            highlights = reading.highlights,
            notes = reading.notes,
            currentBookId = reading.currentBookId,
            currentBook = nav.selectedBook,
            selectedBook = nav.selectedBook,
            selectedChapter = nav.selectedChapter,
            selectedVerse = nav.selectedVerse,
            selectedTab = nav.selectedTab,
            commentaryHtml = nav.commentaryHtml,
            commentaryError = content.commentaryError,
            summary = content.summary,
            summaryText = content.summary?.summary ?: "",
            summaryWhatHappened = content.summary?.whatHappened ?: "",
            summaryApplication = content.summary?.application ?: "",
            summaryItalicText = content.summary?.summary ?: "",
            linkedVerseText = nav.linkedVerseText,
            linkedVerseRef = nav.linkedVerseRef,
            chapterCounts = content.chapterCounts,
            searchResults = content.searchResults,
            searchFilter = content.searchFilter,
            dailyVerse = content.dailyVerse,
            darkMode = settings.darkMode,
            fontSize = settings.fontSize,
            userName = settings.userName,
            userEmail = settings.userEmail,
            isLoggedIn = settings.userName != "Guest" && settings.userEmail.isNotBlank(),
            scrollRestoreKey = content.scrollRestoreKey,
            showNoteDialog = nav.showNoteDialog,
            currentNoteText = nav.currentNoteText
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
                            "Old Testament", "OT" -> rawResults.filter { it.bookId <= 39 }
                            "New Testament", "NT" -> rawResults.filter { it.bookId >= 40 }
                            "Gospel" -> rawResults.filter { it.bookId in 40..43 }
                            "Prophecy" -> rawResults.filter { it.bookId in setOf(23, 24, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 66) }
                            else -> rawResults
                        }
                    }
                }
        }
        loadDailyVerse()
    }

    // WIRED: Primary - ID version. Use this everywhere new.
    fun selectBook(bookId: Int) {
        selectedBookId.value = bookId.coerceIn(1, 66)
        selectedChapter.value = 1
        selectedVerse.value = null
        scrollRestoreKey.update { it + 1 }
        saveReadingProgress()
    }

    // Back-compat: old callers passing "John" still work, converted once via Catalog
    fun selectBook(bookName: String) = selectBook(BookCatalog.id(bookName))

    fun selectChapter(chapter: Int) {
        selectedChapter.value = chapter
        selectedVerse.value = null
        scrollRestoreKey.update { it + 1 }
        saveReadingProgress()
    }

    fun previousChapter() {
        if (selectedChapter.value > 1) selectChapter(selectedChapter.value - 1)
    }

    fun nextChapter() {
        val max = chapterCounts.value[currentBookId.value]
        if (max == null) {
            selectChapter(selectedChapter.value + 1)
        } else if (selectedChapter.value < max) {
            selectChapter(selectedChapter.value + 1)
        }
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

    fun clearSelection() {
        selectedVerse.value = null
    }

    // WIRED: Primary - ID version
    fun openBook(bookId: Int, chapter: Int) {
        selectedBookId.value = bookId.coerceIn(1, 66)
        selectedChapter.value = chapter
        selectedVerse.value = null
        scrollRestoreKey.update { it + 1 }
        saveReadingProgress()
    }

    // Back-compat
    fun openBook(bookName: String, chapter: Int) = openBook(BookCatalog.id(bookName), chapter)

    fun selectTab(tab: String) {
        selectedTab.value = tab
        if (tab == "Open EW") {
            loadCommentary(currentBookId.value, selectedChapter.value)
        }
    }

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

    fun onUserSignedIn(name: String, email: String) {
        viewModelScope.launch { prefs.saveUser(name, email) }
    }

    fun onUserSignedOut() {
        viewModelScope.launch { prefs.clearUser() }
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
        // WIRED: Validate ID only, no DB name lookup. 1-66 = your JOIN test, 0 orphans.
        if (lastBookId in 1..66) {
            selectedBookId.value = lastBookId
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

    // WIRED: Linked verse now uses Catalog ID, not DB books lookup
    fun loadLinkedVerse(bookName: String, chapter: Int, verse: Int) {
        viewModelScope.launch {
            val bookId = BookCatalog.id(bookName)
            linkedVerseRef.value = "${BookCatalog.name(bookId)} $chapter:$verse"
            linkedVerseText.value = "Loading..."
            runCatching {
                val versesList = dao.getVerses(bookId, chapter).first()
                val found = versesList.firstOrNull { it.verse == verse }
                linkedVerseText.value = found?.text ?: "Verse not found."
            }.onFailure {
                linkedVerseText.value = "Could not load verse."
            }
        }
    }

    // WIRED: Bible + Commentary use SAME ID. bookId 43 = John in both tables.
    fun loadCommentary(bookId: Int = currentBookId.value, chapter: Int = selectedChapter.value) {
        commentaryJob?.cancel()
        commentaryJob = viewModelScope.launch {
            commentaryError.value = false
            commentary.value = ""
            try {
                val html = withContext(Dispatchers.IO) {
                    val comm = dao.getCommentary(bookId, chapter)
                    if (comm == null) {
                        Log.e("HomeViewModel", "getCommentary returned NULL book=$bookId (${BookCatalog.name(bookId)}) ch=$chapter - no row in DB")
                        return@withContext null
                    }
                    try {
                        comm.getHtml()
                    } catch (e: Exception) {
                        Log.e("HomeViewModel", "getHtml failed book=$bookId ch=$chapter", e)
                        null
                    }
                }
                if (html.isNullOrBlank()) {
                    commentary.value = ""
                    commentaryError.value = true
                } else {
                    commentary.value = html
                    commentaryError.value = false
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("HomeViewModel", "loadCommentary failed book=$bookId ch=$chapter", e)
                commentaryError.value = true
                commentary.value = ""
            }
        }
    }

    fun loadCommentary() {
        loadCommentary(currentBookId.value, selectedChapter.value)
    }

    fun loadDailyVerse() {
        viewModelScope.launch {
            runCatching {
                val verse = dao.getRandomVerse()
                dailyVerse.value = verse?.let { "${it.text}\n— ${it.bookName} ${it.chapter}:${it.verse}" }
                    ?: "Thy word is a lamp unto my feet, and a light unto my path.\n— Psalm 119:105"
            }.onFailure {
                dailyVerse.value = "Thy word is a lamp unto my feet, and a light unto my path.\n— Psalm 119:105"
            }
        }
    }

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
            val existing = dao.getNoteAtVerse(currentBookId.value, selectedChapter.value, verseNum)
            if (existing != null) {
                dao.updateNote(existing.copy(noteText = noteText, timestamp = System.currentTimeMillis()))
            } else {
                dao.saveNote(
                    NoteEntity(
                        bookId = currentBookId.value,
                        chapter = selectedChapter.value,
                        verse = verseNum,
                        noteText = noteText,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
            currentNoteText.value = noteText
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