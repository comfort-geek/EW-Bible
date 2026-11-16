package com.ewbible.app.ui.screens

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ewbible.app.data.auth.GoogleAuth
import com.ewbible.app.data.database.*
import com.ewbible.app.data.preferences.AppPreferences
import com.ewbible.app.data.sync.SyncManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

data class HomeUiState(
    val books: List<BookEntity> = emptyList(),
    val verses: List<VerseEntity> = emptyList(),
    val highlights: Map<Int, Color> = emptyMap(),
    val notes: List<NoteEntity> = emptyList(),
    val summary: SummaryEntity? = null,
    val commentaryHtml: String = "",
    val commentaryError: Boolean = false,
    val chapterCounts: Map<Int, Int> = emptyMap(),
    val searchResults: List<VerseSearchResult> = emptyList(),
    val dailyVerse: String = "",
    val currentBookId: Int = 1,
    val selectedBook: String = "",
    val selectedChapter: Int = 1,
    val selectedVerse: Int? = null,
    val selectedTab: String = "Bible",
    val darkMode: Boolean? = null,
    val fontSize: Float = 16f,
    val isLoggedIn: Boolean = false,
    val userName: String = "Guest",
    val userEmail: String = "",
    val scrollRestoreKey: Int = 0
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class HomeViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val db = AppDatabase.getInstance(context)
    private val prefs = AppPreferences(context)
    private val googleAuth = GoogleAuth(context)
    private val syncManager = SyncManager()
    private val dao = db.bibleDao()

    private val selectedBook = MutableStateFlow("")
    private val selectedChapter = MutableStateFlow(1)
    private val selectedVerse = MutableStateFlow<Int?>(null)
    private val selectedTab = MutableStateFlow("Bible")
    private val searchQuery = MutableStateFlow("")
    private val searchFilter = MutableStateFlow("All")

    private val commentary = MutableStateFlow("")
    private val commentaryError = MutableStateFlow(false)
    private val summaryState = MutableStateFlow<SummaryEntity?>(null)
    private val chapterCounts = MutableStateFlow>(emptyMap())
    private val searchResults = MutableStateFlow<List<VerseSearchResult>>(emptyList())
    private val dailyVerse = MutableStateFlow("")
    private val scrollRestoreKey = MutableStateFlow(0)

    val books: Flow<List<BookEntity>> = dao.getAllBooks()

    val currentBookId: StateFlow<Int> = combine(
        books,
        selectedBook
    ) { bookList, bookName ->
        bookList.firstOrNull {
            it.name.equals(bookName, ignoreCase = true) ||
                    it.key.equals(bookName, ignoreCase = true)
        }?.id
            ?: bookList.firstOrNull()?.id
            ?: 1
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

    val highlights: Flow> = currentBookId
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

    private data class NavigationState(
        val selectedBook: String,
        val selectedChapter: Int,
        val selectedVerse: Int?,
        val selectedTab: String,
        val commentaryHtml: String
    )

    private data class ContentState(
        val commentaryError: Boolean,
        val summary: SummaryEntity?,
        val chapterCounts: Map<Int, Int>,
        val searchResults: List<VerseSearchResult>,
        val dailyVerse: String
    )

    private data class UserSettingsState(
        val darkMode: Boolean?,
        val fontSize: Float,
        val userName: String,
        val userEmail: String,
        val isLoggedIn: Boolean
    )

    private val readingState = combine(
        books,
        verses,
        highlights,
        notes,
        currentBookId
    ) { books, verses, highlights, notes, currentBookId ->
        ReadingState(
            books = books,
            verses = verses,
            highlights = highlights,
            notes = notes,
            currentBookId = currentBookId
        )
    }

    private val navigationState = combine(
        selectedBook,
        selectedChapter,
        selectedVerse,
        selectedTab,
        commentary
    ) { selectedBook, selectedChapter, selectedVerse, selectedTab, commentaryHtml ->
        NavigationState(
            selectedBook = selectedBook,
            selectedChapter = selectedChapter,
            selectedVerse = selectedVerse,
            selectedTab = selectedTab,
            commentaryHtml = commentaryHtml
        )
    }

    private val contentState = combine(
        commentaryError,
        summaryState,
        chapterCounts,
        searchResults,
        dailyVerse
    ) { commentaryError, summary, chapterCounts, searchResults, dailyVerse ->
        ContentState(
            commentaryError = commentaryError,
            summary = summary,
            chapterCounts = chapterCounts,
            searchResults = searchResults,
            dailyVerse = dailyVerse
        )
    }

    private val userSettingsState = combine(
        prefs.isDarkMode,
        prefs.fontSize,
        prefs.userName,
        prefs.userEmail
    ) { darkMode, fontSize, userName, userEmail ->
        UserSettingsState(
            darkMode = darkMode,
            fontSize = fontSize,
            userName = userName,
            userEmail = userEmail,
            isLoggedIn = googleAuth.isLoggedIn() &&
                    userName != "Guest" &&
                    userEmail.isNotBlank()
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(
        readingState,
        navigationState,
        contentState,
        userSettingsState,
        scrollRestoreKey
    ) { reading, navigation, content, settings, restoreKey ->
        HomeUiState(
            books = reading.books,
            verses = reading.verses,
            highlights = reading.highlights,
            notes = reading.notes,
            currentBookId = reading.currentBookId,
            selectedBook = navigation.selectedBook,
            selectedChapter = navigation.selectedChapter,
            selectedVerse = navigation.selectedVerse,
            selectedTab = navigation.selectedTab,
            commentaryHtml = navigation.commentaryHtml,
            commentaryError = content.commentaryError,
            summary = content.summary,
            chapterCounts = content.chapterCounts,
            searchResults = content.searchResults,
            dailyVerse = content.dailyVerse,
            darkMode = settings.darkMode,
            fontSize = settings.fontSize,
            isLoggedIn = settings.isLoggedIn,
            userName = settings.userName,
            userEmail = settings.userEmail,
            scrollRestoreKey = restoreKey
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    init {
        viewModelScope.launch {
            restoreSavedReading()
            books.collect { bookList ->
                if (bookList.isNotEmpty() && selectedBook.value.isBlank()) {
                    selectedBook.value = bookList.first().name
                }
            }
        }

        viewModelScope.launch {
            chapterCounts.value = dao.getChapterCounts().associate { it.bookId to it.chapterCount }
        }

        viewModelScope.launch {
            combine(currentBookId, selectedChapter) { bookId, chapter ->
                bookId to chapter
            }.collectLatest { (bookId, chapter) ->
                loadSummary(bookId, chapter)
                loadCommentary(bookId, chapter)
            }
        }

        viewModelScope.launch {
            combine(
                searchQuery.debounce(300L).distinctUntilChanged(),
                searchFilter
            ) { query, filter ->
                query to filter
            }.collectLatest { (query, filter) ->
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

    fun selectVerse(verse: Int?) {
        selectedVerse.value = verse
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
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setSearchFilter(filter: String) {
        searchFilter.value = filter
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setDarkMode(enabled)
        }
    }

    fun setFontSize(size: Float) {
        viewModelScope.launch {
            prefs.setFontSize(size)
        }
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
        viewModelScope.launch {
            commentaryError.value = false
            runCatching {
                val comm = dao.getCommentary(bookId, chapter)
                commentary.value = comm?.getHtml() ?: "<p>No commentary available for this chapter.</p>"
            }.onFailure {
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

    fun handleGoogleLogin() {
        viewModelScope.launch {
            googleAuth.login()
        }
    }

    fun handleGoogleLogout() {
        viewModelScope.launch {
            googleAuth.logout()
            prefs.clearUser()
        }
    }

    private fun parseHighlightColor(colorStr: String): Color {
        return runCatching {
            Color(colorStr.toInt())
        }.getOrDefault(Color(0xFFFFF176))
    }
}
