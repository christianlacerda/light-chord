package com.christianlacerda.chords

import com.christianlacerda.chords.library.LibraryIndex
import com.christianlacerda.chords.library.SongEntry
import com.christianlacerda.chords.library.songsForArtist
import java.text.Collator
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArtistSongsTest {
    private val collator = Collator.getInstance(Locale.ENGLISH)

    private fun song(file: String, artist: String?) =
        SongEntry(file = file, modified = 1, firstSeen = 1, title = file, artist = artist)

    @Test
    fun matchesArtistCaseInsensitively() {
        val index = LibraryIndex(listOf(song("a.cho", "Beatles"), song("b.cho", "beatles"), song("c.cho", "Queen")))
        assertEquals(listOf("a.cho", "b.cho"), songsForArtist(index, "BEATLES", collator).map { it.file })
    }

    @Test
    fun unknownArtistAndMissingArtist() {
        val index = LibraryIndex(listOf(song("a.cho", null), song("b.cho", "Queen")))
        assertEquals(listOf("a.cho"), songsForArtist(index, null, collator).map { it.file })
        assertTrue(songsForArtist(index, "Nobody", collator).isEmpty())
    }

    @Test
    fun emptyAfterLastSongDeleted() {
        val before = LibraryIndex(listOf(song("a.cho", "Queen")))
        assertEquals(1, songsForArtist(before, "Queen", collator).size)
        assertTrue(songsForArtist(LibraryIndex(), "Queen", collator).isEmpty())
    }
}
