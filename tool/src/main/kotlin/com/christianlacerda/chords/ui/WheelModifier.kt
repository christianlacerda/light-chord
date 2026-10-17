package com.christianlacerda.chords.ui

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent

/**
 * Rotary + pointer-scroll input for the wheel (key events go through the ViewModel's onKeyDown instead).
 * Put it on the screen root. A mouse wheel on the emulator gives one step per notch.
 * Direction: up / away from the user = positive = faster (flip with [WheelInput.INVERT]).
 */
@Composable
fun Modifier.wheelInput(wheel: WheelInput): Modifier {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try {
            requester.requestFocus()
        } catch (e: IllegalStateException) {
            // not attached yet; rotary simply won't be delivered until the next focus change
        }
    }
    return this
        .focusRequester(requester)
        .focusable()
        .onRotaryScrollEvent { wheel.onRotary(it.verticalScrollPixels) }
        .pointerInput(wheel) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Scroll) {
                        var dy = 0f
                        event.changes.forEach { dy += it.scrollDelta.y }
                        // scroll up reports a negative y; one notch = one step
                        wheel.onRotary(-dy * WheelInput.ROTARY_STEP_PX)
                        event.changes.forEach { it.consume() }
                    }
                }
            }
        }
}
