package com.ewbible.app.ui.viewmodel

import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ewbible.app.data.database.*
import com.ewbible.app.data.preferences.AppPreferences
import com.ewbible.app.data.sync.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections

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
    private val prefs: AppPreferences,
    private val syncManager: SyncManager? = null
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
    private var cloudSyncDone = false
    private var lastCommentaryKey = ""

    private val summaryCache = LruCache<String, SummaryEntity>(60)
    private val summaryMissing = Collections.synchronizedSet(mutableSetOf<String>())
    private val summaryLoadingKeys = Collections.synchronizedSet(mutableSetOf<String>())
    private val versesCache = LruCache<String, List<VerseEntity>>(30)

    private val prophecyBooks = setOf(23,24,26,27,28,29,30,31,32,33,34,35,36,37,38,39,66)

    val books: Flow<List<BookEntity>> = flowOf(BookCatalog.all.map { BookCatalog.toEntity(it) })
    val currentBookId: StateFlow<Int> = selectedBookId.asStateFlow()

    private val bookChapterKey = combine(selectedBookId, selectedChapter) { b, c -> b to c }
        .distinctUntilChanged()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    val verses: Flow<List<VerseEntity>> = bookChapterKey
        .flatMapLatest { (bookId, chapter) ->
            val key = "$bookId-$chapter"
            val cached = synchronized(versesCache) { versesCache.get(key) }
            dao.getVerses(bookId, chapter)
                .distinctUntilChanged()
                .map { list ->
                    if (list.isNotEmpty()) synchronized(versesCache) { versesCache.put(key, list) }
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

    val searchResultsFlow: StateFlow<List<VerseSearchResult>> = searchResults.asStateFlow()
    val searchFilterFlow: StateFlow<String> = searchFilter.asStateFlow()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { restoreSavedReading() }

        viewModelScope.launch { books.collect { _uiState.update { s -> s.copy(books = it) } } }
        viewModelScope.launch { verses.collect { _uiState.update { s -> s.copy(verses = it) } } }
        viewModelScope.launch { highlights.collect { _uiState.update { s -> s.copy(highlights = it) } } }
        viewModelScope.launch { notes.collect { _uiState.update { s -> s.copy(notes = it) } } }
        viewModelScope.launch { selectedBookId.collect { id ->
            _uiState.update { s -> s.copy(currentBookId = id, currentBook = BookCatalog.name(id), selectedBook = BookCatalog.name(id)) }
        } }
        viewModelScope.launch { selectedChapter.collect { _uiState.update { s -> s.copy(selectedChapter = it) } } }
        viewModelScope.launch { selectedVerse.collect { _uiState.update { s -> s.copy(selectedVerse = it) } } }
        viewModelScope.launch { selectedTab.collect { _uiState.update { s -> s.copy(selectedTab = it) } } }
        viewModelScope.launch { commentary.collect { _uiState.update { s -> s.copy(commentaryHtml = it) } } }
        viewModelScope.launch { commentaryError.collect { _uiState.update { s -> s.copy(commentaryError = it) } } }
        viewModelScope.launch { summaryState.collect { summ ->
            _uiState.update { s -> s.copy(summary = summ, summaryText = summ?.summary ?: "", summaryWhatHappened = summ?.whatHappened ?: "", summaryApplication = summ?.application ?: "") }
        } }
        viewModelScope.launch { _summaryLoading.collect { _uiState.update { s -> s.copy(summaryLoading = it) } } }
        viewModelScope.launch { chapterCounts.collect { _uiState.update { s -> s.copy(chapterCounts = it) } } }
        viewModelScope.launch { searchResults.collect { _uiState.update { s -> s.copy(searchResults = it) } } }
        viewModelScope.launch { searchFilter.collect { _uiState.update { s -> s.copy(searchFilter = it) } } }
        viewModelScope.launch { dailyVerse.collect { _uiState.update { s -> s.copy(dailyVerse = it) } } }
        viewModelScope.launch { scrollRestoreKey.collect { _uiState.update { s -> s.copy(scrollRestoreKey = it) } } }
        viewModelScope.launch { linkedVerseText.collect { _uiState.update { s -> s.copy(linkedVerseText = it) } } }
        viewModelScope.launch { linkedVerseRef.collect { _uiState.update { s -> s.copy(linkedVerseRef = it) } } }
        viewModelScope.launch { showNoteDialogState.collect { _uiState.update { s -> s.copy(showNoteDialog = it) } } }
        viewModelScope.launch { showAiChatState.collect { _uiState.update { s -> s.copy(showAiChat = it) } } }
        viewModelScope.launch { currentNoteText.collect { _uiState.update { s -> s.copy(currentNoteText = it) } } }
        viewModelScope.launch { isReadingRestored.collect { _uiState.update { s -> s.copy(isReadingRestored = it) } } }
        viewModelScope.launch { prefs.isDarkMode.collect { _uiState.update { s -> s.copy(darkMode = it) } } }
        viewModelScope.launch { prefs.fontSize.collect { _uiState.update { s -> s.copy(fontSize = it.coerceAtLeast(18f)) } } }
        viewModelScope.launch {
            combine(prefs.userName, prefs.userEmail) { n, e -> n to e }.collect { (n, e) ->
                _uiState.update { s -> s.copy(userName = n, userEmail = e, isLoggedIn = n != "Guest" && e.isNotBlank()) }
            }
        }

        // FIX: Single collector for book+chapter+tab - no more double loadCommentary
        viewModelScope.launch {
            combine(bookChapterKey, selectedTab) { bc, tab -> bc to tab }
                .distinctUntilChanged()
                .collectLatest { (bc, tab) ->
                    val (bookId, chapter) = bc
                    loadSummary(bookId, chapter)
                    if (tab == "Open EW") loadCommentary(bookId, chapter)
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
                        "Prophecy" -> raw.filter { it.bookId in prophecyBooks }
                        else -> raw
                    }
                }
        }
        loadDailyVerse()
        viewModelScope.launch { syncFromCloud() }
    }

    private suspend fun syncFromCloud() {
        val sm = syncManager ?: return
        if (cloudSyncDone) return
        cloudSyncDone = true
        try {
            sm.getNotes().getOrNull()?.forEach { cloud ->
                val local = runCatching { withContext(Dispatchers.IO) { dao.getNoteAtVerse(cloud.bookId, cloud.chapter, cloud.verse) } }.getOrNull()
                if (local == null || cloud.timestamp > local.timestamp) {
                    withContext(Dispatchers.IO) {
                        if (local != null) dao.updateNote(local.copy(text = cloud.noteText, timestamp = cloud.timestamp))
                        else dao.saveNote(NoteEntity(bookId = cloud.bookId, chapter = cloud.chapter, verse = cloud.verse, text = cloud.noteText, timestamp = cloud.timestamp))
                    }
                }
            }
            sm.getHighlights().getOrNull()?.forEach { cloud ->
                val colorInt = runCatching {
                    android.graphics.Color.parseColor(cloud.color)
                }.getOrDefault(0)
                if (colorInt != 0) {
                    withContext(Dispatchers.IO) {
                        dao.saveHighlight(HighlightEntity(bookId = cloud.bookId, chapter = cloud.chapter, verse = cloud.verse, color = colorInt))
                    }
                }
            }
            val cloudProgress = sm.getProgress().getOrNull()
            if (cloudProgress != null) {
                val localBook = runCatching { prefs.getLastBookId() }.getOrDefault(1)
                val localChapter = runCatching { prefs.getLastChapter() }.getOrDefault(1)
                val isLocalDefault = localBook == 1 && localChapter == 1
                if (isLocalDefault) {
                    val bookId = BookCatalog.id(cloudProgress.book)
                    if (bookId in 1..66) {
                        selectedBookId.value = bookId
                        selectedChapter.value = cloudProgress.chapter.coerceIn(1, BookCatalog.chapters(bookId))
                        selectedVerse.value = cloudProgress.verse
                        scrollRestoreKey.value += 1
                        prefs.saveLastReading(bookId = bookId, chapter = cloudProgress.chapter, verse = cloudProgress.verse, scrollIndex = cloudProgress.scrollIndex, scrollOffset = cloudProgress.scrollOffset)
                    }
                }
            }
        } catch (e: Exception) {
        }
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
    fun onUserSignedIn(name: String, email: String) {
        viewModelScope.launch {
            runCatching { prefs.saveUser(name, email) }
            cloudSyncDone = false
            syncFromCloud()
        }
    }
    fun onUserSignedOut() { viewModelScope.launch { runCatching { prefs.clearUser() } } }
    fun saveReadingProgress(verse: VerseEntity? = null, scrollIndex: Int = 0, scrollOffset: Int = 0) {
        viewModelScope.launch {
            val bookId = currentBookId.value
            val chapter = selectedChapter.value
            val verseNum = verse?.verse ?: (selectedVerse.value ?: 1)
            runCatching { prefs.saveLastReading(bookId = bookId, chapter = chapter, verse = verseNum, scrollIndex = scrollIndex, scrollOffset = scrollOffset) }
            runCatching {
                syncManager?.uploadProgress(
                    mode = "New King James Version",
                    book = BookCatalog.name(bookId),
                    chapter = chapter,
                    verse = verseNum,
                    scrollIndex = scrollIndex,
                    scrollOffset = scrollOffset
                )
            }
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
        if (key in summaryMissing) {
            if (bookId == currentBookId.value && chapter == selectedChapter.value) summaryState.value = null
            return
        }
        synchronized(summaryCache) { summaryCache.get(key) }?.let { cached ->
            if (bookId == currentBookId.value && chapter == selectedChapter.value) summaryState.value = cached
            return
        }
        if (key in summaryLoadingKeys) return
        summaryLoadingKeys.add(key)
        viewModelScope.launch {
            val isCurrent = bookId == currentBookId.value && chapter == selectedChapter.value
            if (isCurrent) _summaryLoading.value = true
            val s = runCatching { withContext(Dispatchers.IO) { dao.getSummary(bookId, chapter) } }.getOrNull()
            if (s == null) summaryMissing.add(key) else synchronized(summaryCache) { summaryCache.put(key, s) }
            if (isCurrent) summaryState.value = s
            summaryLoadingKeys.remove(key)
            if (summaryLoadingKeys.isEmpty()) _summaryLoading.value = false
        }
    }

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

    fun loadCommentary(bookId: Int = currentBookId.value, chapter: Int = selectedChapter.value) {
        val key = "$bookId-$chapter"
        // Don't reload same chapter - this was causing double load + flicker
        if (key == lastCommentaryKey && commentary.value.isNotBlank()) return
        commentaryJob?.cancel()
        commentaryJob = viewModelScope.launch {
            try {
                val html = withContext(Dispatchers.IO) {
                    val compressed = runCatching { dao.getCommentaryCompressed(bookId, chapter) }.getOrNull()
                    if (compressed == null || compressed.isEmpty()) null
                    // FIX: Use fast cached path in CommentaryHelper, not raw decompress()
                    else CommentaryHelper.getHtmlCommentary(bookId, chapter, compressed)
                }
                val finalHtml = if (html.isNullOrBlank()) {
                    "<html><body><p><i>Commentary for ${BookCatalog.name(bookId)} $chapter is not available yet.</i></p></body></html>"
                } else html
                // Only update if still current - prevents old chapter overwriting new one
                if (bookId == currentBookId.value && chapter == selectedChapter.value) {
                    lastCommentaryKey = key
                    commentary.value = finalHtml
                    commentaryError.value = false
                }
                // Preload next chapter in background - makes Next instant
                preloadNextCommentary(bookId, chapter)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                if (bookId == currentBookId.value && chapter == selectedChapter.value) {
                    commentary.value = "<html><body><p>Commentary error: ${e.message}</p></body></html>"
                    commentaryError.value = true
                }
            }
        }
    }

    private fun preloadNextCommentary(bookId: Int, chapter: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val max = BookCatalog.chapters(bookId)
                val (nextBook, nextChapter) = if (chapter < max) bookId to chapter + 1
                else if (bookId < 66) (bookId + 1) to 1
                else return@launch
                val compressed = dao.getCommentaryCompressed(nextBook, nextChapter) ?: return@launch
                CommentaryHelper.preload(nextBook, nextChapter, compressed)
            }
        }
    }

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
            val bookId = currentBookId.value
            val chapter = selectedChapter.value
            val timestamp = System.currentTimeMillis()
            runCatching {
                withContext(Dispatchers.IO) {
                    val existing = dao.getNoteAtVerse(bookId, chapter, verseNum)
                    if (existing != null) dao.updateNote(existing.copy(text = noteText, timestamp = timestamp))
                    else dao.saveNote(NoteEntity(bookId = bookId, chapter = chapter, verse = verseNum, text = noteText, timestamp = timestamp))
                }
                currentNoteText.value = noteText
            }
            runCatching { syncManager?.uploadNote(bookId, chapter, verseNum, noteText) }
        }
    }
    fun saveHighlight(color: Color) {
        val verseNum = selectedVerse.value ?: return
        viewModelScope.launch {
            val bookId = currentBookId.value
            val chapter = selectedChapter.value
            val colorInt = color.toArgb()
            runCatching { withContext(Dispatchers.IO) { dao.saveHighlight(HighlightEntity(bookId = bookId, chapter = chapter, verse = verseNum, color = colorInt)) } }
            val hex = "#%08X".format(colorInt)
            runCatching { syncManager?.uploadHighlight(bookId, chapter, verseNum, hex) }
        }
    }
    fun removeHighlight() {
        val verseNum = selectedVerse.value ?: return
        viewModelScope.launch {
            val bookId = currentBookId.value
            val chapter = selectedChapter.value
            runCatching { withContext(Dispatchers.IO) { dao.deleteHighlight(bookId, chapter, verseNum) } }
            runCatching { syncManager?.deleteHighlight(bookId, chapter, verseNum) }
        }
    }
}