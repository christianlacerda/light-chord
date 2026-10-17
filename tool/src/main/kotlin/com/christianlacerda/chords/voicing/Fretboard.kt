package com.christianlacerda.chords.voicing

import com.christianlacerda.chords.model.Voicing

/** Open-string pitch classes, low E to high e. */
val STANDARD_TUNING = intArrayOf(4, 9, 2, 7, 11, 4)

fun soundedPitchClasses(v: Voicing): Set<Int> =
    v.frets.mapIndexedNotNull { i, f -> f?.let { (STANDARD_TUNING[i] + it) % 12 } }.toSet()

/** Index of the lowest sounded string, or null if all muted. */
fun lowestSoundedString(v: Voicing): Int? = v.frets.indexOfFirst { it != null }.takeIf { it >= 0 }

/**
 * First fret shown in the diagram. Wide shapes start at their lowest fretted fret; narrow shapes start at
 * fret 1 (showing the nut) unless they have no open strings and sit higher up the neck, in which case the
 * lowest fretted fret is the base, so the diagram gets its "3fr" label instead of a misleading nut.
 */
fun computeBaseFret(frets: List<Int?>): Int {
    val fretted = frets.filterNotNull().filter { it > 0 }
    if (fretted.isEmpty()) return 1
    val lowest = fretted.min()
    val hasOpen = frets.any { it == 0 }
    return when {
        fretted.max() > 5 -> lowest
        !hasOpen && lowest > 1 -> lowest
        else -> 1
    }
}
