@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val gold = Color(0xFFC9A227)
    val background = if (state.darkMode) Color(0xFF1E1E22) else Color(0xFFFDF8EF)
    val panel = if (state.darkMode) Color(0xFF2A2A30) else Color(0xFFF0EAD9)
    val textColor = if (state.darkMode) Color(0xFFF5F0E6) else Color(0xFF1E1E22)
    val secondaryText = if (state.darkMode) Color(0xFFB7B0A6) else Color(0xFF524B40)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        HomeTopBar(
            selectedTab = state.selectedTab,
            textColor = textColor,
            gold = gold,
            onMenuClick = {
                scope.launch {
                    if (drawerState.isClosed) drawerState.open()
                    else drawerState.close()
                }
            },
            onTabClick = viewModel::selectTab,
            onSettingsClick = {
                // Open settings
            }
        )

        ModalNavigationDrawer(
            modifier = Modifier.weight(1f),
            drawerState = drawerState,
            drawerContent = {
                BookDrawer(
                    otBookEntities = state.books.filter { it.testament.equals("Old", true) },
                    ntBookEntities = state.books.filter { it.testament.equals("New", true) },
                    chapterCounts = state.chapterCounts,
                    selectedBook = state.selectedBook,
                    selectedChapter = state.selectedChapter,
                    expandedBook = state.selectedBook,
                    background = background,
                    textColor = textColor,
                    panel = panel,
                    gold = gold,
                    secondaryText = secondaryText,
                    onOpenBook = viewModel::openBook,
                    onSelectBook = { bookName ->
                        viewModel.openBook(bookName, 1)
                    },
                    onToggleBook = { }
                )
            }
        ) {
            when (state.selectedTab) {
                "Bible" -> BibleContent(
                    selectedBook = state.selectedBook,
                    selectedChapter = state.selectedChapter,
                    summary = state.summary,
                    versesList = state.verses,
                    currentHighlightMap = state.highlights,
                    fontSize = state.fontSize,
                    gold = gold,
                    panel = panel,
                    textColor = textColor,
                    listState = listState,
                    onVerseClick = { verse ->
                        viewModel.saveReadingProgress(
                            verse = verse,
                            scrollIndex = listState.firstVisibleItemIndex,
                            scrollOffset = listState.firstVisibleItemScrollOffset
                        )
                    },
                    onVerseLongClick = { verse ->
                        viewModel.selectVerse(verse)
                        // Show note/highlight sheet
                    }
                )

                "Open EW" -> OpenEWContent(
                    commentaryHtml = state.commentaryHtml,
                    fontSize = state.fontSize,
                    textColor = textColor,
                    secondaryText = secondaryText,
                    hasError = state.commentaryError,
                    bookName = state.selectedBook,
                    chapter = state.selectedChapter,
                    onVerseClick = { book, chapter, verse ->
                        viewModel.openBook(book, chapter)
                        viewModel.selectVerse(verse)
                        viewModel.selectTab("Bible")
                    }
                )

                "Search" -> SearchContent(
                    searchQuery = "", // Bind to a local TextField state
                    searchFilter = "All",
                    filteredSearchResults = emptyList(),
                    verseResults = state.searchResults,
                    textColor = textColor,
                    gold = gold,
                    onQueryChange = viewModel::setSearchQuery,
                    onFilterChange = viewModel::setSearchFilter,
                    onOpenBook = { bookName ->
                        viewModel.openBook(bookName, 1)
                        viewModel.selectTab("Bible")
                    },
                    onOpenVerse = { bookName, chapter, verse ->
                        viewModel.openBook(bookName, chapter)
                        viewModel.selectVerse(verse)
                        viewModel.selectTab("Bible")
                    }
                )
            }
        }
    }
}