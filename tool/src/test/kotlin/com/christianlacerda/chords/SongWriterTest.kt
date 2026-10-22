package com.christianlacerda.chords

import com.christianlacerda.chords.library.PendingSongWrite
import com.christianlacerda.chords.library.SongWriteStore
import com.christianlacerda.chords.library.SongWriter
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SongWriterTest {
    private class Store : SongWriteStore {
        var journal: PendingSongWrite? = null
        var file = "old song"
        var failStage = false
        var failFile = false
        var failClear = false
        override suspend fun pending() = journal
        override suspend fun stage(write: PendingSongWrite) {
            if (failStage) error("storage failure")
            journal = write
        }
        override suspend fun writeFile(write: PendingSongWrite) {
            file = write.text.take(2)
            if (failFile) error("interrupted write")
            file = write.text
        }
        override suspend fun clear() {
            if (failClear) error("interrupted cleanup")
            journal = null
        }
    }

    @Test fun cannotTruncateUntilRecoveryDataIsDurable() = runBlocking {
        val store = Store().apply { failStage = true }
        assertFailsWith<IllegalStateException> { SongWriter(store).write("t.cho", "new song") }
        assertEquals("old song", store.file)
    }

    @Test fun interruptedOverwriteRecoversOnNextInstance() = runBlocking {
        val store = Store().apply { failFile = true }
        assertFailsWith<IllegalStateException> { SongWriter(store).write("t.cho", "new song") }
        assertEquals("new song", store.journal!!.text)
        store.failFile = false
        SongWriter(store).recover()
        assertEquals("new song", store.file)
        assertNull(store.journal)
    }

    @Test fun replayAfterInterruptedCleanupIsIdempotent() = runBlocking {
        val store = Store().apply { failClear = true }
        assertFailsWith<IllegalStateException> { SongWriter(store).write("t.cho", "new song") }
        store.failClear = false
        SongWriter(store).recover()
        assertEquals("new song", store.file)
        assertNull(store.journal)
    }
}
