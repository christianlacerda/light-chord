package com.christianlacerda.chords.layout

import kotlin.math.pow

/**
 * Scroll speed in half-level ticks: tick 0 = level 1 … tick 18 = level 10. Legacy integer level L is tick
 * 2 * (L - 1), so whole levels keep their former speeds.
 */
object ScrollSpeed {
    const val MIN_LEVEL = 1
    const val MAX_LEVEL = 10
    const val TICKS_PER_LEVEL = 2
    const val MIN = 0
    const val MAX = (MAX_LEVEL - MIN_LEVEL) * TICKS_PER_LEVEL
    const val DEFAULT = 6 // level 4

    fun clamp(tick: Int): Int = tick.coerceIn(MIN, MAX)

    fun tickOf(level: Int): Int = (level.coerceIn(MIN_LEVEL, MAX_LEVEL) - MIN_LEVEL) * TICKS_PER_LEVEL

    /** Nearest whole level for older app versions; half steps round down (slower). */
    fun legacyLevel(tick: Int): Int = MIN_LEVEL + clamp(tick) / TICKS_PER_LEVEL

    /** Geometric curve: 12 s per music line at level 1 down to 1.5 s at level 10 (each tick about 12% faster). */
    fun secondsPerLine(tick: Int): Double =
        12.0 * (1.0 / 8.0).pow(clamp(tick).toDouble() / MAX)

    fun pxPerSecond(tick: Int, linePitchPx: Float): Float =
        (linePitchPx / secondsPerLine(tick)).toFloat()

    /** A timed song's rate: "1x" for 100 %, "1.05x", "0.5x" (with [decimalSeparator]). */
    fun rateLabel(percent: Int, decimalSeparator: Char = '.'): String {
        val whole = percent / 100
        val frac = (percent % 100).toString().padStart(2, '0').trimEnd('0')
        return if (frac.isEmpty()) "${whole}x" else "$whole$decimalSeparator${frac}x"
    }

    /** "4" or "4.5" (with [decimalSeparator]). */
    fun label(tick: Int, decimalSeparator: Char = '.'): String {
        val t = clamp(tick)
        val whole = MIN_LEVEL + t / TICKS_PER_LEVEL
        return if (t % TICKS_PER_LEVEL == 0) whole.toString() else "$whole${decimalSeparator}5"
    }
}
