package com.ewbible.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val db = remember { AppDatabase.getInstance(context) }
    val googleAuth = remember { GoogleAuth(context) }
    val syncManager = remember { SyncManager() }
    val scope = rememberCoroutineScope()

    val savedDark by prefs.isDarkMode.collectAsState(initial = true)
    val savedFont by prefs.fontSize.collectAsState(initial = 16f)
    val savedName by prefs.userName.collectAsState(initial = "Guest")
    val savedBook by prefs.lastBook.collectAsState(initial = "Genesis")
    val savedChapter by prefs.lastChapter.collectAsState(initial = 1)
    val savedVerse by prefs.lastVerse.collectAsState(initial = 1)

    var isDarkMode by remember { mutableStateOf(savedDark) }
    var fontSize by remember { mutableStateOf(savedFont) }
    var isLoggedIn by remember { mutableStateOf(savedName != "Guest") }

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

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val booksList by db.bibleDao().getAllBooks().collectAsState(initial = emptyList())
    val bookMap = booksList.associateBy { it.name }
    val currentBookId = bookMap[selectedBook]?.id ?: 1

    val versesList by db.bibleDao().getVerses(currentBookId, selectedChapter).collectAsState(initial = emptyList())
    val highlightList by db.bibleDao().getHighlights(currentBookId, selectedChapter).collectAsState(initial = emptyList())
    val currentHighlightMap = highlightList.associate { hl ->
        hl.verse to runCatching { Color(hl.color.toLong(16)) }.getOrDefault(Color.Yellow)
    }

    var summary by remember(selectedBook, selectedChapter) { mutableStateOf<SummaryEntity?>(null) }
    LaunchedEffect(selectedBook, selectedChapter, currentBookId) {
        summary = db.bibleDao().getSummary(currentBookId, selectedChapter)
    }

    var commentaryHtml by remember(selectedBook, selectedChapter) { mutableStateOf("") }
    LaunchedEffect(selectedBook, selectedChapter, currentBookId, selectedTab) {
        if (selectedTab == "Open EW") {
            commentaryHtml = db.bibleDao().getCommentary(currentBookId, selectedChapter)?.getHtml() ?: ""
        }
    }

    val listState = rememberLazyListState()
    val gold = Color(0xFFC9A227)
    val background = if (isDarkMode) Color(0xFF1E1E22) else Color(0xFFFDF8EF)
    val panel = if (isDarkMode) Color(0xFF2A2A30) else Color(0xFFF0EAD9)
    val textColor = if (isDarkMode) Color(0xFFF5F0E6) else Color(0xFF1E1E22)
    val secondaryText = if (isDarkMode) Color(0xFFB7B0A6) else Color(0xFF524B40)

    val otBooks = listOf(
        "Genesis","Exodus","Leviticus","Numbers","Deuteronomy","Joshua","Judges","Ruth",
        "1 Samuel","2 Samuel","1 Kings","2 Kings","1 Chronicles","2 Chronicles","Ezra",
        "Nehemiah","Esther","Job","Psalms","Proverbs","Ecclesiastes","Song of Solomon",
        "Isaiah","Jeremiah","Lamentations","Ezekiel","Daniel","Hosea","Joel","Amos",
        "Obadiah","Jonah","Micah","Nahum","Habakkuk","Zephaniah","Haggai","Zechariah","Malachi"
    )

    val ntBooks = listOf(
        "Matthew","Mark","Luke","John","Acts","Romans","1 Corinthians","2 Corinthians",
        "Galatians","Ephesians","Philippians","Colossians","1 Thessalonians",
        "2 Thessalonians","1 Timothy","2 Timothy","Titus","Philemon","Hebrews",
        "James","1 Peter","2 Peter","1 John","2 John","3 John","Jude","Revelation"
    )

    val gospelBooks = listOf("Matthew", "Mark", "Luke", "John")
    val prophecyBooks = listOf(
        "Isaiah","Jeremiah","Ezekiel","Daniel","Hosea","Joel","Amos","Micah","Zechariah",
        "Habakkuk","Malachi","Jonah","Nahum","Obadiah"
    )

    val availableBooks = booksList.ifEmpty { otBooks + ntBooks }.sortedBy { it }
    val filteredSearchResults = remember(searchQuery, searchFilter, availableBooks) {
        val q = searchQuery.trim()
        val list = when (searchFilter) {
            "Gospel" -> availableBooks.filter { it in gospelBooks }
            "Prophecy" -> availableBooks.filter { it in prophecyBooks }
            "Old Testament" -> availableBooks.filter { it in otBooks }
            "New Testament" -> availableBooks.filter { it in ntBooks }
            else -> availableBooks
        }
        if (q.isEmpty()) list else list.filter { it.contains(q, ignoreCase = true) }
    }

    LaunchedEffect(savedDark, savedFont, savedName) {
        isDarkMode = savedDark
        fontSize = savedFont
        isLoggedIn = savedName != "Guest"
    }

    LaunchedEffect(savedBook, savedChapter) {
        selectedBook = savedBook
        selectedChapter = savedChapter
    }

    LaunchedEffect(Unit) {
        val dailyBook = booksList.firstOrNull { it.name == "Psalms" }
        if (dailyBook != null) {
            val verses = db.bibleDao().getVerses(dailyBook.id, 119).first()
            val verse = verses.firstOrNull { it.verse == 105 }
            if (verse != null) {
                dailyVerseText = "${verse.text} - Psalm 119:105"
            }
        }
    }

    LaunchedEffect(isDarkMode, fontSize) {
        scope.launch {
            prefs.saveSettings(isDarkMode, fontSize)
        }
    }

    fun saveReadingProgress() {
        scope.launch {
            try {
                prefs.saveReading("World English Bible", selectedBook, selectedChapter, selectedVerse ?: 1)
                prefs.saveScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
            } catch (_: Exception) {
            }
        }
    }

    fun openBook(bookName: String, chapter: Int = 1) {
        selectedBook = bookName
        selectedChapter = chapter
        selectedVerse = 1
        expandedBook = bookName
        scope.launch { prefs.saveReading("World English Bible", bookName, chapter, 1) }
        scope.launch { drawerState.close() }
    }

    fun handleGoogleLogin() {
        scope.launch {
            try {
                val result = googleAuth.signInWithGoogle()
                result.onSuccess { data ->
                    val name = data.split("|").firstOrNull() ?: "User"
                    val email = data.split("|").getOrNull(1) ?: ""
                    prefs.saveUser(name, email)
                    isLoggedIn = true
                }
            } catch (_: Exception) {
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerShape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp),
                modifier = Modifier.width(300.dp),
                containerColor = background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Books",
                        color = textColor,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            Text("Old Testament", color = gold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        items(otBooks) { book ->
                            val isSelected = selectedBook == book
                            val isExpanded = expandedBook == book
                            val chapterCount = bookMap[book]?.let { bookId ->
                                maxOf(1, runCatching { db.bibleDao().getMaxChapter(bookId).first() }.getOrDefault(1))
                            } ?: 1

                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            expandedBook = if (isExpanded) null else book
                                            if (!isExpanded) {
                                                selectedBook = book
                                                selectedChapter = 1
                                            }
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = book,
                                        color = if (isSelected) gold else textColor,
                                        fontSize = 15.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(if (isExpanded) "▾" else "▸", color = secondaryText)
                                }

                                if (isExpanded) {
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        for (chapter in 1..chapterCount) {
                                            val selected = selectedBook == book && selectedChapter == chapter
                                            Text(
                                                text = "Ch $chapter",
                                                color = if (selected) Color.White else textColor,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                                modifier = Modifier
                                                    .background(
                                                        if (selected) gold else panel,
                                                        RoundedCornerShape(8.dp)
                                                    )
                                                    .clickable {
                                                        selectedBook = book
                                                        selectedChapter = chapter
                                                        selectedVerse = 1
                                                        scope.launch { prefs.saveReading("World English Bible", book, chapter, 1) }
                                                        scope.launch { drawerState.close() }
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("New Testament", color = gold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        items(ntBooks) { book ->
                            val isSelected = selectedBook == book
                            val isExpanded = expandedBook == book
                            val chapterCount = bookMap[book]?.let { bookId ->
                                maxOf(1, runCatching { db.bibleDao().getMaxChapter(bookId).first() }.getOrDefault(1))
                            } ?: 1

                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            expandedBook = if (isExpanded) null else book
                                            if (!isExpanded) {
                                                selectedBook = book
                                                selectedChapter = 1
                                            }
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = book,
                                        color = if (isSelected) gold else textColor,
                                        fontSize = 15.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(if (isExpanded) "▾" else "▸", color = secondaryText)
                                }

                                if (isExpanded) {
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        for (chapter in 1..chapterCount) {
                                            val selected = selectedBook == book && selectedChapter == chapter
                                            Text(
                                                text = "Ch $chapter",
                                                color = if (selected) Color.White else textColor,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                                modifier = Modifier
                                                    .background(
                                                        if (selected) gold else panel,
                                                        RoundedCornerShape(8.dp)
                                                    )
                                                    .clickable {
                                                        selectedBook = book
                                                        selectedChapter = chapter
                                                        selectedVerse = 1
                                                        scope.launch { prefs.saveReading("World English Bible", book, chapter, 1) }
                                                        scope.launch { drawerState.close() }
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Text("EW Bible", color = textColor, fontWeight = FontWeight.Bold)
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                }
                            }
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = textColor)
                        }
                    },
                    actions = {
                        IconButton(onClick = { settingsOpen = !settingsOpen }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Settings", tint = textColor)
                        }

                        DropdownMenu(
                            expanded = settingsOpen,
                            onDismissRequest = { settingsOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (isLoggedIn) "Logged in" else "Log in with Google") },
                                onClick = {
                                    settingsOpen = false
                                    if (!isLoggedIn) handleGoogleLogin()
                                }
                            )

                            DropdownMenuItem(
                                text = { Text(if (isDarkMode) "Light mode" else "Dark mode") },
                                onClick = {
                                    settingsOpen = false
                                    isDarkMode = !isDarkMode
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("A-", color = textColor)
                                        Slider(
                                            value = fontSize,
                                            onValueChange = { fontSize = it },
                                            valueRange = 12f..28f,
                                            modifier = Modifier.width(120.dp).padding(horizontal = 6.dp)
                                        )
                                        Text("A+", color = textColor)
                                    }
                                },
                                onClick = {}
                            )

                            DropdownMenuItem(
                                text = { Text("Daily Verse") },
                                onClick = {
                                    settingsOpen = false
                                    dailyVerseDialog = true
                                }
                            )

                            DropdownMenuItem(
                                text = { Text(if (showNotes) "Hide Notes" else "Notes") },
                                onClick = {
                                    settingsOpen = false
                                    showNotes = !showNotes
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = background)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val tabs = listOf("Bible", "Open EW", "Search")
                    tabs.forEach { tab ->
                        Text(
                            text = tab,
                            color = if (selectedTab == tab) gold else textColor,
                            fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedTab = tab }
                                .padding(vertical = 8.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                when (selectedTab) {
                    "Bible" -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = "$selectedBook $selectedChapter",
                                color = textColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
                            )

                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 18.dp)
                            ) {
                                summary?.let { s ->
                                    item {
                                        Card(
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                            colors = CardDefaults.cardColors(containerColor = panel)
                                        ) {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Text("Summary", color = gold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(s.summary, color = textColor, fontSize = fontSize.sp)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text("What happened", color = gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Text(s.whatHappened, color = textColor, fontSize = fontSize.sp)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text("Application", color = gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Text(s.application, color = textColor, fontSize = fontSize.sp)
                                            }
                                        }
                                    }
                                }

                                itemsIndexed(
                                    versesList,
                                    key = { _, verse -> "${selectedBook}-${selectedChapter}-${verse.verse}" }
                                ) { _, verse ->
                                    val verseBg = currentHighlightMap[verse.verse] ?: Color.Transparent
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(verseBg, RoundedCornerShape(8.dp))
                                            .combinedClickable(
                                                onClick = {
                                                    selectedVerse = verse.verse
                                                    saveReadingProgress()
                                                },
                                                onLongClick = {
                                                    selectedVerse = verse.verse
                                                    showHighlightColors = true
                                                }
                                            )
                                            .padding(vertical = 10.dp, horizontal = 8.dp)
                                    ) {
                                        Text(
                                            text = "${verse.verse}",
                                            color = gold,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = fontSize.sp,
                                            modifier = Modifier.width(32.dp)
                                        )
                                        Text(
                                            text = verse.text,
                                            color = textColor,
                                            fontSize = fontSize.sp,
                                            lineHeight = (fontSize + 6).sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    "Open EW" -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 18.dp, vertical = 8.dp)
                        ) {
                            item {
                                if (commentaryHtml.isEmpty()) {
                                    Text("No commentary available for this chapter.", color = secondaryText, fontSize = fontSize.sp)
                                } else {
                                    Text(
                                        text = commentaryHtml,
                                        color = textColor,
                                        fontSize = fontSize.sp,
                                        lineHeight = (fontSize + 6).sp
                                    )
                                }
                            }
                        }
                    }

                    "Search" -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 18.dp, vertical = 10.dp)
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                label = { Text("Search Bible...") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("All", "Gospel", "Prophecy", "Old Testament", "New Testament").forEach { filter ->
                                    FilterChip(
                                        selected = searchFilter == filter,
                                        onClick = { searchFilter = filter },
                                        label = { Text(filter, fontSize = 12.sp) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(filteredSearchResults) { bookName ->
                                    Text(
                                        text = bookName,
                                        color = textColor,
                                        fontSize = fontSize.sp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                openBook(bookName, 1)
                                                selectedTab = "Bible"
                                            }
                                            .padding(vertical = 10.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (showNotes) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = panel)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Notes", color = gold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = noteText,
                                onValueChange = { noteText = it },
                                label = { Text("Write your note...") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    val verseNum = selectedVerse ?: 1
                                    scope.launch {
                                        db.bibleDao().saveNote(
                                            NoteEntity(
                                                bookId = currentBookId,
                                                chapter = selectedChapter,
                                                verse = verseNum,
                                                noteText = noteText,
                                                timestamp = System.currentTimeMillis()
                                            )
                                        )
                                        if (isLoggedIn) {
                                            syncManager.uploadNote(currentBookId, selectedChapter, verseNum, noteText)
                                        }
                                        noteText = ""
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Save Note")
                            }
                        }
                    }
                }

                if (showHighlightColors) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = panel)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Highlight Verse ${selectedVerse ?: 1}", color = gold, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                val palette = listOf(
                                    Color.Yellow,
                                    Color.Green,
                                    Color(0xFFFFB6C1),
                                    Color.Cyan,
                                    Color(0xFFFFA500)
                                )

                                palette.forEach { color ->
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .background(color, RoundedCornerShape(8.dp))
                                            .clickable {
                                                selectedVerse?.let { verse ->
                                                    scope.launch {
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
                                                    }
                                                }
                                                showHighlightColors = false
                                            }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            TextButton(
                                onClick = {
                                    selectedVerse?.let { verse ->
                                        scope.launch {
                                            db.bibleDao().deleteHighlight(currentBookId, selectedChapter, verse)
                                            if (isLoggedIn) {
                                                syncManager.deleteHighlight(currentBookId, selectedChapter, verse)
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

            if (dailyVerseDialog) {
                AlertDialog(
                    onDismissRequest = { dailyVerseDialog = false },
                    title = { Text("Daily Verse", color = gold, fontWeight = FontWeight.Bold) },
                    text = {
                        Text(
                            text = dailyVerseText,
                            color = textColor,
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize + 8).sp,
                            textAlign = TextAlign.Center
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { dailyVerseDialog = false }) {
                            Text("Close")
                        }
                    }
                )
            }
        }
    }
}