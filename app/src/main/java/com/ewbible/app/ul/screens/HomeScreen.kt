package com.ewbible.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ewbible.app.data.auth.GoogleAuth
import com.ewbible.app.data.database.*
import com.ewbible.app.data.sync.SyncManager
import kotlinx.coroutines.launch

enum class ReadingLevel { BOOKS, CHAPTERS, VERSES }

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class
)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val db = remember { AppDatabase.getInstance(context) }
    val googleAuth = remember { GoogleAuth(context) }
    val syncManager = remember { SyncManager() }
    val scope = rememberCoroutineScope()

    val savedMode by prefs.lastMode.collectAsState(initial = "World English Bible")
    val savedBook by prefs.lastBook.collectAsState(initial = "Genesis")
    val savedChapter by prefs.lastChapter.collectAsState(initial = 1)
    val savedVerse by prefs.lastVerse.collectAsState(initial = 1)
    val savedDark by prefs.isDarkMode.collectAsState(initial = true)
    val savedFont by prefs.fontSize.collectAsState(initial = 16f)
    val savedName by prefs.userName.collectAsState(initial = "Guest")
    val savedScrollIndex by prefs.lastScrollIndex.collectAsState(initial = 0)
    val savedScrollOffset by prefs.lastScrollOffset.collectAsState(initial = 0)

    var currentMode by remember { mutableStateOf("World English Bible") }
    var selectedBook by remember { mutableStateOf("Genesis") }
    var selectedChapter by remember { mutableStateOf(1) }
    var selectedVerse by remember { mutableStateOf<Int?>(null) }
    var isFullScreen by remember { mutableStateOf(false) }

    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(savedMode, savedBook, savedChapter, savedVerse, savedDark, savedFont, savedName) {
        if (!initialized) {
            currentMode = savedMode
            selectedBook = savedBook
            selectedChapter = savedChapter
            selectedVerse = savedVerse
            isDarkMode = savedDark
            fontSize = savedFont
            isLoggedIn = savedName != "Guest"
            initialized = true
        }
    }

    // ---------- DB ----------
    val booksList by db.bibleDao().getAllBooks().collectAsState(initial = emptyList())
    val currentBookId = booksList.find { it.name == selectedBook }?.id ?: 1
    val versesList by db.bibleDao().getVerses(currentBookId, selectedChapter).collectAsState(initial = emptyList())

    val visibleVerses = remember(versesList, showSearch, searchQuery) {
        if (!showSearch || searchQuery.isBlank()) {
            versesList
        } else {
            versesList.filter { it.text.contains(searchQuery, ignoreCase = true) }
        }
    }

    var summary by remember(selectedBook, selectedChapter) { mutableStateOf<SummaryEntity?>(null) }
    LaunchedEffect(selectedBook, selectedChapter, currentBookId) {
        summary = try {
            db.bibleDao().getSummary(currentBookId, selectedChapter)
        } catch (_: Exception) {
            null
        }
    }

    var commentaryHtml by remember(selectedBook, selectedChapter) { mutableStateOf("") }
    LaunchedEffect(selectedBook, selectedChapter, currentBookId, currentMode) {
        commentaryHtml = if (currentMode == "Endurance" || currentMode == "Commentary") {
            try {
                db.bibleDao().getCommentary(currentBookId, selectedChapter)?.getHtml() ?: ""
            } catch (_: Exception) {
                ""
            }
        } else {
            ""
        }
    }

    val leftDrawerState = rememberDrawerState(DrawerValue.Closed)
    var readingLevel by remember { mutableStateOf(ReadingLevel.BOOKS) }
    var showRightDrawer by remember { mutableStateOf(false) }
    var isLoggedIn by remember { mutableStateOf(false) }
    var isDarkMode by remember { mutableStateOf(true) }
    var fontSize by remember { mutableStateOf(16f) }
    var showDailyVerse by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchFilter by remember { mutableStateOf("All") }
    var showHighlightColors by remember { mutableStateOf(false) }
    var highlightedVerses by remember { mutableStateOf<Map<Int, Color>>(emptyMap()) }
    val highlightColors = listOf(
        Color.Yellow,
        Color.Green,
        Color(0xFFFFB6C1),
        Color.Cyan,
        Color(0xFFFFA500)
    )
    var syncStatus by remember { mutableStateOf("") }

    val dbHighlights by db.bibleDao().getHighlights(currentBookId, selectedChapter).collectAsState(initial = emptyList())

    LaunchedEffect(dbHighlights) {
        highlightedVerses = try {
            dbHighlights.associate { highlight ->
                highlight.verse to Color(
                    runCatching { highlight.color.toLong() }.getOrDefault(0xFFFFFF00)
                )
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    val bgColor = if (isDarkMode) Color(0xFF1E1E22) else Color(0xFFFDF8EF)
    val textColor = if (isDarkMode) Color(0xFFF5F0E6) else Color(0xFF1E1E22)
    val cardColor = if (isDarkMode) Color(0xFF2A2A30) else Color(0xFFF0EAD9)
    val goldColor = Color(0xFFC9A227)

    val listState = rememberLazyListState()
    var scrollRestored by remember { mutableStateOf(false) }

    LaunchedEffect(savedScrollIndex) {
        if (!scrollRestored && savedScrollIndex > 0) {
            try {
                listState.scrollToItem(savedScrollIndex, savedScrollOffset)
            } catch (_: Exception) {
            }
            scrollRestored = true
        }
    }

    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        kotlinx.coroutines.delay(500)
        scope.launch {
            try {
                val verseNum = (listState.firstVisibleItemIndex + 1).coerceAtLeast(1)
                prefs.saveScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
                prefs.saveReading(currentMode, selectedBook, selectedChapter, verseNum)
                if (isLoggedIn) {
                    try {
                        syncManager.uploadProgress(
                            currentMode,
                            selectedBook,
                            selectedChapter,
                            verseNum,
                            listState.firstVisibleItemIndex,
                            listState.firstVisibleItemScrollOffset
                        )
                        syncStatus = "Synced ✅"
                    } catch (_: Exception) {
                        syncStatus = ""
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(isDarkMode, fontSize) {
        try {
            prefs.saveSettings(isDarkMode, fontSize)
        } catch (_: Exception) {
        }
    }

    BackHandler(enabled = isFullScreen) {
        isFullScreen = false
    }

    fun openReading(mode: String, book: String, chapter: Int) {
        currentMode = mode
        selectedBook = book
        selectedChapter = chapter
        selectedVerse = 1
        scrollRestored = true
        showSearch = false
        scope.launch {
            try {
                prefs.saveReading(mode, book, chapter, 1)
                prefs.saveScroll(0, 0)
                if (isLoggedIn) {
                    syncManager.uploadProgress(mode, book, chapter, 1, 0, 0)
                }
                listState.scrollToItem(0, 0)
                leftDrawerState.close()
            } catch (_: Exception) {
            }
        }
    }

    fun downloadCloudData() {
        scope.launch {
            syncStatus = "Syncing..."
            try {
                val cloudProgress = syncManager.getProgress()
                if (cloudProgress != null) {
                    if (selectedBook == "Genesis" && selectedChapter == 1 && savedScrollIndex == 0) {
                        currentMode = cloudProgress.mode
                        selectedBook = cloudProgress.book
                        selectedChapter = cloudProgress.chapter
                        selectedVerse = cloudProgress.verse
                        prefs.saveReading(
                            cloudProgress.mode,
                            cloudProgress.book,
                            cloudProgress.chapter,
                            cloudProgress.verse
                        )
                        prefs.saveScroll(cloudProgress.scrollIndex, cloudProgress.scrollOffset)
                    }
                }

                val cloudHighlights = syncManager.getHighlights()
                cloudHighlights.forEach { highlight ->
                    try {
                        db.bibleDao().saveHighlight(
                            HighlightEntity(
                                bookId = highlight.bookId,
                                chapter = highlight.chapter,
                                verse = highlight.verse,
                                color = highlight.color
                            )
                        )
                    } catch (_: Exception) {
                    }
                }

                val cloudNotes = syncManager.getNotes()
                cloudNotes.forEach { note ->
                    try {
                        db.bibleDao().saveNote(
                            NoteEntity(
                                bookId = note.bookId,
                                chapter = note.chapter,
                                verse = note.verse,
                                noteText = note.noteText,
                                timestamp = note.timestamp
                            )
                        )
                    } catch (_: Exception) {
                    }
                }

                syncStatus = "Synced ✅"
            } catch (_: Exception) {
                syncStatus = "Sync failed"
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
        if (isFullScreen) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().background(bgColor).padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                if (currentMode == "World English Bible") {
                    summary?.let { s ->
                        item(key = "summary-full-$selectedBook-$selectedChapter") {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5EEDD))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        "CHAPTER SUMMARY - $selectedBook $selectedChapter",
                                        color = goldColor,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(s.summary, color = Color.Black, fontSize = fontSize.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("What Happened", color = goldColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text(s.whatHappened, color = Color.Black, fontSize = fontSize.sp)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("Application For You", color = goldColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text(s.application, color = Color.Black, fontSize = fontSize.sp)
                                }
                            }
                        }
                    }

                    if (visibleVerses.isEmpty()) {
                        item {
                            Text(
                                "Loading World English Bible...",
                                color = Color.Gray,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(vertical = 20.dp)
                            )
                        }
                    }

                    itemsIndexed(visibleVerses, key = { _, v -> "full-$selectedBook-$selectedChapter-${v.verse}-WEB" }) { _, verse ->
                        val hl = highlightedVerses[verse.verse]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(hl ?: Color.Transparent, RoundedCornerShape(8.dp))
                                .combinedClickable(
                                    onClick = {
                                        selectedVerse = verse.verse
                                        scope.launch {
                                            try {
                                                prefs.saveReading(currentMode, selectedBook, selectedChapter, verse.verse)
                                                if (isLoggedIn) {
                                                    syncManager.uploadProgress(
                                                        currentMode,
                                                        selectedBook,
                                                        selectedChapter,
                                                        verse.verse,
                                                        listState.firstVisibleItemIndex,
                                                        listState.firstVisibleItemScrollOffset
                                                    )
                                                }
                                            } catch (_: Exception) {
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        selectedVerse = verse.verse
                                        showHighlightColors = true
                                    }
                                )
                                .padding(vertical = 10.dp, horizontal = 8.dp)
                        ) {
                            Text(text = "${verse.verse}  ", color = goldColor, fontWeight = FontWeight.Bold, fontSize = fontSize.sp)
                            Text(text = verse.text, color = textColor, fontSize = fontSize.sp)
                        }
                    }
                } else {
                    item(key = "commentary-full-$selectedBook-$selectedChapter") {
                        if (commentaryHtml.isEmpty()) {
                            Text("Loading Endurance...", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 20.dp))
                        } else {
                            Text(text = commentaryHtml, color = textColor, fontSize = fontSize.sp, modifier = Modifier.padding(vertical = 10.dp))
                        }
                    }
                }
            }

            if (showHighlightColors) {
                Box(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = cardColor)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Highlight Verse $selectedVerse - Choose Color", fontWeight = FontWeight.Bold, color = textColor)

                            Row(
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            ) {
                                highlightColors.forEach { color ->
                                    Button(
                                        onClick = {
                                            selectedVerse?.let { verse ->
                                                highlightedVerses = highlightedVerses + (verse to color)
                                                scope.launch {
                                                    try {
                                                        db.bibleDao().saveHighlight(
                                                            HighlightEntity(
                                                                bookId = currentBookId,
                                                                chapter = selectedChapter,
                                                                verse = verse,
                                                                color = color.value.toString()
                                                            )
                                                        )
                                                        if (isLoggedIn) {
                                                            syncManager.uploadHighlight(currentBookId, selectedChapter, verse, color.value.toString())
                                                        }
                                                    } catch (_: Exception) {
                                                    }
                                                }
                                            }
                                            showHighlightColors = false
                                        },
                                        modifier = Modifier.size(40.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = color)
                                    ) {}
                                }
                            }

                            TextButton(onClick = { showHighlightColors = false }) {
                                Text("Close")
                            }
                        }
                    }
                }
            }

            return@Box
        }

        ModalNavigationDrawer(
            drawerState = leftDrawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Text(text = currentMode, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Grow in the Word of God, every day.", color = goldColor, fontSize = 12.sp)
                        if (syncStatus.isNotEmpty()) {
                            Text(text = syncStatus, color = Color.Gray, fontSize = 11.sp)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = when (readingLevel) {
                                ReadingLevel.BOOKS -> "Book"
                                ReadingLevel.CHAPTERS -> "$selectedBook - Chapter"
                                ReadingLevel.VERSES -> "$selectedBook $selectedChapter - Verse"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )

                        Divider(modifier = Modifier.padding(vertical = 8.dp))

                        LazyColumn(modifier = Modifier.weight(1f)) {
                            when (readingLevel) {
                                ReadingLevel.BOOKS -> {
                                    itemsIndexed(booksList) { _, book ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 12.dp)
                                                .clickable {
                                                    selectedBook = book.name
                                                    readingLevel = ReadingLevel.CHAPTERS
                                                }
                                        ) {
                                            Text(text = book.name, fontSize = 16.sp, modifier = Modifier.weight(1f))
                                            Text(text = ">", color = Color.Gray)
                                        }
                                        Divider()
                                    }
                                }

                                ReadingLevel.CHAPTERS -> {
                                    itemsIndexed((1..50).toList()) { _, ch ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 12.dp)
                                                .clickable {
                                                    openReading(currentMode, selectedBook, ch)
                                                    readingLevel = ReadingLevel.VERSES
                                                }
                                        ) {
                                            Text(text = "Chapter $ch", fontSize = 16.sp, modifier = Modifier.weight(1f))
                                            Text(text = ">", color = Color.Gray)
                                        }
                                        Divider()
                                    }
                                }

                                ReadingLevel.VERSES -> {
                                    itemsIndexed((1..31).toList()) { _, v ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                                        ) {
                                            Text(text = "Verse $v", fontSize = 16.sp, modifier = Modifier.weight(1f))
                                        }
                                        Divider()
                                    }
                                }
                            }
                        }

                        if (readingLevel == ReadingLevel.CHAPTERS) {
                            Button(onClick = { readingLevel = ReadingLevel.BOOKS }, modifier = Modifier.fillMaxWidth()) {
                                Text("← Back to Books")
                            }
                        }
                        if (readingLevel == ReadingLevel.VERSES) {
                            Button(onClick = { readingLevel = ReadingLevel.CHAPTERS }, modifier = Modifier.fillMaxWidth()) {
                                Text("← Back to Chapters")
                            }
                        }

                        Button(
                            onClick = {
                                when (readingLevel) {
                                    ReadingLevel.BOOKS -> readingLevel = ReadingLevel.CHAPTERS
                                    ReadingLevel.CHAPTERS -> {
                                        openReading(currentMode, selectedBook, 1)
                                        readingLevel = ReadingLevel.VERSES
                                    }
                                    ReadingLevel.VERSES -> scope.launch { leftDrawerState.close() }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            Text("Open →")
                        }
                    }
                }
            }
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text("EW Bible", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = textColor)
                                Text("Grow in the Word of God, every day.", color = goldColor, fontSize = 11.sp)
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        if (leftDrawerState.isClosed) leftDrawerState.open()
                                        else leftDrawerState.close()
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = textColor)
                            }
                        },
                        actions = {
                            IconButton(onClick = { showRightDrawer = !showRightDrawer }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Settings", tint = textColor)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = bgColor,
                            titleContentColor = textColor
                        )
                    )
                },
                containerColor = bgColor
            ) { padding ->
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Text(
                            text = "Bible",
                            color = if (currentMode == "World English Bible") goldColor else textColor,
                            fontSize = 15.sp,
                            fontWeight = if (currentMode == "World English Bible") FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    openReading("World English Bible", selectedBook, selectedChapter)
                                    isFullScreen = true
                                }
                                .padding(vertical = 8.dp),
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "Endurance",
                            color = if (currentMode == "Endurance") goldColor else textColor,
                            fontSize = 15.sp,
                            fontWeight = if (currentMode == "Endurance") FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    openReading("Endurance", selectedBook, selectedChapter)
                                    isFullScreen = true
                                }
                                .padding(vertical = 8.dp),
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "Search",
                            color = textColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showSearch = true }
                                .padding(vertical = 8.dp),
                            textAlign = TextAlign.Center
                        )
                    }

                    if (currentMode == "World English Bible") {
                        Text(
                            text = "$selectedBook $selectedChapter",
                            color = textColor,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).padding(horizontal = 20.dp)
                    ) {
                        if (currentMode == "World English Bible") {
                            summary?.let { s ->
                                item(key = "summary-$selectedBook-$selectedChapter") {
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5EEDD))
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(
                                                "CHAPTER SUMMARY - $selectedBook $selectedChapter",
                                                color = goldColor,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(s.summary, color = Color.Black, fontSize = fontSize.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text("What Happened", color = goldColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            Text(s.whatHappened, color = Color.Black, fontSize = fontSize.sp)
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text("Application For You", color = goldColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            Text(s.application, color = Color.Black, fontSize = fontSize.sp)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("Scroll down to start reading ↓", color = Color.Gray, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }

                            if (visibleVerses.isEmpty()) {
                                item {
                                    Text(
                                        "Loading World English Bible...",
                                        color = Color.Gray,
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(vertical = 20.dp)
                                    )
                                }
                            }

                            itemsIndexed(visibleVerses, key = { _, v -> "$selectedBook-$selectedChapter-${v.verse}-WEB" }) { _, verse ->
                                val hl = highlightedVerses[verse.verse]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(hl ?: Color.Transparent, RoundedCornerShape(8.dp))
                                        .combinedClickable(
                                            onClick = {
                                                selectedVerse = verse.verse
                                                scope.launch {
                                                    try {
                                                        prefs.saveReading(currentMode, selectedBook, selectedChapter, verse.verse)
                                                        if (isLoggedIn) {
                                                            syncManager.uploadProgress(
                                                                currentMode,
                                                                selectedBook,
                                                                selectedChapter,
                                                                verse.verse,
                                                                listState.firstVisibleItemIndex,
                                                                listState.firstVisibleItemScrollOffset
                                                            )
                                                        }
                                                    } catch (_: Exception) {
                                                    }
                                                }
                                            },
                                            onLongClick = {
                                                selectedVerse = verse.verse
                                                showHighlightColors = true
                                            }
                                        )
                                        .padding(vertical = 10.dp, horizontal = 8.dp)
                                ) {
                                    Text(text = "${verse.verse}  ", color = goldColor, fontWeight = FontWeight.Bold, fontSize = fontSize.sp)
                                    Text(text = verse.text, color = textColor, fontSize = fontSize.sp)
                                }
                            }
                        } else {
                            item(key = "commentary-$selectedBook-$selectedChapter") {
                                if (commentaryHtml.isEmpty()) {
                                    Text("Loading Endurance...", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 20.dp))
                                } else {
                                    Text(text = commentaryHtml, color = textColor, fontSize = fontSize.sp, modifier = Modifier.padding(vertical = 10.dp))
                                }
                            }
                        }
                    }

                    if (showHighlightColors) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            colors = CardDefaults.cardColors(containerColor = cardColor)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Highlight Verse $selectedVerse - Choose Color", fontWeight = FontWeight.Bold, color = textColor)

                                Row(
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                ) {
                                    highlightColors.forEach { color ->
                                        Button(
                                            onClick = {
                                                selectedVerse?.let { verse ->
                                                    highlightedVerses = highlightedVerses + (verse to color)
                                                    scope.launch {
                                                        try {
                                                            db.bibleDao().saveHighlight(
                                                                HighlightEntity(
                                                                    bookId = currentBookId,
                                                                    chapter = selectedChapter,
                                                                    verse = verse,
                                                                    color = color.value.toString()
                                                                )
                                                            )
                                                            if (isLoggedIn) {
                                                                syncManager.uploadHighlight(currentBookId, selectedChapter, verse, color.value.toString())
                                                            }
                                                        } catch (_: Exception) {
                                                        }
                                                    }
                                                }
                                                showHighlightColors = false
                                            },
                                            modifier = Modifier.size(40.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = color)
                                        ) {}
                                    }
                                }

                                TextButton(
                                    onClick = {
                                        selectedVerse?.let { verse ->
                                            highlightedVerses = highlightedVerses - verse
                                            scope.launch {
                                                try {
                                                    db.bibleDao().deleteHighlight(currentBookId, selectedChapter, verse)
                                                    if (isLoggedIn) {
                                                        syncManager.deleteHighlight(currentBookId, selectedChapter, verse)
                                                    }
                                                } catch (_: Exception) {
                                                }
                                            }
                                        }
                                        showHighlightColors = false
                                    }
                                ) {
                                    Text("Remove Highlight")
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showRightDrawer && !isFullScreen,
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it },
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Card(
                modifier = Modifier.fillMaxHeight().width(300.dp),
                shape = RoundedCornerShape(0.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDarkMode) Color(0xFF2A2A30) else Color.White
                )
            ) {
                LazyColumn(modifier = Modifier.padding(16.dp)) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = textColor)
                            TextButton(onClick = { showRightDrawer = false }) { Text("Close ✕") }
                        }

                        Divider(modifier = Modifier.padding(vertical = 8.dp))

                        if (!isLoggedIn) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        try {
                                            val result = googleAuth.signInWithGoogle()
                                            result.onSuccess { data ->
                                                val name = data.split("|")[0]
                                                val email = data.split("|").getOrNull(1) ?: ""
                                                prefs.saveUser(name, email)
                                                isLoggedIn = true
                                                downloadCloudData()
                                            }
                                        } catch (_: Exception) {
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = goldColor)
                            ) {
                                Text("Login to Sync Data", color = Color.Black)
                            }
                        } else {
                            Text("Welcome, $savedName 👋", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textColor)
                            Text("Synced with your Gmail account - $syncStatus", color = Color.Gray, fontSize = 12.sp)

                            TextButton(
                                onClick = {
                                    scope.launch {
                                        try {
                                            googleAuth.signOut()
                                            prefs.clearUser()
                                            isLoggedIn = false
                                        } catch (_: Exception) {
                                        }
                                    }
                                }
                            ) {
                                Text("Logout")
                            }

                            Button(
                                onClick = { downloadCloudData() },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            ) {
                                Text("Sync Now ↻")
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Dark Mode", color = textColor)
                            Switch(
                                checked = isDarkMode,
                                onCheckedChange = { isDarkMode = it }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Text Size", fontWeight = FontWeight.Bold, color = textColor)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("A-", fontSize = 13.sp, color = textColor)
                            Slider(
                                value = fontSize,
                                onValueChange = { fontSize = it },
                                valueRange = 12f..28f,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                            )
                            Text("A+", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = textColor)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { showDailyVerse = !showDailyVerse; showNotes = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (showDailyVerse) "Daily Verse ▲" else "Daily Verse ▼")
                        }

                        if (showDailyVerse) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5EEDD))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        "DAILY VERSE - Linked to World English Bible",
                                        color = goldColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text("\"Your word is a lamp for my feet, a light on my path.\"", fontSize = 15.sp, color = Color.Black)
                                    Text("Psalm 119:105 - WEB", color = Color.Gray, fontSize = 12.sp)
                                    TextButton(
                                        onClick = {
                                            openReading("World English Bible", "Psalms", 119)
                                            showRightDrawer = false
                                        }
                                    ) {
                                        Text("Open in World English Bible →")
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { showNotes = !showNotes; showDailyVerse = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (showNotes) "Notes ▲" else "Notes ▼")
                        }

                        if (showNotes) {
                            OutlinedTextField(
                                value = noteText,
                                onValueChange = { noteText = it },
                                label = { Text("Write your note...") },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            )

                            Button(
                                onClick = {
                                    scope.launch {
                                        try {
                                            val verseToSave = selectedVerse ?: 1
                                            db.bibleDao().saveNote(
                                                NoteEntity(
                                                    bookId = currentBookId,
                                                    chapter = selectedChapter,
                                                    verse = verseToSave,
                                                    noteText = noteText,
                                                    timestamp = System.currentTimeMillis()
                                                )
                                            )
                                            if (isLoggedIn) {
                                                syncManager.uploadNote(currentBookId, selectedChapter, verseToSave, noteText)
                                            }
                                            noteText = ""
                                            syncStatus = "Note Saved ✅"
                                        } catch (_: Exception) {
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            ) {
                                Text("Save Note")
                            }

                            Text(
                                if (isLoggedIn) "Notes sync to cloud ✅" else "Login to sync notes",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        if (showSearch) {
            AlertDialog(
                onDismissRequest = { showSearch = false },
                title = { Text("Search - World English Bible Only") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("Search WEB...") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Filter:", fontWeight = FontWeight.Bold)

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("All", "Gospel", "Prophecy", "Old Testament", "New Testament").forEach { filter ->
                                FilterChip(
                                    selected = searchFilter == filter,
                                    onClick = { searchFilter = filter },
                                    label = { Text(filter, fontSize = 12.sp) }
                                )
                            }
                        }

                        Text(
                            "Searching in: $searchFilter - World English Bible",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSearch = false }) {
                        Text("Search")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSearch = false }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}