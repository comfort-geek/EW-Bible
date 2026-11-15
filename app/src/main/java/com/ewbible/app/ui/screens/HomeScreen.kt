package com.ewbible.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import com.ewbible.app.data.auth.GoogleAuth
import com.ewbible.app.data.database.*
import com.ewbible.app.data.sync.SyncManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val db = remember { AppDatabase.getInstance(context) }
    val googleAuth = remember { GoogleAuth(context) }
    val syncManager = remember { SyncManager() }
    val scope = rememberCoroutineScope()
    val systemDark = isSystemInDarkTheme()

    val savedDark by prefs.isDarkMode.collectAsState(initial = null)
    val savedFont by prefs.fontSize.collectAsState(initial = 16f)
    val savedName by prefs.userName.collectAsState(initial = "Guest")
    val savedEmail by prefs.userEmail.collectAsState(initial = "")
    val savedBook by prefs.lastBook.collectAsState(initial = "Genesis")
    val savedChapter by prefs.lastChapter.collectAsState(initial = 1)
    val savedVerse by prefs.lastVerse.collectAsState(initial = 1)
    val savedScrollIndex by prefs.lastScrollIndex.collectAsState(initial = 0)
    val savedScrollOffset by prefs.lastScrollOffset.collectAsState(initial = 0)
    val savedDailyVerseDate by prefs.dailyVerseDate.collectAsState(initial = "")
    val savedDailyVerseRef by prefs.dailyVerseReference.collectAsState(initial = null)

    var isDarkMode by remember { mutableStateOf(systemDark) }
    var themeMode by remember { mutableStateOf("System") }
    var fontSize by remember { mutableStateOf(savedFont) }
    var isLoggedIn by remember { mutableStateOf(false) }
    var userName by remember { mutableStateOf("Guest") }
    var userEmail by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var searchFilter by remember { mutableStateOf("All") }
    var selectedBook by remember { mutableStateOf(savedBook) }
    var selectedChapter by remember { mutableStateOf(savedChapter) }
    var selectedVerse by remember { mutableStateOf<Int?>(savedVerse) }
    var selectedTab by remember { mutableStateOf("Bible") }
    var expandedBook by remember { mutableStateOf<String?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }
    var showHighlightColors by remember { mutableStateOf(false) }
    var dailyVerseDialog by remember { mutableStateOf(false) }
    var dailyVerseText by remember { mutableStateOf("Your word is a lamp for my feet, a light on my path. - Psalm 119:105") }
    var verseSearchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var chapterCounts by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var hasRestoredScroll by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val listState = rememberLazyListState()
    val booksList by db.bibleDao().getAllBooks().collectAsState(initial = emptyList())
    val selectedBookEntity = booksList.firstOrNull {
        it.name.equals(selectedBook, ignoreCase = true) || it.key.equals(selectedBook, ignoreCase = true)
    }
    val currentBookId = selectedBookEntity?.id ?: booksList.firstOrNull()?.id ?: 1
    val versesList by db.bibleDao().getVerses(currentBookId, selectedChapter).collectAsState(initial = emptyList())
    val highlights by db.bibleDao().getHighlights(currentBookId, selectedChapter).collectAsState(initial = emptyList())
    val highlightMap = remember(highlights) {
        highlights.associate { highlight -> highlight.verse to parseHighlightColor(highlight.color) }
    }

    var summary by remember(selectedBook, selectedChapter) { mutableStateOf<SummaryEntity?>(null) }
    var commentaryHtml by remember(selectedBook, selectedChapter) { mutableStateOf("") }
    var commentaryError by remember { mutableStateOf(false) }

    fun syncFromCloud() {
        scope.launch {
            syncManager.getHighlights().forEach { cloud ->
                db.bibleDao().saveHighlight(HighlightEntity(bookId = cloud.bookId, chapter = cloud.chapter, verse = cloud.verse, color = cloud.color))
            }
            syncManager.getNotes().forEach { cloud ->
                db.bibleDao().saveNote(NoteEntity(bookId = cloud.bookId, chapter = cloud.chapter, verse = cloud.verse, noteText = cloud.noteText, timestamp = cloud.timestamp))
            }
            syncManager.getProgress()?.let { p ->
                if (booksList.any { it.name == p.book }) {
                    selectedBook = p.book
                    selectedChapter = p.chapter
                    selectedVerse = p.verse
                    prefs.saveReading(p.mode, p.book, p.chapter, p.verse)
                    prefs.saveScroll(p.scrollIndex, p.scrollOffset)
                }
            }
        }
    }

    LaunchedEffect(selectedBook, selectedChapter, currentBookId) {
        summary = runCatching { db.bibleDao().getSummary(currentBookId, selectedChapter) }.getOrNull()
    }

    LaunchedEffect(selectedBook, selectedChapter, currentBookId, selectedTab) {
        if (selectedTab == "Open EW") {
            commentaryError = false
            commentaryHtml = runCatching {
                db.bibleDao().getCommentary(currentBookId, selectedChapter)?.getHtml().orEmpty()
            }.onFailure { commentaryError = true }.getOrDefault("")
        }
    }

    LaunchedEffect(booksList) {
        if (booksList.isEmpty()) return@LaunchedEffect
        val counts = mutableMapOf<Int, Int>()
        for (book in booksList) counts[book.id] = db.bibleDao().getMaxChapter(book.id)
        chapterCounts = counts
    }

    val gold = Color(0xFFC9A227)
    val background = if (isDarkMode) Color(0xFF1E1E22) else Color(0xFFFDF8EF)
    val panel = if (isDarkMode) Color(0xFF2A2A30) else Color(0xFFF0EAD9)
    val textColor = if (isDarkMode) Color(0xFFF5F0E6) else Color(0xFF1E1E22)
    val secondaryText = if (isDarkMode) Color(0xFFB7B0A6) else Color(0xFF524B40)

    val otBooks = remember(booksList) {
        booksList.filter { book ->
            val t = book.testament.trim()
            t.equals("OT", ignoreCase = true) || t.startsWith("Old", ignoreCase = true) || (t.isBlank() && book.id <= 39)
        }.sortedBy { it.id }
    }
    val ntBooks = remember(booksList) {
        booksList.filter { book ->
            val t = book.testament.trim()
            t.equals("NT", ignoreCase = true) || t.startsWith("New", ignoreCase = true) || (t.isBlank() && book.id > 39)
        }.sortedBy { it.id }
    }
    val availableBooks = remember(booksList) { booksList.sortedBy { it.id }.map { it.name } }
    val filteredSearchResults = remember(searchQuery, searchFilter, availableBooks, otBooks, ntBooks) {
        val gospelBooks = setOf("Matthew", "Mark", "Luke", "John")
        val prophecyBooks = setOf("Isaiah", "Jeremiah", "Ezekiel", "Daniel", "Hosea", "Joel", "Amos", "Obadiah", "Jonah", "Micah", "Nahum", "Habakkuk", "Zephaniah", "Haggai", "Zechariah", "Malachi", "Revelation")
        val candidates = when (searchFilter) {
            "Gospel" -> availableBooks.filter { it in gospelBooks }
            "Prophecy" -> availableBooks.filter { it in prophecyBooks }
            "Old Testament" -> availableBooks.filter { name -> otBooks.any { it.name == name } }
            "New Testament" -> availableBooks.filter { name -> ntBooks.any { it.name == name } }
            else -> availableBooks
        }
        val query = searchQuery.trim()
        if (query.isEmpty()) candidates else candidates.filter { it.contains(query, ignoreCase = true) }
    }

    LaunchedEffect(searchQuery, searchFilter, booksList) {
        val query = searchQuery.trim()
        if (query.length < 2 || booksList.isEmpty()) { verseSearchResults = emptyList(); return@LaunchedEffect }
        val bookById = booksList.associateBy { it.id }
        val gospelBooks = setOf("Matthew", "Mark", "Luke", "John")
        val prophecyBooks = setOf("Isaiah", "Jeremiah", "Ezekiel", "Daniel", "Hosea", "Joel", "Amos", "Obadiah", "Jonah", "Micah", "Nahum", "Habakkuk", "Zephaniah", "Haggai", "Zechariah", "Malachi", "Revelation")
        val safeQuery = query.replace("%", "\\%").replace("_", "\\_")
        val rows = runCatching { db.bibleDao().searchVerses("%$safeQuery%") }.getOrDefault(emptyList())
        verseSearchResults = rows.mapNotNull { row ->
            val book = bookById[row.bookId] ?: return@mapNotNull null
            val allowed = when (searchFilter) {
                "Gospel" -> book.name in gospelBooks
                "Prophecy" -> book.name in prophecyBooks
                "Old Testament" -> book.id <= 39
                "New Testament" -> book.id > 39
                else -> true
            }
            if (!allowed) null else SearchResult(book.name, book.id, row.chapter, row.verse, row.text)
        }
    }

    LaunchedEffect(booksList, savedBook, savedChapter, savedVerse) {
        if (booksList.isEmpty()) return@LaunchedEffect
        val restoredBook = booksList.firstOrNull { it.name.equals(savedBook, ignoreCase = true) || it.key.equals(savedBook, ignoreCase = true) }
            ?: booksList.minByOrNull { it.id } ?: return@LaunchedEffect
        selectedBook = restoredBook.name
        selectedChapter = savedChapter.coerceAtLeast(1)
        selectedVerse = savedVerse.coerceAtLeast(1)
    }

    // FIX: Restore exact scroll position once - only on first load, after verses are loaded
    LaunchedEffect(versesList, savedScrollIndex, savedScrollOffset) {
        if (hasRestoredScroll) return@LaunchedEffect
        if (versesList.isEmpty()) return@LaunchedEffect
        if (savedScrollIndex <= 0 && savedScrollOffset <= 0) {
            hasRestoredScroll = true
            return@LaunchedEffect
        }
        // versesList has header items too, so clamp to list size
        val targetIndex = savedScrollIndex.coerceIn(0, (versesList.size + 2))
        runCatching { listState.scrollToItem(targetIndex, savedScrollOffset) }
        hasRestoredScroll = true
    }

    LaunchedEffect(savedName, savedEmail) {
        userName = savedName
        userEmail = savedEmail
        isLoggedIn = googleAuth.isLoggedIn() && savedName != "Guest"
        if (isLoggedIn) syncFromCloud()
    }
    LaunchedEffect(savedDark) {
        if (savedDark != null) {
            isDarkMode = savedDark!!
            themeMode = if (savedDark!!) "Dark" else "Light"
        } else {
            isDarkMode = systemDark
            themeMode = "System"
        }
    }
    LaunchedEffect(savedFont) { fontSize = savedFont }

    LaunchedEffect(booksList, savedDailyVerseDate, savedDailyVerseRef) {
        if (booksList.isEmpty()) return@LaunchedEffect
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val reference = savedDailyVerseRef
        if (savedDailyVerseDate == today && reference != null) {
            val book = booksList.firstOrNull { it.id == reference.bookId }
            val verse = book?.let { db.bibleDao().getVerses(it.id, reference.chapter).first().firstOrNull { row -> row.verse == reference.verse } }
            if (book != null && verse != null) {
                dailyVerseText = "${verse.text} - ${book.name} ${reference.chapter}:${reference.verse}"
                return@LaunchedEffect
            }
        }
        val random = java.util.Random(today.hashCode().toLong())
        val book = booksList[random.nextInt(booksList.size)]
        val maxChapter = db.bibleDao().getMaxChapter(book.id).coerceAtLeast(1)
        val chapter = random.nextInt(maxChapter) + 1
        val verses = db.bibleDao().getVerses(book.id, chapter).first()
        if (verses.isEmpty()) return@LaunchedEffect
        val verse = verses[random.nextInt(verses.size)]
        dailyVerseText = "${verse.text} - ${book.name} $chapter:${verse.verse}"
        prefs.saveDailyVerse(today, DailyVerseReference(book.id, chapter, verse.verse))
    }

    LaunchedEffect(isDarkMode, fontSize) { prefs.saveSettings(isDarkMode, fontSize) }
    // REMOVED: LaunchedEffect(selectedBook, selectedChapter) { listState.scrollToItem(0) } - this was killing scroll restore

    fun openBook(bookName: String, chapter: Int) {
        selectedBook = bookName
        selectedChapter = chapter
        selectedVerse = 1
        expandedBook = bookName
        hasRestoredScroll = true // user manually changed book, don't restore old scroll
        scope.launch {
            prefs.saveReading("World English Bible", bookName, chapter, 1)
            prefs.saveScroll(0, 0)
            if (isLoggedIn) syncManager.uploadProgress("World English Bible", bookName, chapter, 1, 0, 0)
            listState.scrollToItem(0)
            drawerState.close()
        }
    }
    fun saveReadingProgress(verse: Int) {
        selectedVerse = verse
        scope.launch {
            prefs.saveReading("World English Bible", selectedBook, selectedChapter, verse)
            prefs.saveScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
            if (isLoggedIn) syncManager.uploadProgress("World English Bible", selectedBook, selectedChapter, verse, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        }
    }
    fun handleGoogleLogin() {
        scope.launch {
            googleAuth.signInWithGoogle().onSuccess { data ->
                val name = data.substringBefore('|').ifBlank { "User" }
                val email = data.substringAfter('|', "")
                prefs.saveUser(name, email)
                userName = name; userEmail = email; isLoggedIn = true
                syncFromCloud()
            }.onFailure { isLoggedIn = false }
        }
    }
    fun handleGoogleLogout() {
        scope.launch {
            googleAuth.signOut()
            prefs.saveUser("Guest", "")
            userName = "Guest"; userEmail = ""; isLoggedIn = false
        }
    }
    fun saveHighlight(color: Color) {
        val verse = selectedVerse ?: return
        val hex = "#%08X".format(color.toArgb())
        scope.launch {
            db.bibleDao().saveHighlight(HighlightEntity(bookId = currentBookId, chapter = selectedChapter, verse = verse, color = hex))
            if (isLoggedIn) syncManager.uploadHighlight(currentBookId, selectedChapter, verse, hex)
            showHighlightColors = false
        }
    }
    fun removeHighlight() {
        val verse = selectedVerse ?: return
        scope.launch {
            db.bibleDao().deleteHighlight(currentBookId, selectedChapter, verse)
            if (isLoggedIn) syncManager.deleteHighlight(currentBookId, selectedChapter, verse)
            showHighlightColors = false
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            BookDrawer(
                otBookEntities = otBooks, ntBookEntities = ntBooks, chapterCounts = chapterCounts,
                selectedBook = selectedBook, selectedChapter = selectedChapter, expandedBook = expandedBook,
                background = background, textColor = textColor, panel = panel, gold = gold, secondaryText = secondaryText,
                onOpenBook = { bookName, chapter -> openBook(bookName, chapter) },
                onSelectBook = { bookName -> selectedBook = bookName; selectedChapter = 1 },
                onToggleBook = { bookName -> expandedBook = if (expandedBook == bookName) null else bookName },
            )
        },
    ) {
        Box(modifier = Modifier.fillMaxSize().background(background)) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("EW Bible", color = textColor, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { if (drawerState.isClosed) drawerState.open() else drawerState.close() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = textColor)
                        }
                    },
                    actions = {
                        IconButton(onClick = { settingsOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Settings", tint = textColor)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = background),
                )
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("Bible", "Open EW", "Search").forEach { tab ->
                        Text(text = tab, color = if (selectedTab == tab) gold else textColor,
                            fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal, fontSize = 15.sp,
                            modifier = Modifier.weight(1f).clickable { selectedTab = tab }.padding(vertical = 8.dp), textAlign = TextAlign.Center)
                    }
                }
                when (selectedTab) {
                    "Bible" -> BibleContent(selectedBook = selectedBook, selectedChapter = selectedChapter, summary = summary, versesList = versesList, currentHighlightMap = highlightMap, fontSize = fontSize, gold = gold, panel = panel, textColor = textColor, listState = listState, onVerseClick = ::saveReadingProgress, onVerseLongClick = { verse -> selectedVerse = verse; showHighlightColors = true })
                    "Open EW" -> OpenEWContent(commentaryHtml = commentaryHtml, fontSize = fontSize, textColor = textColor, secondaryText = secondaryText, hasError = commentaryError, bookName = selectedBook, chapter = selectedChapter)
                    "Search" -> SearchContent(searchQuery = searchQuery, searchFilter = searchFilter, filteredSearchResults = filteredSearchResults, verseResults = verseSearchResults, textColor = textColor, gold = gold, onQueryChange = { searchQuery = it }, onFilterChange = { searchFilter = it }, onOpenBook = { bookName -> openBook(bookName, 1); selectedTab = "Bible" }, onOpenVerse = { bookName, chapter, verse -> openBook(bookName, chapter); selectedVerse = verse; selectedTab = "Bible" })
                }
                if (showNotes) {
                    NotesCard(selectedBook = selectedBook, selectedChapter = selectedChapter, selectedVerse = selectedVerse, noteText = noteText, panel = panel, gold = gold, textColor = textColor, onNoteTextChange = { noteText = it }, onSaveNote = {
                        val verse = selectedVerse ?: 1
                        scope.launch {
                            db.bibleDao().saveNote(NoteEntity(bookId = currentBookId, chapter = selectedChapter, verse = verse, noteText = noteText, timestamp = System.currentTimeMillis()))
                            if (isLoggedIn) syncManager.uploadNote(currentBookId, selectedChapter, verse, noteText)
                            noteText = ""
                        }
                    })
                }
                if (showHighlightColors) {
                    HighlightCard(selectedVerse = selectedVerse, panel = panel, gold = gold, textColor = textColor, onHighlightPicked = ::saveHighlight, onRemoveHighlight = ::removeHighlight, onDismiss = { showHighlightColors = false })
                }
            }

            if (settingsOpen) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable { settingsOpen = false }) {
                    Column(
                        modifier = Modifier.fillMaxHeight().fillMaxWidth(0.85f).align(Alignment.CenterEnd).background(background).clickable(enabled = false) { }.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = textColor)
                            IconButton(onClick = { settingsOpen = false }) { Icon(Icons.Default.Close, contentDescription = "Close", tint = textColor) }
                        }
                        if (isLoggedIn) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(gold), contentAlignment = Alignment.Center) {
                                    Text(userName.firstOrNull()?.toString()?.uppercase() ?: "U", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                }
                                Column {
                                    Text(userName, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = textColor)
                                    Text(userEmail, fontSize = 13.sp, color = secondaryText)
                                }
                            }
                            Button(onClick = { handleGoogleLogout() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = panel)) {
                                Text("Log out", color = textColor)
                            }
                        } else {
                            Text("Not signed in", color = secondaryText)
                            Button(onClick = { settingsOpen = false; handleGoogleLogin() }, modifier = Modifier.fillMaxWidth()) {
                                Text("Log in with Google")
                            }
                        }
                        Divider(color = panel)
                        Text("Appearance", fontWeight = FontWeight.Bold, color = textColor)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("System", "Light", "Dark").forEach { mode ->
                                val selected = themeMode == mode
                                Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (selected) gold else panel).clickable {
                                    themeMode = mode
                                    isDarkMode = when (mode) { "Light" -> false; "Dark" -> true; else -> systemDark }
                                }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                    Text(mode, color = if (selected) Color.White else textColor, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Text("Font size", fontWeight = FontWeight.Bold, color = textColor)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("A-", color = textColor)
                            Slider(value = fontSize, onValueChange = { fontSize = it }, valueRange = 12f..28f, modifier = Modifier.weight(1f).padding(horizontal = 6.dp))
                            Text("A+", color = textColor)
                        }
                        Text("Daily Verse", fontWeight = FontWeight.Bold, color = textColor)
                        Button(onClick = { settingsOpen = false; dailyVerseDialog = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = panel)) {
                            Text("Show Daily Verse", color = textColor)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { settingsOpen = false; showNotes = !showNotes }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = panel)) {
                                Text(if (showNotes) "Hide Notes" else "Notes", color = textColor)
                            }
                        }
                    }
                }
            }
        }
    }
    if (dailyVerseDialog) {
        DailyVerseDialog(dailyVerseText = dailyVerseText, gold = gold, textColor = textColor, fontSize = fontSize, onDismiss = { dailyVerseDialog = false })
    }
}

private fun parseHighlightColor(raw: String): Color {
    val cleaned = raw.trim().removePrefix("#")
    val parsed = runCatching { cleaned.toLong(16) }.getOrNull() ?: return Color.Yellow
    return when (cleaned.length) {
        8 -> Color(parsed.toInt())
        6 -> Color((0xFF000000L or parsed).toInt())
        else -> Color.Yellow
    }
}