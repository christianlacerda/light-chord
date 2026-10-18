package com.christianlacerda.chords

import com.christianlacerda.chords.library.SongSettings
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SongSettingsJsonTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun oldSavedDataDefaultsToShowingTabs() {
        val s = json.decodeFromString(SongSettings.serializer(), """{"speed":6,"transpose":0,"lastProgress":0.4}""")
        assertEquals(6, s.speed)
        assertTrue(s.showTabs)
    }

    @Test fun showTabsRoundTrips() {
        val text = json.encodeToString(SongSettings.serializer(), SongSettings(speed = 3, showTabs = false))
        assertFalse(json.decodeFromString(SongSettings.serializer(), text).showTabs)
    }
}
