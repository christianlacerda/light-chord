package com.christianlacerda.chords.library

import kotlinx.serialization.Serializable

/** Durable intent, committed before the SDK's text writer can truncate a song. */
@Serializable
data class PendingSongWrite(val name: String, val text: String)

interface SongWriteStore {
    suspend fun pending(): PendingSongWrite?
    suspend fun stage(write: PendingSongWrite)
    suspend fun writeFile(write: PendingSongWrite)
    suspend fun clear()
}

/** Caller serializes access. An interrupted write is replayed before indexing/reading songs. */
class SongWriter(private val store: SongWriteStore) {
    suspend fun recover() {
        val pending = store.pending() ?: return
        store.writeFile(pending)
        store.clear()
    }

    suspend fun write(name: String, text: String) {
        recover()
        store.stage(PendingSongWrite(name, text))
        recover()
    }
}
