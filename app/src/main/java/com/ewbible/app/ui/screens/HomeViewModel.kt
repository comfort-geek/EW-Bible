package com.ewbible.app.ui.screens

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ewbible.app.data.auth.GoogleAuth
import com.ewbible.app.data.database.*
import com.ewbible.app.data.sync.SyncManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HomeUiState(
    val books: List<BookEntity> = emptyList(),
    val verses: List<VerseEntity> = emptyList(),
    val highlights: Map<Int, Color> = emptyMap(),
    val notes: List<NoteEntity> = emptyList(),
    val summary: SummaryEntity? = null,
    val commentaryHtml: String = "",
    val commentaryError: Boolean = false,
    val chapterCounts: Map<Int, Int> = emptyMap(),
    val searchResults: List<SearchResult> = emptyList(),
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
    private val chapterCounts = MutableStateFlow<Map<Int, Int>>(emptyMap())
    private val searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
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
        val searchResults: List<SearchResult>,
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
        }

        viewModelScope.launch {
            books.collect { bookList ->
                if (bookList.isNotEmpty() && selectedBook.value.isBlank()) {
                    selectedBook.value = bookList.first().name
                }
            }
        }

        viewModelScope.launch {
            combine(currentBookId, selectedChapter) { bookId, chapter ->
                bookId to chapter
            }.collectLatest { (bookId, chapter) ->
                loadChapterCounts(bookId)
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
                    searchResults.value = dao.searchVerses(query, filter)
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

    fun selectTab(tab: String) {
        selectedTab.value = tab
    }

    fun navigateToReference(bookName: String, chapter: Int, verse: Int? = null) {
        selectedBook.value = bookName
        selectedChapter.value = chapter
        selectedVerse.value = verse
