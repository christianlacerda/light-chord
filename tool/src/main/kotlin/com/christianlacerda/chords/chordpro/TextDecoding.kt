package com.christianlacerda.chords.chordpro

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

object TextDecoding {
    /** Strict UTF-8; on any malformed input falls back to windows-1252 (Latin-1 Brazilian files). */
    fun decode(bytes: ByteArray): String {
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: CharacterCodingException) {
            String(bytes, charset("windows-1252"))
        }
    }
}
