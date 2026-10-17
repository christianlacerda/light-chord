package com.christianlacerda.chords.model

/** Barre across strings [fromString]..[toString] (0 = low E .. 5 = high e) at absolute [fret]. */
data class Barre(val fret: Int, val fromString: Int, val toString: Int, val finger: Int = 1)

/** [frets]/[fingers] have 6 entries, low E to high e. null fret = muted, 0 = open. */
data class Voicing(
    val frets: List<Int?>,
    val fingers: List<Int>,
    val baseFret: Int,
    val barre: Barre?,
)
