package com.christianlacerda.chords

import com.christianlacerda.chords.library.FileStat
import com.christianlacerda.chords.library.LibraryIndex
import com.christianlacerda.chords.library.LibraryIndexer
import com.christianlacerda.chords.library.SongEntryHeader
import com.christianlacerda.chords.library.SongError
import com.christianlacerda.chords.library.countAddedSince
import java.text.Collator
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryIndexerTest {
    private val calls = ArrayList<String>()
    private fun header(artist: String? = "Traditional", error: String? = null) = { name: String ->
        calls.add(name)
        SongEntryHeader("T-$name", artist, "G", null, error)
    }

    @Test fun newFileParsed() {
        val idx = LibraryIndexer.reindex(LibraryIndex(), listOf(FileStat("a.cho", 10)), emptySet(), 100, header())
        assertEquals(1, idx.entries.size)
        with(idx.entries[0]) { assertEquals("a.cho", file); assertEquals(10, modified); assertEquals(100, firstSeen); assertEquals("T-a.cho", title); assertFalse(bundled) }
    }

    @Test fun unchangedReusedWithoutParsing() {
        val first = LibraryIndexer.reindex(LibraryIndex(), listOf(FileStat("a.cho", 10)), emptySet(), 100, header())
        calls.clear()
        val second = LibraryIndexer.reindex(first, listOf(FileStat("a.cho", 10)), emptySet(), 200, header())
        assertTrue(calls.isEmpty()); assertEquals(first, second)
    }

    @Test fun changedReparsedKeepsFirstSeen() {
        val first = LibraryIndexer.reindex(LibraryIndex(), listOf(FileStat("a.cho", 10)), emptySet(), 100, header())
        calls.clear()
        val second = LibraryIndexer.reindex(first, listOf(FileStat("a.cho", 11)), emptySet(), 200, header("Other"))
        assertEquals(listOf("a.cho"), calls)
        assertEquals(100, second.entries[0].firstSeen); assertEquals(11, second.entries[0].modified)
        assertEquals("Other", second.entries[0].artist)
    }

    @Test fun removedDropped() {
        val first = LibraryIndexer.reindex(LibraryIndex(), listOf(FileStat("a.cho", 1), FileStat("b.cho", 1)), emptySet(), 1, header())
        val second = LibraryIndexer.reindex(first, listOf(FileStat("b.cho", 1)), emptySet(), 2, header())
        assertEquals(listOf("b.cho"), second.entries.map { it.file })
    }

    @Test fun errorsRecordedAndBundledFlag() {
        val idx = LibraryIndexer.reindex(LibraryIndex(), listOf(FileStat("a.txt", 1)), setOf("a.txt"), 1, header(null, SongError.NOT_CHORDPRO))
        assertEquals("not_chordpro", idx.entries[0].error); assertTrue(idx.entries[0].bundled)
    }

    @Test fun songFileFilter() {
        assertTrue(LibraryIndexer.isSongFile("a.cho")); assertTrue(LibraryIndexer.isSongFile("A.TXT"))
        assertTrue(LibraryIndexer.isSongFile("a.chopro")); assertTrue(LibraryIndexer.isSongFile("a.chordpro"))
        assertFalse(LibraryIndexer.isSongFile("a.pdf")); assertFalse(LibraryIndexer.isSongFile("._a.cho"))
        assertFalse(LibraryIndexer.isSongFile(".hidden.cho")); assertFalse(LibraryIndexer.isSongFile("noext"))
    }

    @Test fun groupingCaseInsensitiveUnknownLast() {
        val artists = listOf("beatles", "Zé", null, "Beatles", "  ", "Ana")
        var i = 0
        val idx = LibraryIndexer.reindex(
            LibraryIndex(), artists.indices.map { FileStat("f$it.cho", 1) }, emptySet(), 1,
        ) { SongEntryHeader("t", artists[i++], null, null, null) }
        val coll = Collator.getInstance(Locale.ENGLISH).apply { strength = Collator.PRIMARY }
        val groups = LibraryIndexer.groupByArtist(idx, coll)
        assertEquals(listOf("Ana", "beatles", "Zé", null), groups.map { it.artist })
        assertEquals(2, groups[1].count)
        assertNull(groups.last().artist); assertEquals(2, groups.last().count)
    }

    @Test fun addedTodayCountsOnlyNonBundledSince() {
        val idx = LibraryIndexer.reindex(
            LibraryIndex(), listOf(FileStat("a.cho", 1), FileStat("b.cho", 1)), setOf("a.cho"), 1_000, header(),
        )
        assertEquals(1, idx.countAddedSince(500))
        assertEquals(0, idx.countAddedSince(2_000))
    }
}
