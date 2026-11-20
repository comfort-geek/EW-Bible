package com.ewbible.app.data.database

data class BookInfo(
    val id: Int,
    val name: String,
    val key: String,
    val testament: String,
    val chapters: Int
)

object BookCatalog {

    val all: List<BookInfo> = listOf(
        BookInfo(1, "Genesis", "genesis", "OT", 50),
        BookInfo(2, "Exodus", "exodus", "OT", 40),
        BookInfo(3, "Leviticus", "leviticus", "OT", 27),
        BookInfo(4, "Numbers", "numbers", "OT", 36),
        BookInfo(5, "Deuteronomy", "deuteronomy", "OT", 34),
        BookInfo(6, "Joshua", "joshua", "OT", 24),
        BookInfo(7, "Judges", "judges", "OT", 21),
        BookInfo(8, "Ruth", "ruth", "OT", 4),
        BookInfo(9, "1 Samuel", "1-samuel", "OT", 31),
        BookInfo(10, "2 Samuel", "2-samuel", "OT", 24),
        BookInfo(11, "1 Kings", "1-kings", "OT", 22),
        BookInfo(12, "2 Kings", "2-kings", "OT", 25),
        BookInfo(13, "1 Chronicles", "1-chronicles", "OT", 29),
        BookInfo(14, "2 Chronicles", "2-chronicles", "OT", 36),
        BookInfo(15, "Ezra", "ezra", "OT", 10),
        BookInfo(16, "Nehemiah", "nehemiah", "OT", 13),
        BookInfo(17, "Esther", "esther", "OT", 10),
        BookInfo(18, "Job", "job", "OT", 42),
        BookInfo(19, "Psalms", "psalms", "OT", 150),
        BookInfo(20, "Proverbs", "proverbs", "OT", 31),
        BookInfo(21, "Ecclesiastes", "ecclesiastes", "OT", 12),
        BookInfo(22, "Song of Solomon", "song-of-solomon", "OT", 8),
        BookInfo(23, "Isaiah", "isaiah", "OT", 66),
        BookInfo(24, "Jeremiah", "jeremiah", "OT", 52),
        BookInfo(25, "Lamentations", "lamentations", "OT", 5),
        BookInfo(26, "Ezekiel", "ezekiel", "OT", 48),
        BookInfo(27, "Daniel", "daniel", "OT", 12),
        BookInfo(28, "Hosea", "hosea", "OT", 14),
        BookInfo(29, "Joel", "joel", "OT", 3),
        BookInfo(30, "Amos", "amos", "OT", 9),
        BookInfo(31, "Obadiah", "obadiah", "OT", 1),
        BookInfo(32, "Jonah", "jonah", "OT", 4),
        BookInfo(33, "Micah", "micah", "OT", 7),
        BookInfo(34, "Nahum", "nahum", "OT", 3),
        BookInfo(35, "Habakkuk", "habakkuk", "OT", 3),
        BookInfo(36, "Zephaniah", "zephaniah", "OT", 3),
        BookInfo(37, "Haggai", "haggai", "OT", 2),
        BookInfo(38, "Zechariah", "zechariah", "OT", 14),
        BookInfo(39, "Malachi", "malachi", "OT", 4),
        BookInfo(40, "Matthew", "matthew", "NT", 28),
        BookInfo(41, "Mark", "mark", "NT", 16),
        BookInfo(42, "Luke", "luke", "NT", 24),
        BookInfo(43, "John", "john", "NT", 21),
        BookInfo(44, "Acts", "acts", "NT", 28),
        BookInfo(45, "Romans", "romans", "NT", 16),
        BookInfo(46, "1 Corinthians", "1-corinthians", "NT", 16),
        BookInfo(47, "2 Corinthians", "2-corinthians", "NT", 13),
        BookInfo(48, "Galatians", "galatians", "NT", 6),
        BookInfo(49, "Ephesians", "ephesians", "NT", 6),
        BookInfo(50, "Philippians", "philippians", "NT", 4),
        BookInfo(51, "Colossians", "colossians", "NT", 4),
        BookInfo(52, "1 Thessalonians", "1-thessalonians", "NT", 5),
        BookInfo(53, "2 Thessalonians", "2-thessalonians", "NT", 3),
        BookInfo(54, "1 Timothy", "1-timothy", "NT", 6),
        BookInfo(55, "2 Timothy", "2-timothy", "NT", 4),
        BookInfo(56, "Titus", "titus", "NT", 3),
        BookInfo(57, "Philemon", "philemon", "NT", 1),
        BookInfo(58, "Hebrews", "hebrews", "NT", 13),
        BookInfo(59, "James", "james", "NT", 5),
        BookInfo(60, "1 Peter", "1-peter", "NT", 5),
        BookInfo(61, "2 Peter", "2-peter", "NT", 3),
        BookInfo(62, "1 John", "1-john", "NT", 5),
        BookInfo(63, "2 John", "2-john", "NT", 1),
        BookInfo(64, "3 John", "3-john", "NT", 1),
        BookInfo(65, "Jude", "jude", "NT", 1),
        BookInfo(66, "Revelation", "revelation", "NT", 22)
    )

    private val byId = all.associateBy { it.id }

    private val aliases = mapOf(
        "psalm" to 19, "ps" to 19,
        "song" to 22, "song of songs" to 22, "canticles" to 22, "song-of-songs" to 22,
        "1 sam" to 9, "1-sam" to 9,
        "2 sam" to 10, "2-sam" to 10,
        "1 kings" to 11, "1 kgs" to 11, "1-kgs" to 11,
        "2 kings" to 12, "2 kgs" to 12, "2-kgs" to 12,
        "1 chron" to 13, "1-chron" to 13, "1 chr" to 13,
        "2 chron" to 14, "2-chron" to 14, "2 chr" to 14,
        "1 cor" to 46, "1-cor" to 46,
        "2 cor" to 47, "2-cor" to 47,
        "1 thess" to 52, "1-thess" to 52,
        "2 thess" to 53, "2-thess" to 53,
        "1 tim" to 54, "1-tim" to 54,
        "2 tim" to 55, "2-tim" to 55,
        "1 pet" to 60, "1-pet" to 60,
        "2 pet" to 61, "2-pet" to 61,
        "1 jn" to 62, "1-jn" to 62,
        "2 jn" to 63, "2-jn" to 63,
        "3 jn" to 64, "3-jn" to 64,
        "rev" to 66,
        "gen" to 1, "ex" to 2, "lev" to 3, "num" to 4, "deut" to 5,
        "josh" to 6, "judg" to 7, "neh" to 16, "esth" to 17,
        "eccl" to 21, "isa" to 23, "jer" to 24, "lam" to 25, "ezek" to 26, "dan" to 27,
        "hos" to 28, "obad" to 31, "mic" to 33, "nah" to 34, "hab" to 35,
        "zeph" to 36, "hag" to 37, "zech" to 38, "mal" to 39,
        "matt" to 40, "mk" to 41, "lk" to 42, "jn" to 43,
        "rom" to 45, "gal" to 48, "eph" to 49, "phil" to 50, "col" to 51,
        "philem" to 57, "heb" to 58, "jas" to 59
    )

    private val aliasDisplayNames = listOf(
        "Psalm", "Ps",
        "Song of Songs", "Song",
        "1 Sam", "2 Sam", "1 Kgs", "2 Kgs", "1 Chr", "2 Chr", "1 Chron", "2 Chron",
        "1 Cor", "2 Cor", "1 Thess", "2 Thess", "1 Tim", "2 Tim",
        "1 Pet", "2 Pet", "1 Jn", "2 Jn", "3 Jn", "Rev",
        "Gen", "Ex", "Lev", "Num", "Deut", "Josh", "Judg", "Neh", "Esth",
        "Eccl", "Isa", "Jer", "Lam", "Ezek", "Dan", "Hos", "Obad", "Mic", "Nah", "Hab",
        "Zeph", "Hag", "Zech", "Mal", "Matt", "Mk", "Lk", "Jn",
        "Rom", "Gal", "Eph", "Phil", "Col", "Philem", "Heb", "Jas"
    )

    private val cachedDisplayNames: List<String> = (all.map { it.name } + aliasDisplayNames).distinct()

    private val normalizedLookup: Map<String, Int> = buildMap {
        fun putNormalized(key: String, id: Int) {
            val n = normalize(key)
            put(n, id)
            put(n.replace('-', ' '), id)
            put(n.replace(' ', '-'), id)
        }
        for (info in all) {
            putNormalized(info.name, info.id)
            putNormalized(info.key, info.id)
        }
        for ((k, v) in aliases) putNormalized(k, v)
        // ✅ FIXED: no-space for BOTH full names AND aliases
        // "1Samuel" -> 9, "1Sam" -> 9, "1Cor" -> 46, "1Jn" -> 62, "2Thess" -> 53
        for (info in all) {
            if (info.name[0].isDigit()) {
                putNormalized(info.name.replace(" ", ""), info.id)
            }
        }
        for ((k, v) in aliases) {
            if (k[0].isDigit()) {
                putNormalized(k.replace(" ", ""), v)
                putNormalized(k.replace("-", ""), v)
            }
        }
        // ✅ FIXED: no-space for display aliases too - "1Chron" / "1Thess" from aliasDisplayNames
        for (name in aliasDisplayNames) {
            val idForAlias = aliases.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
                ?: all.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id
                ?: continue
            if (name[0].isDigit()) {
                putNormalized(name.replace(" ", ""), idForAlias)
            }
        }
    }

    private fun normalize(input: String): String {
        if (input.isEmpty()) return ""
        val sb = StringBuilder(input.length)
        var lastWasSpace = true
        for (c in input) {
            val ch = when (c) {
                '_', '-', ' ' -> ' '
                else -> c.lowercaseChar()
            }
            if (ch == ' ') {
                if (!lastWasSpace) sb.append(' ')
                lastWasSpace = true
            } else {
                sb.append(ch)
                lastWasSpace = false
            }
        }
        if (sb.isNotEmpty() && sb[sb.length - 1] == ' ') sb.setLength(sb.length - 1)
        return sb.toString()
    }

    fun name(id: Int): String = byId[id]?.name ?: "Genesis"
    fun key(id: Int): String = byId[id]?.key ?: "genesis"
    fun id(name: String): Int {
        if (name.isEmpty()) return 1
        return normalizedLookup[normalize(name)] ?: 1
    }
    fun testament(id: Int): String = byId[id]?.testament ?: "OT"
    fun chapters(id: Int): Int = byId[id]?.chapters ?: 1
    fun toEntity(info: BookInfo): BookEntity = BookEntity(
        id = info.id,
        name = info.name,
        key = info.key,
        testament = info.testament
    )
    fun canonicalName(raw: String): String = name(id(raw))
    fun allDisplayNames(): List<String> = cachedDisplayNames
}