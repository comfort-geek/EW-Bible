package com.ewbible.app.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
    val darkMode: Boolean = false,
    val fontSize: Float = 16f,
    val isLoggedIn: Boolean = false,
    val userName: String = "Guest",
    val userEmail: String = ""
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
                    highlightList.associate {
                        it.verse to parseHighlightColor(it.color)
                    }
                }
            }
        }

    val notes: Flow<List<NoteEntity>> = dao.getAllNotes()

    val uiState: StateFlow<HomeUiState> = combine(
        books,
        verses,
        highlights,
        notes,
        currentBookId,
        selectedBook,
        selectedChapter,
        selectedVerse,
        selectedTab,
        commentary,
        commentaryError,
        summaryState,
        chapterCounts,
        searchResults,
        dailyVerse,
        prefs.isDarkMode,
        prefs.fontSize,
        prefs.userName,
        prefs.userEmail
    ) { values ->
        HomeUiState(
            books = values[0] as List<BookEntity>,
            verses = values[1] as List<VerseEntity>,
            highlights = values[2] as Map<Int, Color>,
            notes = values[3] as List<NoteEntity>,
            currentBookId = values[4] as Int,
            selectedBook = values[5] as String,
            selectedChapter = values[6] as Int,
            selectedVerse = values[7] as Int?,
            selectedTab = values[8] as String,
            commentaryHtml = values[9] as String,
            commentaryError = values[10] as Boolean,
            summary = values[11] as SummaryEntity?,
            chapterCounts = values[12] as Map<Int, Int>,
            searchResults = values[13] as List<SearchResult>,
            dailyVerse = values[14] as String,
            darkMode = values[15] as Boolean,
            fontSize = values[16] as Float,
            userName = values[17] as String,
            userEmail = values[18] as String
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    init {
        viewModelScope.launch {
            books.collect { bookList ->
                if (bookList.isNotEmpty()) {
                    chapterCounts.value = dao.getChapterCounts()
                        .associate { it.bookId to it.chapterCount }
                }
            }
        }

        viewModelScope.launch {
            searchQuery
                .debounce(300)
                .collectLatest { query ->
                    val trimmed = query.trim()

                    searchResults.value = if (trimmed.length < 2) {
                        emptyList()
                    } else {
                        dao.searchVerses(trimmed)
                    }
                }
        }

        viewModelScope.launch {
            loadDailyVerse()
        }
    }

    fun openBook(bookName: String, chapter: Int) {
        selectedBook.value = bookName
        selectedChapter.value = chapter.coerceAtLeast(1)
        selectedVerse.value = 1

        viewModelScope.launch {
            prefs.saveReading(
                translation = "World English Bible",
                book = bookName,
                chapter = chapter,
                verse = 1
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
                translation = "World English Bible",
                book = book,
                chapter = selectedChapter.value,
                verse = verse
            )
            prefs.saveScroll(scrollIndex, scrollOffset)
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
        }
    }

    fun removeHighlight() {
        val verse = selectedVerse.value ?: return

        viewModelScope.launch {
            dao.deleteHighlight(
                bookId = currentBookId.value,
                chapter = selectedChapter.value,
                verse = verse
            )
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
        }
    }

    private suspend fun loadDailyVerse() {
        val bookList = books.first()
        if (bookList.isEmpty()) return

        val today = LocalDate.now().toString()
        val savedDate = prefs.dailyVerseDate.first()
        val reference = prefs.dailyVerseReference.first()

        if (savedDate == today && reference != null) {
            val verse = dao.getVerses(reference.bookId, reference.chapter)
                .first()
                .firstOrNull { it.verse == reference.verse }

            if (verse != null) {
                val book = bookList.first { it.id == reference.bookId }

                dailyVerse.value =
                    "${verse.text} - ${book.name} ${reference.chapter}:${reference.verse}"
                return
            }
        }

        val random = kotlin.random.Random(today.hashCode())
        val book = bookList.random(random)
        val maxChapter = dao.getMaxChapter(book.id).coerceAtLeast(1)
        val chapter = random.nextInt(maxChapter) + 1
        val verseList = dao.getVerses(book.id, chapter).first()

        if (verseList.isEmpty()) return

        val verse = verseList.random(random)
        dailyVerse.value =
            "${verse.text} - ${book.name} $chapter:${verse.verse}"

        prefs.saveDailyVerse(
            today,
            DailyVerseReference(book.id, chapter, verse.verse)
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