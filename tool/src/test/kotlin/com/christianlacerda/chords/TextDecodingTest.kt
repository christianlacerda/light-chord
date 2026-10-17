package com.christianlacerda.chords

import com.christianlacerda.chords.chordpro.TextDecoding
import kotlin.test.Test
import kotlin.test.assertEquals

class TextDecodingTest {
    @Test fun utf8() {
        assertEquals("Refrão", TextDecoding.decode("Refrão".toByteArray(Charsets.UTF_8)))
    }

    @Test fun latin1() {
        assertEquals("Refrão", TextDecoding.decode("Refrão".toByteArray(Charsets.ISO_8859_1)))
    }

    @Test fun windows1252Punctuation() {
        val bytes = byteArrayOf(0x93.toByte(), 'a'.code.toByte(), 0x94.toByte())
        assertEquals("“a”", TextDecoding.decode(bytes))
    }
}
