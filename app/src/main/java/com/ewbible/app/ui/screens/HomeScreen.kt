package com.ewbible.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.ewbible.app.data.auth.GoogleAuth
import com.ewbible.app.data.database.AppDatabase
import com.ewbible.app.data.database.AppPreferences
import com.ewbible.app.data.sync.SyncManager

@Composable
fun HomeScreen(
    db: AppDatabase = AppDatabase.getInstance(LocalContext.current),
    prefs: AppPreferences = AppPreferences(LocalContext.current),
    auth: GoogleAuth = GoogleAuth(LocalContext.current),
    sync: SyncManager = SyncManager()
) {
    // --- YOUR ORIGINAL STRUCTURE - UNTOUCHED ---
    val books by db.bibleDao().getAllBooks().collectAsState(initial = emptyList())
    val lastBook by prefs.lastBook.collectAsState(initial = "Genesis")

    // --- UI STATE ONLY - no DB calls ---
    var selectedBook by remember(lastBook) { mutableStateOf(lastBook) }
    var selectedChapter by remember { mutableStateOf(1) }
    var tab by remember { mutableStateOf("Bible") }
    var drawerOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var dailyVerseText by remember { mutableStateOf("") }
    var fontSize by remember { mutableStateOf(16f) }
    var isDark by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<String>>(emptyList()) }

    // Colors - light dimmed ergonomic
    val bgColor = if (isDark) Color(0xFF202328) else Color(0xFFEDE6D3)
    val textColor = if (isDark) Color.White else Color(0xFF2B2820)
    val cardColor = if (isDark) Color(0xFF2A2E35) else Color(0xFFF7F1E3)
    val readingGold = Color(0xFFB78F3A)

    // Keep selectedBook in sync when lastBook loads
    LaunchedEffect(lastBook) { if (lastBook.isNotBlank()) selectedBook = lastBook }

    // Book names from YOUR books Flow - no hardcoded DB needed
    val bookNames = books.map { it.name }
    // Fallback if DB empty yet
    val displayBooks = if (bookNames.isNotEmpty()) bookNames else listOf("Genesis", "Exodus", "Deuteronomy", "Matthew", "John", "Revelation")
    val otBooks = listOf("Genesis","Exodus","Leviticus","Numbers","Deuteronomy","Joshua","Judges","Ruth","1 Samuel","2 Samuel","1 Kings","2 Kings","1 Chronicles","2 Chronicles","Ezra","Nehemiah","Esther","Job","Psalms","Proverbs","Ecclesiastes","Song of Solomon","Isaiah","Jeremiah","Lamentations","Ezekiel","Daniel","Hosea","Joel","Amos","Obadiah","Jonah","Micah","Nahum","Habakkuk","Zephaniah","Haggai","Zechariah","Malachi")
    val ntBooks = listOf("Matthew","Mark","Luke","John","Acts","Romans","1 Corinthians","2 Corinthians","Galatians","Ephesians","Philippians","Colossians","1 Thessalonians","2 Thessalonians","1 Timothy","2 Timothy","Titus","Philemon","Hebrews","James","1 Peter","2 Peter","1 John","2 John","3 John","Jude","Revelation")

    Box(Modifier.fillMaxSize().background(bgColor)) {
        Column(Modifier.fillMaxSize()) {
            // --- TOP ROW: [Menu] [Tabs] [3-dots] - FIXED ---
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { drawerOpen = true }, contentPadding = PaddingValues(4.dp)) { Text("☰", color = textColor, fontSize = 22.sp) }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                    listOf("Bible", "Endurance", "Search").forEach { t ->
                        Text(
                            text = t,
                            color = if (tab == t) readingGold else textColor,
                            fontWeight = if (tab == t) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 15.sp,
                            maxLines = 1,
                            modifier = Modifier.clickable { tab = t }.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }
                Box {
                    TextButton(onClick = { settingsOpen = true }, contentPadding = PaddingValues(4.dp)) { Text("⋮", color = textColor, fontSize = 22.sp) }
                    DropdownMenu(expanded = settingsOpen, onDismissRequest = { settingsOpen = false }) {
                        DropdownMenuItem(text = { Text("Google sign in") }, onClick = { settingsOpen = false })
                        DropdownMenuItem(text = { Text(if (isDark) "Light mode" else "Dark mode") }, onClick = { isDark = !isDark; settingsOpen = false })
                        // Font Slider A- [slider] A+ - FIXED
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("A-", color = textColor)
                                    Slider(value = fontSize, onValueChange = { fontSize = it }, valueRange = 12f..24f, modifier = Modifier.weight(1f).padding(horizontal = 6.dp))
                                    Text("A+", color = textColor)
                                }
                            },
                            onClick = {}
                        )
                        DropdownMenuItem(text = { Text("Daily Verse") }, onClick = {
                            settingsOpen = false
                            dailyVerseText = "$selectedBook $selectedChapter:1\n\nDaily verse will show here once verses are wired from your DAO."
                            dialog = "daily"
                        })
                    }
                }
            }

            // Title ONCE only
            Text("$selectedBook $selectedChapter", color = textColor, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

            when (tab) {
                "Bible" -> {
                    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
                        Text("EW Bible", style = MaterialTheme.typography.headlineMedium, color = textColor)
                        Text("Books loaded: ${books.size}", color = textColor, modifier = Modifier.padding(top = 12.dp), fontSize = fontSize.sp)
                        Text("Last book: $lastBook", color = textColor, modifier = Modifier.padding(top = 8.dp), fontSize = fontSize.sp)
                        // Summary card - placeholder, will show real DB summary once you send DAO
                        Card(colors = CardDefaults.cardColors(containerColor = cardColor), modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                            Column(Modifier.padding(14.dp)) {
                                Text("Chapter Summary", color = readingGold, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Text("Summary from database will appear here for $selectedBook $selectedChapter.", color = textColor, fontSize = fontSize.sp, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                        Button(onClick = {}, modifier = Modifier.padding(top = 16.dp)) { Text("Open Bible") }
                        Spacer(Modifier.height(80.dp))
                    }
                }
                "Endurance" -> {
                    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
                        Text("Commentary - $selectedBook $selectedChapter", color = readingGold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Commentary from database will appear here. No <p> tags, verse links will be blue and tappable.", color = textColor, fontSize = fontSize.sp, modifier = Modifier.padding(top = 10.dp))
                        Spacer(Modifier.height(80.dp))
                    }
                }
                "Search" -> {
                    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, label = { Text("Search Bible...") }, modifier = Modifier.fillMaxWidth())
                        Button(onClick = {
                            searchResults = displayBooks.filter { it.contains(searchQuery, ignoreCase = true) }
                        }, modifier = Modifier.padding(top = 8.dp)) { Text("Search") }
                        LazyColumn(Modifier.weight(1f)) {
                            items(searchResults) { bName ->
                                Text(bName, color = textColor, fontSize = fontSize.sp, modifier = Modifier.fillMaxWidth().clickable { selectedBook = bName; selectedChapter = 1; tab = "Bible" }.padding(vertical = 8.dp))
                            }
                        }
                    }
                }
            }
        }

        // Drawer - scrollable OT then NT
        if (drawerOpen) {
            AlertDialog(
                onDismissRequest = { drawerOpen = false },
                title = { Text("Select Book", color = textColor) },
                text = {
                    LazyColumn(Modifier.fillMaxWidth().height(420.dp)) {
                        item { Text("OLD TESTAMENT", color = readingGold, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
                        items(otBooks) { b ->
                            Text(b, color = if (b == selectedBook) readingGold else textColor, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().clickable { selectedBook = b; selectedChapter = 1; drawerOpen = false }.padding(vertical = 8.dp))
                        }
                        item { Text("NEW TESTAMENT", color = readingGold, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
                        items(ntBooks) { b ->
                            Text(b, color = if (b == selectedBook) readingGold else textColor, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().clickable { selectedBook = b; selectedChapter = 1; drawerOpen = false }.padding(vertical = 8.dp))
                        }
                        item { Text("Chapter", color = readingGold, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
                        items((1..50).toList()) { c ->
                            Text("Chapter $c", color = if (c == selectedChapter) readingGold else textColor, modifier = Modifier.fillMaxWidth().clickable { selectedChapter = c; drawerOpen = false }.padding(vertical = 8.dp))
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { drawerOpen = false }) { Text("Close", color = readingGold) } }
            )
        }

        // Clean Daily Verse
        if (dialog == "daily") {
            AlertDialog(
                onDismissRequest = { dialog = null },
                containerColor = bgColor,
                title = { Text("Daily Verse", color = readingGold, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                text = {
                    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        val parts = dailyVerseText.split("\n\n", limit = 2)
                        Text(parts.getOrNull(0) ?: "", color = readingGold, fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Text(parts.getOrNull(1) ?: dailyVerseText, color = textColor, fontSize = fontSize.sp, lineHeight = (fontSize + 8).sp, textAlign = TextAlign.Center)
                    }
                },
                confirmButton = { TextButton(onClick = { dialog = null }) { Text("Close", color = readingGold, fontWeight = FontWeight.Bold) } }
            )
        }
    }
}
