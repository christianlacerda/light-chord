package com.christianlacerda.chords

import com.christianlacerda.chords.library.SongSettings
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

    @Test fun legacyLevelOpensAtItsFormerTick() {
        val s = json.decodeFromString(SongSettings.serializer(), """{"speed":4}""")
        assertNull(s.speedTick)
        assertEquals(6, s.tick)
    }

    @Test fun halfStepRoundTripsWithDowngradeLevel() {
        val text = json.encodeToString(SongSettings.serializer(), SongSettings().withTick(7))
        val back = json.decodeFromString(SongSettings.serializer(), text)
        assertEquals(7, back.tick)
        assertEquals(4, back.speed) // what an older version reads
    }

    @Test fun tickWinsOverLegacyLevel() {
        val s = json.decodeFromString(SongSettings.serializer(), """{"speed":9,"speedTick":3}""")
        assertEquals(3, s.tick)
    }

    @Test fun outOfRangeTickIsClamped() {
        val s = json.decodeFromString(SongSettings.serializer(), """{"speedTick":40}""")
        assertEquals(18, s.tick)
    }
}
