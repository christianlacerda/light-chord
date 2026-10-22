package com.christianlacerda.chords.layout

import kotlin.math.pow

object ScrollSpeed {
    const val MIN = 1
    const val MAX = 10
    const val DEFAULT = 4

    fun clamp(level: Int): Int = level.coerceIn(MIN, MAX)

    /** Geometric curve: 12 s per music line at level 1 down to 1.5 s at level 10 (each step about 26% faster). */
    fun secondsPerLine(level: Int): Double =
        12.0 * (1.0 / 8.0).pow((clamp(level) - 1) / 9.0)

    fun pxPerSecond(level: Int, linePitchPx: Float): Float =
        (linePitchPx / secondsPerLine(level)).toFloat()
}
