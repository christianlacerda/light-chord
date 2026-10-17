package com.christianlacerda.chords

import java.util.Locale

/**
 * Strings needed outside of composition (the Tool Manager manifest has no Context), chosen by the
 * default locale. Everything shown in screens uses string resources instead.
 */
object ToolText {
    private fun pt(): Boolean = Locale.getDefault().language == "pt"

    fun toolName(): String = if (pt()) "Cifras" else "Chords"

    fun songsLabel(): String = if (pt()) "Músicas" else "Songs"

    fun songsHeader(): String =
        if (pt()) {
            "Arquivos ChordPro (.cho, .chopro, .txt). As músicas são listadas por artista."
        } else {
            "ChordPro files (.cho, .chopro, .txt). Songs are listed by artist."
        }
}
