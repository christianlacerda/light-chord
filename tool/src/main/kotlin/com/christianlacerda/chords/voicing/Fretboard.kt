package com.christianlacerda.chords.voicing

import com.christianlacerda.chords.model.Voicing

/** Open-string pitch classes, low E to high e. */
val STANDARD_TUNING = intArrayOf(4, 9, 2, 7, 11, 4)

fun soundedPitchClasses(v: Voicing): Set<Int> =
    v.frets.mapIndexedNotNull { i, f -> f?.let { (STANDARD_TUNING[i] + it) % 12 } }.toSet()

/** Index of the lowest sounded string, or null if all muted. */
fun lowestSoundedString(v: Voicing): Int? = v.frets.indexOfFirst { it != null }.takeIf { it >= 0 }

fun computeBaseFret(frets: List<Int?>): Int {
    val fretted = frets.filterNotNull().filter { it > 0 }
    if (fretted.isEmpty()) return 1
    return if (fretted.max() <= 5) 1 else fretted.min()
}
