package com.ewbible.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

enum class ReadingLevel { BOOKS, CHAPTERS, VERSES }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen() {
    val leftDrawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var currentMode by remember { mutableStateOf("World English Bible") } // or Commentary
    var readingLevel by remember { mutableStateOf(ReadingLevel.BOOKS) }
    var selectedBook by remember { mutableStateOf("Genesis") }
    var selectedChapter by remember { mutableStateOf(1) }
    var selectedVerse by remember { mutableStateOf<Int?>(null) }

    // Right Drawer - slides
    var showRightDrawer by remember { mutableStateOf(false) }
    var isLoggedIn by remember { mutableStateOf(false) }
    var userName by remember { mutableStateOf("Guest") }
    var isDarkMode by remember { mutableStateOf(true) }
    var fontSize by remember { mutableStateOf(16f) }
    var showDailyVerse by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }

    // Search
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchFilter by remember { mutableStateOf("All") }

    // Highlight - long press verse
    var showHighlightColors by remember { mutableStateOf(false) }
    var highlightedVerses by remember { mutableStateOf<Map<Int, Color>>(emptyMap()) }
    val highlightColors = listOf(Color.Yellow, Color.Green, Color(0xFFFFB6C1), Color.Cyan, Color(0xFFFFA500))

    val bgColor = if (isDarkMode) Color(0xFF121212) else Color.White
    val textColor = if (isDarkMode) Color.White else Color.Black
    val cardColor = if (isDarkMode) Color(0xFF2A2A2A) else Color(0xFFF0F0F0)

    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {

        // ---------- LEFT DRAWER ----------
        ModalNavigationDrawer(
            drawerState = leftDrawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Text(text = currentMode, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Grow in the Word of God, every day.", color = Color(0xFFC9A227), fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = when (readingLevel) {
                                ReadingLevel.BOOKS -> "Book"
                                ReadingLevel.CHAPTERS -> "$selectedBook - Chapter"
                                ReadingLevel.VERSES -> "$selectedBook $selectedChapter - Verse"
                            },
                            fontWeight = FontWeight.Bold, fontSize = 16.sp
                        )
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            when (readingLevel) {
                                ReadingLevel.BOOKS -> {
                                    val books = listOf("Genesis","Exodus","Leviticus","Psalms","Isaiah","Matthew","Mark","Luke","John","Acts","Romans","Revelation")
                                    items(books) { book ->
                                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(text = book, fontSize = 16.sp, modifier = Modifier.weight(1f))
                                            Text(text = ">", color = Color.Gray)
                                        }
                                        Divider()
                                    }
                                }
                                ReadingLevel.CHAPTERS -> {
                                    items((1..50).toList()) { ch ->
                                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                                            Text(text = "Chapter $ch", fontSize = 16.sp, modifier = Modifier.weight(1f))
                                            Text(text = ">", color = Color.Gray)
                                        }
                                        Divider()
                                    }
                                }
                                ReadingLevel.VERSES -> {
                                    items((1..31).toList()) { v ->
                                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                                            Text(text = "Verse $v", fontSize = 16.sp, modifier = Modifier.weight(1f))
                                        }
                                        Divider()
                                    }
                                }
                            }
                        }
                        if (readingLevel == ReadingLevel.CHAPTERS) {
                            Button(onClick = { readingLevel = ReadingLevel.BOOKS }, modifier = Modifier.fillMaxWidth()) { Text("← Back to Books") }
                        }
                        if (readingLevel == ReadingLevel.VERSES) {
                            Button(onClick = { readingLevel = ReadingLevel.CHAPTERS }, modifier = Modifier.fillMaxWidth()) { Text("← Back to Chapters") }
                        }
                        Button(onClick = {
                            when (readingLevel) {
                                ReadingLevel.BOOKS -> readingLevel = ReadingLevel.CHAPTERS
                                ReadingLevel.CHAPTERS -> readingLevel = ReadingLevel.VERSES
                                ReadingLevel.VERSES -> scope.launch { leftDrawerState.close() }
                            }
                        }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Open →") }
                    }
                }
            }
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("EW Bible", fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { if (leftDrawerState.isClosed) leftDrawerState.open() else leftDrawerState.close() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        },
                        actions = {
                            IconButton(onClick = { showRightDrawer = !showRightDrawer }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Settings")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor, titleContentColor = textColor)
                    )
                },
                containerColor = bgColor
            ) { padding ->
                Column(modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
                    Text(text = "EW Bible", color = textColor, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Grow in the Word of God, every day.", color = Color(0xFFC9A227), fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Now: $currentMode - $selectedBook $selectedChapter", color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(24.dp))

                    // 3 BUTTONS - NO ICONS - CORRECT NAMES
                    Button(onClick = { currentMode = "World English Bible"; readingLevel = ReadingLevel.BOOKS; scope.launch { leftDrawerState.open() } }, modifier = Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC9A227))) {
                        Text("World English Bible", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { currentMode = "Commentary"; readingLevel = ReadingLevel.BOOKS; scope.launch { leftDrawerState.open() } }, modifier = Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = cardColor)) {
                        Text("Commentary", color = textColor, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { showSearch = true }, modifier = Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = cardColor)) {
                        Text("Search", color = textColor, fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Text(text = "Reading Preview - Hold a verse to highlight:", color = Color.Gray, fontSize = 12.sp)
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items((1..10).toList()) { verseNum ->
                            val hl = highlightedVerses[verseNum]
                            Row(modifier = Modifier.fillMaxWidth().background(hl ?: Color.Transparent, RoundedCornerShape(8.dp)).combinedClickable(
                                onClick = { selectedVerse = verseNum },
                                onLongClick = { selectedVerse = verseNum; showHighlightColors = true }
                            ).padding(12.dp)) {
                                Text(text = "$verseNum  ", color = Color(0xFFC9A227), fontWeight = FontWeight.Bold, fontSize = fontSize.sp)
                                Text(text = "In the beginning God created the heavens and the earth. - $currentMode", color = textColor, fontSize = fontSize.sp)
                            }
                        }
                    }
                    if (showHighlightColors) {
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = cardColor)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Highlight Verse $selectedVerse - Choose Color", fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                    highlightColors.forEach { c ->
                                        Button(onClick = { selectedVerse?.let { highlightedVerses = highlightedVerses + (it to c) }; showHighlightColors = false }, modifier = Modifier.size(40.dp), colors = ButtonDefaults.buttonColors(containerColor = c)) {}
                                    }
                                }
                                TextButton(onClick = { selectedVerse?.let { highlightedVerses = highlightedVerses - it }; showHighlightColors = false }) { Text("Remove Highlight") }
                            }
                        }
                    }
                }
            }
        }

        // ---------- RIGHT DRAWER - SLIDES FROM RIGHT ----------
        AnimatedVisibility(visible = showRightDrawer, enter = slideInHorizontally { it }, exit = slideOutHorizontally { it }, modifier = Modifier.align(Alignment.TopEnd)) {
            Card(modifier = Modifier.fillMaxHeight().width(300.dp), shape = RoundedCornerShape(0.dp), colors = CardDefaults.cardColors(containerColor = if (isDarkMode) Color(0xFF1E1E1E) else Color.White)) {
                LazyColumn(modifier = Modifier.padding(16.dp)) {
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { showRightDrawer = false }) { Text("Close ✕") }
                        }
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        if (!isLoggedIn) {
                            Button(onClick = { isLoggedIn = true; userName = "Your Gmail Name" }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC9A227))) {
                                Text("Login to Sync Data", color = Color.Black)
                            }
                            Text("Login with Gmail to sync Notes & Highlights", color = Color.Gray, fontSize = 11.sp)
                        } else {
                            Text("Welcome, $userName 👋", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("Synced with your Gmail account", color = Color.Gray, fontSize = 12.sp)
                            TextButton(onClick = { isLoggedIn = false }) { Text("Logout") }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Dark Mode")
                            Switch(checked = isDarkMode, onCheckedChange = { isDarkMode = it })
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Text Size", fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("A-", fontSize = 13.sp)
                            Slider(value = fontSize, onValueChange = { fontSize = it }, valueRange = 12f..28f, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                            Text("A+", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { showDailyVerse = !showDailyVerse; showNotes = false }, modifier = Modifier.fillMaxWidth()) { Text(if (showDailyVerse) "Daily Verse ▲" else "Daily Verse ▼") }
                        if (showDailyVerse) {
                            Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF5EEDD))) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("DAILY VERSE - Linked to World English Bible", color = Color(0xFFC9A227), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text("\"Your word is a lamp for my feet, a light on my path.\"", fontSize = 15.sp, color = Color.Black)
                                    Text("Psalm 119:105 - WEB", color = Color.Gray, fontSize = 12.sp)
                                    TextButton(onClick = { currentMode = "World English Bible"; showRightDrawer = false; scope.launch { leftDrawerState.open() } }) { Text("Open in World English Bible →") }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { showNotes = !showNotes; showDailyVerse = false }, modifier = Modifier.fillMaxWidth()) { Text(if (showNotes) "Notes ▲" else "Notes ▼") }
                        if (showNotes) {
                            OutlinedTextField(value = noteText, onValueChange = { noteText = it }, label = { Text("Write your note...") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                            Text("Notes will sync after Login", color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // ---------- SEARCH - ONLY WEB ----------
        if (showSearch) {
            AlertDialog(
                onDismissRequest = { showSearch = false },
                title = { Text("Search - World English Bible Only") },
                text = {
                    Column {
                        OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, label = { Text("Search WEB...") }, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Filter:", fontWeight = FontWeight.Bold)
                        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("All","Gospel","Prophecy","Old Testament","New Testament").forEach { f ->
                                FilterChip(selected = searchFilter == f, onClick = { searchFilter = f }, label = { Text(f, fontSize = 12.sp) })
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Searching in: $searchFilter - World English Bible", color = Color.Gray, fontSize = 12.sp)
                        Text("Results for \"$searchQuery\" will show here in Step 8 from DB.", color = Color.Gray, fontSize = 12.sp)
                    }
                },
                confirmButton = { TextButton(onClick = { showSearch = false }) { Text("Search") } },
                dismissButton = { TextButton(onClick = { showSearch = false }) { Text("Close") } }
            )
        }
    }
}
