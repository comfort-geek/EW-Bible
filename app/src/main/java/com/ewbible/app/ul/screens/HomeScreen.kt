package com.ewbible.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ewbible.app.data.auth.GoogleAuth
import com.ewbible.app.data.database.*
import com.ewbible.app.data.sync.SyncManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class ReadingLevel { BOOKS, CHAPTERS, VERSES }

data class VerseRef(
    val bookName: String,
    val chapter: Int,
    val verseStart: Int,
    val verseEnd: Int,
    val chapterEnd: Int = chapter
)

data class SearchHit(
    val bookName: String,
    val chapter: Int,
    val verse: Int,
    val text: String
)

private val biblesBookAliases = mapOf(
    "Genesis" to "Genesis",
    "Exodus" to "Exodus",
    "Leviticus" to "Leviticus",
    "Numbers" to "Numbers",
    "Deuteronomy" to "Deuteronomy",
    "Joshua" to "Joshua",
    "Judges" to "Judges",
    "Ruth" to "Ruth",
    "1 Samuel" to "1 Samuel",
    "2 Samuel" to "2 Samuel",
    "1 Kings" to "1 Kings",
    "2 Kings" to "2 Kings",
    "1 Chronicles" to "1 Chronicles",
    "2 Chronicles" to "2 Chronicles",
    "Ezra" to "Ezra",
    "Nehemiah" to "Nehemiah",
    "Esther" to "Esther",
    "Job" to "Job",
    "Psalms" to "Psalms",
    "Psalm" to "Psalms",
    "Proverbs" to "Proverbs",
    "Ecclesiastes" to "Ecclesiastes",
    "Song of Solomon" to "Song of Solomon",
    "Song of Songs" to "Song of Solomon",
    "Isaiah" to "Isaiah",
    "Jeremiah" to "Jeremiah",
    "Lamentations" to "Lamentations",
    "Ezekiel" to "Ezekiel",
    "Daniel" to "Daniel",
    "Hosea" to "Hosea",
    "Joel" to "Joel",
    "Amos" to "Amos",
    "Obadiah" to "Obadiah",
    "Jonah" to "Jonah",
    "Micah" to "Micah",
    "Nahum" to "Nahum",
    "Habakkuk" to "Habakkuk",
    "Zephaniah" to "Zephaniah",
    "Haggai" to "Haggai",
    "Zechariah" to "Zechariah",
    "Malachi" to "Malachi",
    "Matthew" to "Matthew",
    "Mark" to "Mark",
    "Luke" to "Luke",
    "John" to "John",
    "Acts" to "Acts",
    "Romans" to "Romans",
    "1 Corinthians" to "1 Corinthians",
    "2 Corinthians" to "2 Corinthians",
    "Galatians" to "Galatians",
    "Ephesians" to "Ephesians",
    "Philippians" to "Philippians",
    "Colossians" to "Colossians",
    "1 Thessalonians" to "1 Thessalonians",
    "2 Thessalonians" to "2 Thessalonians",
    "1 Timothy" to "1 Timothy",
    "2 Timothy" to "2 Timothy",
    "Titus" to "Titus",
    "Philemon" to "Philemon",
    "Hebrews" to "Hebrews",
    "James" to "James",
    "1 Peter" to "1 Peter",
    "2 Peter" to "2 Peter",
    "1 John" to "1 John",
    "2 John" to "2 John",
    "3 John" to "3 John",
    "Jude" to "Jude",
    "Revelation" to "Revelation",
)

private val verseReferenceRegex: Regex by lazy {
    val bookAlternation = biblesBookAliases.keys
        .sortedByDescending { it.length }
        .joinToString("|") { book ->
            book.split(" ").joinToString("\\s+") { Regex.escape(it) }
        }

    Regex(
        """(?<![A-Za-z])($bookAlternation)\s+(\d{1,3}):(\d{1,3})(?:\s*[-–—]\s*(?:(\d{1,3}):)?(\d{1,3}))?(?!\d)""",
        RegexOption.IGNORE_CASE
    )
}

fun parseVerseRefs(text: String): List<Pair<IntRange, VerseRef>> =
    verseReferenceRegex.findAll(text).mapNotNull { match ->
        val rawBook = match.groupValues[1]
        val canonicalBook = biblesBookAliases.entries
            .firstOrNull { it.key.equals(rawBook, ignoreCase = true) }
            ?.value ?: return@mapNotNull null

        val chapterStart = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
        val verseStart = match.groupValues[3].toIntOrNull() ?: return@mapNotNull null
        val chapterEnd = match.groupValues[4].toIntOrNull() ?: chapterStart
        val verseEnd = match.groupValues[5].toIntOrNull() ?: verseStart

        if (chapterStart < 1 || verseStart < 1 ||
            chapterEnd < chapterStart || verseEnd < 1 ||
            (chapterEnd == chapterStart && verseEnd < verseStart)
        ) {
            return@mapNotNull null
        }

        match.range to VerseRef(
            bookName = canonicalBook,
            chapter = chapterStart,
            verseStart = verseStart,
            verseEnd = verseEnd,
            chapterEnd = chapterEnd
        )
    }.toList()

@Composable
fun ClickableCommentaryText(
    text: String,
    textColor: Color,
    fontSize: TextUnit,
    onVerseClick: (VerseRef) -> Unit
) {
    val refs = remember(text) { parseVerseRefs(text) }
    val annotatedString = remember(text, refs) {
        buildAnnotatedString {
            var last = 0
            refs.forEach { (range, _) ->
                append(text.substring(last, range.first))
                pushStringAnnotation(tag = "VERSE", annotation = refs.indexOfFirst { it.first == range }.toString())
                withStyle(
                    SpanStyle(
                        color = Color(0xFFC9A227),
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    append(text.substring(range))
                }
                pop()
                last = range.last + 1
            }
            if (last < text.length) append(text.substring(last))
        }
    }

    ClickableText(
        text = annotatedString,
        style = TextStyle(color = textColor, fontSize = fontSize),
        onClick = { offset ->
            val annotation = annotatedString.getStringAnnotations("VERSE", offset, offset).firstOrNull()
            if (annotation != null) {
                val index = annotation.item.toInt()
                val ref = refs[index].second
                onVerseClick(ref)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val db = remember { AppDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
    val googleAuth = remember { GoogleAuth(context) }
    val syncManager = remember { SyncManager() }

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
    var activeTab by remember { mutableStateOf("Bible") }
    var selectedBook by remember { mutableStateOf("Genesis") }
    var selectedChapter by remember { mutableStateOf(1) }
    var selectedVerse by remember { mutableStateOf<Int?>(null) }
    var isFirstLoad by remember { mutableStateOf(true) }
    var isDarkMode by remember { mutableStateOf(true) }
    var fontSize by remember { mutableStateOf(16f) }
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchScope by remember { mutableStateOf("Current Chapter") }
    var settingsExpanded by remember { mutableStateOf(false) }
    var highlightedVerses by remember { mutableStateOf<Map<Int, Color>>(emptyMap()) }
    var popupRef by remember { mutableStateOf<VerseRef?>(null) }
    var popupText by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchHit>>(emptyList()) }

    val leftDrawerState = rememberDrawerState(DrawerValue.Closed)
    val booksList by db.bibleDao().getAllBooks().collectAsState(initial = emptyList())
    val currentBookId = booksList.firstOrNull { it.name == selectedBook }?.id ?: booksList.firstOrNull()?.id ?: 1
    val maxChapter by db.bibleDao().getMaxChapter(currentBookId).collectAsState(initial = 1)
    val currentBookChapterCount = maxOf(1, maxChapter)
    val chapterNumbers = remember(currentBookId, currentBookChapterCount) { (1..currentBookChapterCount).toList() }
    val versesList by db.bibleDao().getVerses(currentBookId, selectedChapter).collectAsState(initial = emptyList())
    val listState = rememberLazyListState()

    var summary by remember(selectedBook, selectedChapter) { mutableStateOf<SummaryEntity?>(null) }
    var commentaryHtml by remember(selectedBook, selectedChapter, currentMode) { mutableStateOf("") }
    var scrollRestored by remember { mutableStateOf(false) }

    LaunchedEffect(savedMode, savedBook, savedChapter, savedVerse) {
        if (isFirstLoad) {
            currentMode = savedMode
            selectedBook = savedBook
            selectedChapter = savedChapter
            selectedVerse = savedVerse
            activeTab = when (savedMode) {
                "Endurance" -> "Endurance"
                "Search" -> "Search"
                else -> "Bible"
            }
            isFirstLoad = false
        }
    }

    LaunchedEffect(savedDark, savedFont, savedName) {
        isDarkMode = savedDark
        fontSize = savedFont
    }

    LaunchedEffect(selectedBook, selectedChapter, currentBookId) {
        try {
            summary = db.bibleDao().getSummary(currentBookId, selectedChapter)
        } catch (_: Exception) {
            summary = null
        }
    }

    LaunchedEffect(selectedBook, selectedChapter, currentBookId, currentMode) {
        try {
            if (currentMode == "Endurance" || currentMode == "Commentary") {
                commentaryHtml = db.bibleDao().getCommentary(currentBookId, selectedChapter)?.getHtml() ?: ""
            }
        } catch (_: Exception) {
            commentaryHtml = ""
        }
    }

    LaunchedEffect(db.bibleDao().getHighlights(currentBookId, selectedChapter)) {
        try {
            val dbHighlights by db.bibleDao().getHighlights(currentBookId, selectedChapter).collectAsState(initial = emptyList())
            highlightedVerses = dbHighlights.associate { hl ->
                hl.verse to Color(runCatching { hl.color.toLong() }.getOrDefault(0xFFFFFF00))
            }
        } catch (_: Exception) {
            highlightedVerses = emptyMap()
        }
    }

    val bgColor = if (isDarkMode) Color(0xFF1E1E22) else Color(0xFFFDF8EF)
    val textColor = if (isDarkMode) Color(0xFFF5F0E6) else Color(0xFF1E1E22)
    val cardColor = if (isDarkMode) Color(0xFF2A2A30) else Color(0xFFF0EAD9)
    val goldColor = Color(0xFFC9A227)

    fun syncCurrentReading() = scope.launch {
        try {
            prefs.saveReading(currentMode, selectedBook, selectedChapter, selectedVerse ?: 1)
            prefs.saveScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
            prefs.saveSettings(isDarkMode, fontSize)
        } catch (_: Exception) {
        }
    }

    fun goToBook(bookName: String) {
        selectedBook = bookName
        selectedChapter = 1
        selectedVerse = 1
        currentMode = "World English Bible"
        activeTab = "Bible"
        scope.launch {
            prefs.saveReading(currentMode, selectedBook, selectedChapter, 1)
            prefs.saveScroll(0, 0)
            listState.scrollToItem(0, 0)
        }
    }

    fun goToChapter(chapter: Int) {
        selectedChapter = chapter
        selectedVerse = 1
        scope.launch {
            prefs.saveReading(currentMode, selectedBook, chapter, 1)
            prefs.saveScroll(0, 0)
            listState.scrollToItem(0, 0)
        }
    }

    fun openVerse(ref: VerseRef) {
        popupRef = null
        currentMode = "World English Bible"
        activeTab = "Bible"
        selectedBook = ref.bookName
        selectedChapter = ref.chapter
        selectedVerse = ref.verseStart
        scope.launch {
            prefs.saveReading("World English Bible", ref.bookName, ref.chapter, ref.verseStart)
            prefs.saveScroll(0, 0)
            listState.scrollToItem(0, 0)
        }
    }

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
                val verseNum = listState.firstVisibleItemIndex + 1
                prefs.saveScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
                prefs.saveReading(currentMode, selectedBook, selectedChapter, verseNum)
            } catch (_: Exception) {
            }
        }
    }

    fun loadVersePopup(ref: VerseRef) {
        popupRef = ref
        popupText = "Loading..."
        scope.launch {
            try {
                val book = booksList.firstOrNull { it.name.equals(ref.bookName, ignoreCase = true) }
                    ?: booksList.firstOrNull { it.name.equals(ref.bookName.replace("1 ", "").replace("2 ", ""), ignoreCase = true) }

                if (book == null) {
                    popupText = "Book not found in the Bible database."
                    return@launch
                }

                val results = buildList {
                    for (chapter in ref.chapter..ref.chapterEnd) {
                        val chapterVerses = db.bibleDao().getVerses(book.id, chapter).first()
                        val firstVerse = if (chapter == ref.chapter) ref.verseStart else 1
                        val lastVerse = if (chapter == ref.chapterEnd) ref.verseEnd else Int.MAX_VALUE
                        chapterVerses.filter { it.verse in firstVerse..lastVerse }.forEach { verse ->
                            add("${book.name} $chapter:${verse.verse}  ${verse.text}")
                        }
                    }
                }

                popupText = if (results.isEmpty()) "Verse not found." else results.joinToString("\n")
            } catch (_: Exception) {
                popupText = "Could not load verse."
            }
        }
    }

    LaunchedEffect(searchQuery, searchScope, selectedBook, selectedChapter, currentBookId, booksList) {
        if (searchQuery.isBlank()) {
            searchResults = emptyList()
            return@LaunchedEffect
        }

        scope.launch {
            val results = mutableListOf<SearchHit>()
            try {
                when (searchScope) {
                    "Current Chapter" -> {
                        db.bibleDao().getVerses(currentBookId, selectedChapter).first().forEach { verse ->
                            if (verse.text.contains(searchQuery, ignoreCase = true)) {
                                results += SearchHit(selectedBook, selectedChapter, verse.verse, verse.text)
                            }
                        }
                    }
                    "Current Book" -> {
                        for (chapter in 1..currentBookChapterCount) {
                            db.bibleDao().getVerses(currentBookId, chapter).first().forEach { verse ->
                                if (verse.text.contains(searchQuery, ignoreCase = true)) {
                                    results += SearchHit(selectedBook, chapter, verse.verse, verse.text)
                                }
                            }
                        }
                    }
                    else -> {
                        booksList.forEach { book ->
                            val maxBookChapter = db.bibleDao().getMaxChapter(book.id).first()
                            for (chapter in 1..maxOf(1, maxBookChapter)) {
                                db.bibleDao().getVerses(book.id, chapter).first().forEach { verse ->
                                    if (verse.text.contains(searchQuery, ignoreCase = true)) {
                                        results += SearchHit(book.name, chapter, verse.verse, verse.text)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
            searchResults = results
        }
    }

    val modeButtons = listOf("Bible", "Endurance", "Search")

    fun updateTab(tab: String) {
        activeTab = tab
        when (tab) {
            "Bible" -> currentMode = "World English Bible"
            "Endurance" -> currentMode = "Endurance"
            "Search" -> currentMode = "Search"
        }
    }

    val readingBody = @Composable {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            // no-op; handled below
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            if (dragAmount > 120f) {
                                if (selectedChapter > 1) goToChapter(selectedChapter - 1)
                            } else if (dragAmount < -120f) {
                                if (selectedChapter < currentBookChapterCount) goToChapter(selectedChapter + 1)
                            }
                        }
                    )
                }
        ) {
            when (currentMode) {
                "Search" -> {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search Bible or commentary") },
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Current Chapter", "Current Book", "All Books").forEach { scopeLabel ->
                                FilterChip(
                                    selected = searchScope == scopeLabel,
                                    onClick = { searchScope = scopeLabel },
                                    label = { Text(scopeLabel) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        if (searchQuery.isBlank()) {
                            Text("Type a verse word or phrase to search.", color = textColor)
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                itemsIndexed(searchResults) { _, hit ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedBook = hit.bookName
                                                selectedChapter = hit.chapter
                                                selectedVerse = hit.verse
                                                currentMode = "World English Bible"
                                                activeTab = "Bible"
                                            }
                                            .padding(vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = "${hit.bookName} ${hit.chapter}:${hit.verse}",
                                            fontWeight = FontWeight.Bold,
                                            color = goldColor
                                        )
                                        Text(text = hit.text, color = textColor)
                                    }
                                }
                            }
                        }
                    }
                }

                "Endurance" -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        item {
                            Text(
                                text = "Endurance - ${selectedBook} ${selectedChapter}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = textColor,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        item {
                            if (commentaryHtml.isEmpty()) {
                                Text("Loading Endurance...", color = Color.Gray)
                            } else {
                                ClickableCommentaryText(
                                    text = commentaryHtml,
                                    textColor = textColor,
                                    fontSize = fontSize.sp,
                                    onVerseClick = { ref -> loadVersePopup(ref) }
                                )
                            }
                        }
                    }
                }

                else -> {
                    if (versesList.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Loading Bible...", color = Color.Gray)
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)
                        ) {
                            summary?.let { s ->
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                        colors = CardDefaults.cardColors(containerColor = cardColor)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text("📖 Summary", fontWeight = FontWeight.Bold, color = goldColor)
                                            Text(s.summary, color = textColor)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("What Happened", fontWeight = FontWeight.Bold, color = goldColor)
                                            Text(s.whatHappened, color = textColor)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("Application For You", fontWeight = FontWeight.Bold, color = goldColor)
                                            Text(s.application, color = textColor)
                                        }
                                    }
                                }
                            }
                            itemsIndexed(versesList, key = { _, verse -> "read-${selectedBook}-${selectedChapter}-${verse.verse}" }) { _, verse ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .background(
                                            color = highlightedVerses[verse.verse] ?: Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { selectedVerse = verse.verse }
                                        .padding(vertical = 8.dp, horizontal = 10.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = "${verse.verse} ",
                                        color = goldColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = fontSize.sp,
                                        modifier = Modifier.width(30.dp)
                                    )
                                    Text(
                                        text = verse.text,
                                        color = textColor,
                                        fontSize = fontSize.sp,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun VersePopupDialog() {
        popupRef?.let { ref ->
            AlertDialog(
                onDismissRequest = { popupRef = null },
                title = { Text("${ref.bookName} ${ref.chapter}:${ref.verseStart}${if (ref.verseEnd != ref.verseStart && ref.chapter == ref.chapterEnd) "-${ref.verseEnd}" else ""}", fontWeight = FontWeight.Bold) },
                text = { Text(popupText) },
                confirmButton = {
                    TextButton(onClick = { openVerse(ref) }) {
                        Text("Open in Bible →")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { popupRef = null }) {
                        Text("Close")
                    }
                }
            )
        }
    }

    ModalNavigationDrawer(
        drawerState = leftDrawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Text("Select Book", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = textColor)
                    Spacer(modifier = Modifier.height(12.dp))
                    if (booksList.isEmpty()) {
                        Text("Loading books...", color = Color.Gray)
                    } else {
                        val groupedBooks = booksList.groupBy { it.testament }
                        groupedBooks.entries.forEach { (testament, books) ->
                            Text(testament, fontWeight = FontWeight.Bold, color = goldColor)
                            books.forEach { book ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { goToBook(book.name) }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(book.name, color = textColor, modifier = Modifier.weight(1f))
                                    if (book.name == selectedBook) {
                                        Text("●", color = goldColor)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Chapter ${selectedChapter}", fontWeight = FontWeight.Bold, color = textColor)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        itemsIndexed(chapterNumbers) { _, chapter ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { goToChapter(chapter) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Chapter $chapter", color = textColor, modifier = Modifier.weight(1f))
                                if (chapter == selectedChapter) Text("●", color = goldColor)
                            }
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            containerColor = bgColor,
            topBar = {
                TopAppBar(
                    title = {
                        Text("EW Bible", fontWeight = FontWeight.Bold, color = textColor, fontSize = 18.sp)
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { if (leftDrawerState.isClosed) leftDrawerState.open() else leftDrawerState.close() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = textColor)
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { settingsExpanded = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Settings", tint = textColor)
                            }
                            DropdownMenu(
                                expanded = settingsExpanded,
                                onDismissRequest = { settingsExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Dark mode") },
                                    trailingIcon = {
                                        Switch(
                                            checked = isDarkMode,
                                            onCheckedChange = {
                                                isDarkMode = it
                                                scope.launch { prefs.saveSettings(it, fontSize) }
                                            }
                                        )
                                    },
                                    onClick = { }
                                )
                                DropdownMenuItem(
                                    text = { Text("A-") },
                                    onClick = {
                                        fontSize = (fontSize - 1f).coerceAtLeast(12f)
                                        scope.launch { prefs.saveSettings(isDarkMode, fontSize) }
                                        settingsExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("A+") },
                                    onClick = {
                                        fontSize = (fontSize + 1f).coerceAtMost(24f)
                                        scope.launch { prefs.saveSettings(isDarkMode, fontSize) }
                                        settingsExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Reset") },
                                    onClick = {
                                        selectedBook = "Genesis"
                                        selectedChapter = 1
                                        selectedVerse = 1
                                        isDarkMode = true
                                        fontSize = 16f
                                        scope.launch { prefs.saveReading("World English Bible", "Genesis", 1, 1); prefs.saveSettings(true, 16f) }
                                        settingsExpanded = false
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor, titleContentColor = textColor)
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor)
                    .padding(padding)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    modeButtons.forEach { tab ->
                        val selected = tab == activeTab
                        Button(
                            onClick = { updateTab(tab) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) goldColor else cardColor,
                                contentColor = if (selected) Color.Black else textColor
                            )
                        ) {
                            Text(tab)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$selectedBook $selectedChapter",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { scope.launch { leftDrawerState.open() } }) {
                        Icon(Icons.Default.Menu, contentDescription = "Book Menu", tint = goldColor)
                    }
                }

                if (showSearchBar) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        placeholder = { Text("Search verse text") }
                    )
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    readingBody()
                }
            }
        }
    }

    BackHandler(enabled = currentMode == "Search" && showSearchBar) {
        showSearchBar = false
    }

    if (popupRef != null) {
        VersePopupDialog()
    }
}

