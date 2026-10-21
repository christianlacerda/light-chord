package com.christianlacerda.chords.transfer

import java.net.URI
import java.security.MessageDigest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Pure parsing/validation/reporting for the Chord Desk transfer; no I/O. */
object TransferProtocol {
    private val TOKEN = Regex("[0-9a-f]{32}")
    private val HEX64 = Regex("[0-9a-f]{64}")
    private val json = Json { ignoreUnknownKeys = true }

    /** Parses the scanned QR text; null unless it is `http://host[:port]/lightchord/v1?t=<32 hex>`. */
    fun parseQr(text: String): TransferTarget? {
        val uri = try {
            URI(text.trim())
        } catch (e: Exception) {
            return null
        }
        if (uri.scheme != "http") return null
        if (uri.userInfo != null || uri.fragment != null) return null
        val host = uri.host?.takeIf { it.isNotEmpty() } ?: return null
        if (uri.path != TransferLimits.PROTOCOL_PATH) return null
        val token = queryParam(uri.rawQuery, "t") ?: return null
        if (!TOKEN.matches(token)) return null
        val authority = if (uri.port == -1) host else "$host:${uri.port}"
        return TransferTarget("http://$authority${TransferLimits.PROTOCOL_PATH}", token)
    }

    private fun queryParam(rawQuery: String?, key: String): String? {
        if (rawQuery == null) return null
        for (part in rawQuery.split('&')) {
            val i = part.indexOf('=')
            if (i > 0 && part.substring(0, i) == key) return part.substring(i + 1)
        }
        return null
    }

    /** Null when [name] is acceptable, else a short reason. */
    fun nameProblem(name: String): String? {
        if (name.isEmpty() || name == "." || name == "..") return "bad name"
        if (name.any { it == '/' || it == '\\' || it.code < 0x20 || it.code == 0x7f }) return "bad name"
        if (name.startsWith(".")) return "bad name"
        if (name.toByteArray(Charsets.UTF_8).size > 255) return "bad name"
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext !in TransferLimits.EXTENSIONS) return "unsupported type"
        return null
    }

    /**
     * Parses the manifest body. Throws [TransferException.Protocol] when the manifest as a whole is
     * unusable (bad JSON, wrong version, too many files); individually bad entries are [ValidatedManifest.rejected].
     */
    fun parseManifest(body: String): ValidatedManifest {
        val dto = try {
            json.decodeFromString(ManifestDto.serializer(), body)
        } catch (e: SerializationException) {
            throw TransferException.Protocol("bad manifest")
        } catch (e: IllegalArgumentException) {
            throw TransferException.Protocol("bad manifest")
        }
        if (dto.version != 1) throw TransferException.Protocol("unsupported version")
        if (dto.files.size > TransferLimits.MAX_FILES) throw TransferException.Protocol("too many files")
        val ok = ArrayList<ManifestFile>()
        val rejected = ArrayList<Failure>()
        val seenNames = HashSet<String>()
        val seenIds = HashSet<Int>()
        for (f in dto.files) {
            val problem = nameProblem(f.name)
                ?: when {
                    f.size < 0 || f.size > TransferLimits.MAX_FILE_BYTES -> "too large"
                    !HEX64.matches(f.sha256.lowercase()) -> "bad hash"
                    !seenIds.add(f.id) -> "duplicate id"
                    !seenNames.add(f.name.lowercase()) -> "duplicate name"
                    else -> null
                }
            if (problem != null) {
                rejected.add(Failure(f.name, problem))
            } else {
                ok.add(ManifestFile(f.id, f.name, f.size.toInt(), f.sha256.lowercase()))
            }
        }
        return ValidatedManifest(ok, rejected)
    }

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    /** Same hash as the phone's copy → SAME (skip); different → CONFLICT; no copy → NEW. */
    fun classify(files: List<ManifestFile>, existingHash: (String) -> String?): List<ClassifiedFile> =
        files.map { f ->
            val have = existingHash(f.name)
            ClassifiedFile(
                f,
                when {
                    have == null -> FileClass.NEW
                    have.equals(f.sha256, ignoreCase = true) -> FileClass.SAME
                    else -> FileClass.CONFLICT
                },
            )
        }

    fun doneJson(summary: TransferSummary): String {
        fun names(list: List<String>) = JsonArray(list.map { JsonPrimitive(it) })
        val obj: JsonObject = buildJsonObject {
            put("received", names(summary.received))
            put("replaced", names(summary.replaced))
            put("skipped", names(summary.skipped))
            put(
                "failed",
                JsonArray(
                    summary.failed.map {
                        buildJsonObject {
                            put("name", it.name)
                            put("reason", it.reason)
                        }
                    },
                ),
            )
        }
        return obj.toString()
    }
}
