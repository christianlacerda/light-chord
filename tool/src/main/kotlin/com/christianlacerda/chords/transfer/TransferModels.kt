package com.christianlacerda.chords.transfer

import kotlinx.serialization.Serializable

/** Limits and constants from docs/chord-desk-transfer.md. */
object TransferLimits {
    const val PROTOCOL_PATH = "/lightchord/v1"
    const val MAX_FILES = 200
    const val MAX_FILE_BYTES = 256 * 1024
    const val MAX_MANIFEST_BYTES = 256 * 1024
    val EXTENSIONS = setOf("cho", "chopro", "crd", "txt")
}

/** Where to connect, parsed from the QR payload. [base] is `http://host:port/lightchord/v1`. */
data class TransferTarget(val base: String, val token: String) {
    fun url(path: String): String = "$base$path?t=$token"
}

@Serializable
data class ManifestFileDto(
    val id: Int,
    val name: String,
    val size: Long,
    val sha256: String,
)

@Serializable
data class ManifestDto(
    val version: Int,
    val sender: String? = null,
    val files: List<ManifestFileDto>,
)

/** A manifest entry that passed validation. */
data class ManifestFile(val id: Int, val name: String, val size: Int, val sha256: String)

data class Failure(val name: String, val reason: String)

/** Outcome of validating a manifest: usable entries plus entries rejected individually. */
data class ValidatedManifest(val files: List<ManifestFile>, val rejected: List<Failure>)

enum class FileClass { NEW, SAME, CONFLICT }

data class ClassifiedFile(val file: ManifestFile, val kind: FileClass)

data class TransferSummary(
    val received: List<String>,
    val replaced: List<String>,
    val skipped: List<String>,
    val failed: List<Failure>,
) {
    /** Songs now on the phone from this transfer. */
    val songsReceived: Int get() = received.size + replaced.size
}

/** Why a transfer could not even start or had to stop. */
sealed class TransferException(message: String) : Exception(message) {
    /** Network failure: wrong Wi-Fi, server closed, timeout. */
    class Unreachable(message: String) : TransferException(message)

    /** HTTP 401: the one-time token is wrong or expired. */
    class Expired : TransferException("expired")

    /** Anything else the protocol does not allow. */
    class Protocol(message: String) : TransferException(message)
}
