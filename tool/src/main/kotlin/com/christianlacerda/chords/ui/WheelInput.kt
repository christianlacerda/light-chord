package com.christianlacerda.chords.ui

/**
 * Key codes the LP3 wheel can send. The SDK's LightDeviceKeys mapping (light-keyboard) says the wheel sends
 * 317 and 318 for its two directions and 319 for a press. Which of 317/318 is "up" is UNVERIFIED: 317 is assumed
 * to be up (faster); flip [INVERT] if the device disagrees. Plain ints so this file is JVM-testable.
 */
object WheelKeys {
    const val WHEEL_A = 317
    const val WHEEL_B = 318
    const val WHEEL_PRESS = 319

    private const val KEYCODE_DPAD_CENTER = 23
    private const val KEYCODE_ENTER = 66
    private const val KEYCODE_DPAD_UP = 19
    private const val KEYCODE_DPAD_DOWN = 20
    private const val KEYCODE_PAGE_UP = 92
    private const val KEYCODE_PAGE_DOWN = 93
    private const val KEYCODE_NAVIGATE_PREVIOUS = 260
    private const val KEYCODE_NAVIGATE_NEXT = 261
    private const val KEYCODE_SYSTEM_NAVIGATION_UP = 280
    private const val KEYCODE_SYSTEM_NAVIGATION_DOWN = 281

    val UP: Set<Int> = setOf(
        WHEEL_A, KEYCODE_DPAD_UP, KEYCODE_SYSTEM_NAVIGATION_UP, KEYCODE_NAVIGATE_PREVIOUS, KEYCODE_PAGE_UP,
    )
    val DOWN: Set<Int> = setOf(
        WHEEL_B, KEYCODE_DPAD_DOWN, KEYCODE_SYSTEM_NAVIGATION_DOWN, KEYCODE_NAVIGATE_NEXT, KEYCODE_PAGE_DOWN,
    )
    /** 317-319 don't pass through the stock emulator, so DPAD centre / Enter also act as the wheel press. */
    val PRESS: Set<Int> = setOf(WHEEL_PRESS, KEYCODE_DPAD_CENTER, KEYCODE_ENTER)
}

/**
 * Turns wheel input into steps: +1 = faster/next, -1 = slower/previous. [onPress] fires on a wheel press
 * (toggle play/pause in the Song view).
 */
class WheelInput(
    private val onStep: (Int) -> Unit,
    private val onPress: (() -> Unit)? = null,
) {
    companion object {
        const val ACTION_DOWN = 0
        const val ACTION_UP = 1
        const val ROTARY_STEP_PX = 48f
        /** Flip if the physical direction is backwards. */
        const val INVERT = false
    }

    private var rotaryAccum = 0f

    private fun dir(step: Int) = if (INVERT) -step else step

    /** Returns true if the key was consumed. [repeat] is the extra repeat count (onKeyMultiple). */
    fun onKey(keyCode: Int, action: Int, repeat: Int): Boolean {
        val up = keyCode in WheelKeys.UP
        val down = keyCode in WheelKeys.DOWN
        val press = keyCode in WheelKeys.PRESS
        if (!up && !down && !press) return false
        if (action != ACTION_DOWN) return true
        if (press) {
            if (repeat == 0) onPress?.invoke()
            return true
        }
        val n = 1 + repeat.coerceAtLeast(0)
        repeat(n) { onStep(dir(if (up) 1 else -1)) }
        return true
    }

    /** Positive = away from the user = faster. Fires one step per [ROTARY_STEP_PX]. */
    fun onRotary(deltaPx: Float): Boolean {
        rotaryAccum += deltaPx
        while (rotaryAccum >= ROTARY_STEP_PX) { rotaryAccum -= ROTARY_STEP_PX; onStep(dir(1)) }
        while (rotaryAccum <= -ROTARY_STEP_PX) { rotaryAccum += ROTARY_STEP_PX; onStep(dir(-1)) }
        return true
    }
}
