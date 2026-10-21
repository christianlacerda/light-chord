package com.christianlacerda.chords.transfer

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Steps of a transfer, reported to the UI. */
sealed interface TransferProgress {
    data object Connecting : TransferProgress
    data class Receiving(val done: Int, val total: Int) : TransferProgress
}

/** Hooks a transfer needs from the phone side. */
interface TransferHost {
    /** SHA-256 hex of the song already stored under [name], or null when there is none. */
    suspend fun existingHash(name: String): String?

    /** Stores [bytes] under [name] (overwriting). Throw to report the file as failed. */
    suspend fun write(name: String, bytes: ByteArray)

    /** Asked once, only when some files differ from the phone's copies. true = replace them. */
    suspend fun confirmReplace(conflicts: Int): Boolean

    fun onProgress(progress: TransferProgress)
}

class TransferClient(private val http: TransferHttp, private val target: TransferTarget) {

    suspend fun fetchManifest(): ValidatedManifest {
        val reply = http.get(target.url("/manifest"), TransferLimits.MAX_MANIFEST_BYTES)
        check(reply)
        return TransferProtocol.parseManifest(reply.body.toString(Charsets.UTF_8))
    }

    private fun check(reply: HttpReply) {
        when {
            reply.status == 401 -> throw TransferException.Expired()
            reply.status !in 200..299 -> throw TransferException.Protocol("HTTP ${reply.status}")
        }
    }

    /** Best effort: never throws except for cancellation. */
    suspend fun postDone(summary: TransferSummary) {
        try {
            http.postJson(target.url("/done"), TransferProtocol.doneJson(summary))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // the phone's result stands even if Chord Desk can't be told
        }
    }

    /**
     * Runs the whole transfer. Throws [TransferException] only when the manifest cannot be obtained;
     * once files are flowing, problems become [TransferSummary.failed] entries. A done report is always
     * attempted after the manifest was read, including when the transfer ends early.
     */
    suspend fun run(host: TransferHost): TransferSummary {
        host.onProgress(TransferProgress.Connecting)
        val manifest = fetchManifest()
        val received = ArrayList<String>()
        val replaced = ArrayList<String>()
        val skipped = ArrayList<String>()
        val failed = ArrayList<Failure>(manifest.rejected)
        fun summary() = TransferSummary(received, replaced, skipped, failed)
        try {
            val hashes = HashMap<String, String?>()
            for (f in manifest.files) hashes[f.name] = host.existingHash(f.name)
            val classified = TransferProtocol.classify(manifest.files) { hashes[it] }
            val conflicts = classified.count { it.kind == FileClass.CONFLICT }
            val replace = conflicts > 0 && host.confirmReplace(conflicts)
            val queue = ArrayList<ClassifiedFile>()
            for (c in classified) {
                when {
                    c.kind == FileClass.SAME -> skipped.add(c.file.name)
                    c.kind == FileClass.CONFLICT && !replace -> skipped.add(c.file.name)
                    else -> queue.add(c)
                }
            }
            host.onProgress(TransferProgress.Receiving(0, queue.size))
            var expired = false
            for ((i, c) in queue.withIndex()) {
                if (expired) {
                    failed.add(Failure(c.file.name, "expired"))
                    continue
                }
                host.onProgress(TransferProgress.Receiving(i + 1, queue.size))
                try {
                    val bytes = download(c.file)
                    host.write(c.file.name, bytes)
                    if (c.kind == FileClass.CONFLICT) replaced.add(c.file.name) else received.add(c.file.name)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: TransferException.Expired) {
                    expired = true
                    failed.add(Failure(c.file.name, "expired"))
                } catch (e: TransferException.Unreachable) {
                    failed.add(Failure(c.file.name, "connection lost"))
                } catch (e: TransferException) {
                    failed.add(Failure(c.file.name, e.message ?: "failed"))
                } catch (e: Exception) {
                    failed.add(Failure(c.file.name, "could not save"))
                }
            }
        } finally {
            // runs on cancellation too; postDone itself is best effort
            withContext(NonCancellable) { postDone(summary()) }
        }
        return summary()
    }

    private suspend fun download(file: ManifestFile): ByteArray {
        val reply = http.get(target.url("/file/${file.id}"), TransferLimits.MAX_FILE_BYTES)
        check(reply)
        val bytes = reply.body
        if (bytes.size > TransferLimits.MAX_FILE_BYTES) throw TransferException.Protocol("too large")
        if (!TransferProtocol.sha256Hex(bytes).equals(file.sha256, ignoreCase = true)) {
            throw TransferException.Protocol("hash mismatch")
        }
        return bytes
    }
}
