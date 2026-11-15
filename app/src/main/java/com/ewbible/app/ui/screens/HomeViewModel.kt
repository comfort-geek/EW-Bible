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
                if (bookList.isNotEmpty()) {
                    chapterCounts.value = dao.getChapterCounts()
                        .associate { chapterCount ->
                            chapterCount.bookId to chapterCount.chapterCount
                        }
                }
            }
        }

        viewModelScope.launch {
            searchQuery
                .debounce(300)
                .collectLatest { query ->
                    val trimmedQuery = query.trim()

                    searchResults.value = if (trimmedQuery.length < 2) {
                        emptyList()
                    } else {
                        dao.searchVerses(trimmedQuery).map { result ->
                            SearchResult(
                                bookName = result.bookName,
                                bookId = result.bookId,
                                chapter = result.chapter,
                                verse = result.verse,
                                text = result.text
                            )
                        }
                    }
                }
        }

        viewModelScope.launch {
            loadDailyVerse()
        }

        viewModelScope.launch {
            if (isLoggedIn()) {
                syncFromCloud()
            }
        }
    }

    fun openBook(bookName: String, chapter: Int) {
        selectedBook.value = bookName
        selectedChapter.value = chapter.coerceAtLeast(1)
        selectedVerse.value = 1

        viewModelScope.launch {
            prefs.saveReading(
                "World English Bible",
                bookName,
                chapter,
                1
            )
            prefs.saveScroll(0, 0)
        }
    }

    fun selectChapter(chapter: Int) {
        selectedChapter.value = chapter.coerceAtLeast(1)
    }

    fun selectVerse(verse: Int) {
        selectedVerse.value = verse
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

    fun loadSummary() {
        viewModelScope.launch {
            summaryState.value = runCatching {
                dao.getSummary(
                    currentBookId.value,
                    selectedChapter.value
                )
            }.getOrNull()
        }
    }

    fun loadCommentary() {
        viewModelScope.launch {
            commentaryError.value = false

            commentary.value = runCatching {
                dao.getCommentary(
                    currentBookId.value,
                    selectedChapter.value
                )?.getHtml().orEmpty()
            }.onFailure {
                commentaryError.value = true
            }.getOrDefault("")
        }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            prefs.saveSettings(
                enabled,
                prefs.fontSize.first()
            )
        }
    }

    fun setFontSize(size: Float) {
        viewModelScope.launch {
            prefs.saveSettings(
                prefs.isDarkMode.first() ?: false,
                size
            )
        }
    }

    fun saveReadingProgress(
        verse: Int,
        scrollIndex: Int,
        scrollOffset: Int
    ) {
        val book = selectedBook.value
        if (book.isBlank()) return

        selectedVerse.value = verse

        viewModelScope.launch {
            prefs.saveReading(
                "World English Bible",
                book,
                selectedChapter.value,
                verse
            )
            prefs.saveScroll(scrollIndex, scrollOffset)

            if (isLoggedIn()) {
                syncManager.uploadProgress(
                    "World English Bible",
                    book,
                    selectedChapter.value,
                    verse,
                    scrollIndex,
                    scrollOffset
                )
            }
        }
    }

    fun saveHighlight(color: Color) {
        val verse = selectedVerse.value ?: return

        viewModelScope.launch {
            val hex = "#%08X".format(color.toArgb())

            dao.saveHighlight(
                HighlightEntity(
                    bookId = currentBookId.value,
                    chapter = selectedChapter.value,
                    verse = verse,
                    color = hex
                )
            )

            if (isLoggedIn()) {
                syncManager.uploadHighlight(
                    currentBookId.value,
                    selectedChapter.value,
                    verse,
                    hex
                )
            }
        }
    }

    fun removeHighlight() {
        val verse = selectedVerse.value ?: return

        viewModelScope.launch {
            dao.deleteHighlight(
                currentBookId.value,
                selectedChapter.value,
                verse
            )

            if (isLoggedIn()) {
                syncManager.deleteHighlight(
                    currentBookId.value,
                    selectedChapter.value,
                    verse
                )
            }
        }
    }

    fun saveNote(text: String) {
        val verse = selectedVerse.value ?: return
        if (text.isBlank()) return

        viewModelScope.launch {
            dao.saveNote(
                NoteEntity(
                    bookId = currentBookId.value,
                    chapter = selectedChapter.value,
                    verse = verse,
                    noteText = text,
                    timestamp = System.currentTimeMillis()
                )
            )

            if (isLoggedIn()) {
                syncManager.uploadNote(
                    currentBookId.value,
                    selectedChapter.value,
                    verse,
                    text
                )
            }
        }
    }

    fun handleGoogleLogin() {
        viewModelScope.launch {
            googleAuth.signInWithGoogle()
                .onSuccess { data ->
                    val name = data.substringBefore('|').ifBlank { "User" }
                    val email = data.substringAfter('|', "")

                    prefs.saveUser(name, email)
                    syncFromCloud()
                }
        }
    }

    fun handleGoogleLogout() {
        viewModelScope.launch {
            googleAuth.signOut()
            prefs.saveUser("Guest", "")
        }
    }

    fun syncFromCloud() {
        viewModelScope.launch {
            syncManager.getHighlights().forEach { cloudHighlight ->
                dao.saveHighlight(
                    HighlightEntity(
                        bookId = cloudHighlight.bookId,
                        chapter = cloudHighlight.chapter,
                        verse = cloudHighlight.verse,
                        color = cloudHighlight.color
                    )
                )
            }

            syncManager.getNotes().forEach { cloudNote ->
                val existingNote = dao.getNoteAtVerse(
                    cloudNote.bookId,
                    cloudNote.chapter,
                    cloudNote.verse
                )

                if (existingNote != null) {
                    dao.updateNote(
                        existingNote.copy(
                            noteText = cloudNote.noteText,
                            timestamp = cloudNote.timestamp
                        )
                    )
                } else {
                    dao.saveNote(
                        NoteEntity(
                            bookId = cloudNote.bookId,
                            chapter = cloudNote.chapter,
                            verse = cloudNote.verse,
                            noteText = cloudNote.noteText,
                            timestamp = cloudNote.timestamp
                        )
                    )
                }
            }

            syncManager.getProgress()?.let { progress ->
                val bookList = books.first()
                val bookExists = bookList.any { book ->
                    book.name == progress.book ||
                            book.key == progress.book
                }

                if (bookExists) {
                    selectedBook.value = progress.book
                    selectedChapter.value = progress.chapter.coerceAtLeast(1)
                    selectedVerse.value = progress.verse

                    prefs.saveReading(
                        progress.mode,
                        progress.book,
                        progress.chapter,
                        progress.verse
                    )
                    prefs.saveScroll(
                        progress.scrollIndex,
                        progress.scrollOffset
                    )

                    scrollRestoreKey.value += 1
                }
            }
        }
    }

    private suspend fun isLoggedIn(): Boolean {
    val userName = prefs.userName.first()
    val userEmail = prefs.userEmail.first()

    return googleAuth.isLoggedIn() &&
            userName != "Guest" &&
            userEmail.isNotBlank()
}
    }

    private suspend fun restoreSavedReading() {
        val savedBook = prefs.lastBook.first()
        val savedChapter = prefs.lastChapter.first()
        val savedVerse = prefs.lastVerse.first()

        if (savedBook.isNotBlank()) {
            selectedBook.value = savedBook
            selectedChapter.value = savedChapter.coerceAtLeast(1)
            selectedVerse.value = savedVerse.coerceAtLeast(1)
        }
    }

    private suspend fun loadDailyVerse() {
        val bookList = books.first()
        if (bookList.isEmpty()) return

        val today = LocalDate.now().toString()
        val savedDate = prefs.dailyVerseDate.first()
        val savedReference = prefs.dailyVerseReference.first()

        if (savedDate == today && savedReference != null) {
            val savedVerse = dao.getVerses(
                savedReference.bookId,
                savedReference.chapter
            ).first().firstOrNull { verse ->
                verse.verse == savedReference.verse
            }

            if (savedVerse != null) {
                val savedBook = bookList.first { book ->
                    book.id == savedReference.bookId
                }

                dailyVerse.value =
                    "${savedVerse.text} - ${savedBook.name} ${savedReference.chapter}:${savedReference.verse}"
                return
            }
        }

        val random = kotlin.random.Random(today.hashCode())
        val randomBook = bookList.random(random)
        val maxChapter = dao.getMaxChapter(randomBook.id).coerceAtLeast(1)
        val randomChapter = random.nextInt(maxChapter) + 1
        val chapterVerses = dao.getVerses(
            randomBook.id,
            randomChapter
        ).first()

        if (chapterVerses.isEmpty()) return

        val randomVerse = chapterVerses.random(random)

        dailyVerse.value =
            "${randomVerse.text} - ${randomBook.name} $randomChapter:${randomVerse.verse}"

        prefs.saveDailyVerse(
            today,
            DailyVerseReference(
                bookId = randomBook.id,
                chapter = randomChapter,
                verse = randomVerse.verse
            )
        )
    }
}

private fun parseHighlightColor(raw: String): Color {
    val cleaned = raw.trim().removePrefix("#")
    val parsed = runCatching { cleaned.toLong(16) }.getOrNull()
        ?: return Color.Yellow

    return when (cleaned.length) {
        8 -> Color(parsed.toInt())
        6 -> Color((0xFF000000L or parsed).toInt())
        else -> Color.Yellow
    }
}