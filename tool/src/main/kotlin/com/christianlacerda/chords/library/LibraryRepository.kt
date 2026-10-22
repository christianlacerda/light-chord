package com.christianlacerda.chords.library

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.christianlacerda.chords.chordpro.ChordProParser
import com.christianlacerda.chords.chordpro.ParseIssue
import com.christianlacerda.chords.chordpro.ParseResult
import com.christianlacerda.chords.chordpro.TextDecoding
import com.christianlacerda.chords.model.ChordNotation
import com.christianlacerda.chords.transfer.TransferProtocol
import com.christianlacerda.chords.voicing.VoicingLibrary
import com.thelightphone.sdk.LightFileShare
import java.io.InputStreamReader
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val INDEX_KEY = stringPreferencesKey("library_index_v1")
private val SETTINGS_KEY = stringPreferencesKey("song_settings_v1")
private val SEEDED_KEY = stringSetPreferencesKey("seeded_files")
private val NOTATION_KEY = stringPreferencesKey("chord_notation")
private val PENDING_WRITE_KEY = stringPreferencesKey("pending_song_write_v1")

private const val MAX_CHARS = 512 * 1024
private const val SAMPLES_DIR = "samples"

/** Shared by every repository instance so concurrent screens never index at the same time. */
private val refreshMutex = Mutex()
private val settingsSerializer = MapSerializer(String.serializer(), SongSettings.serializer())
private val json = Json { ignoreUnknownKeys = true }

/**
 * File + DataStore access for the song library. Cheap to construct (one per ViewModel); it holds
 * the file share / DataStore handed to it, never a Context.
 */
class LibraryRepository(
    private val fileShare: LightFileShare,
    private val dataStore: DataStore<Preferences>,
    private val readAsset: (String) -> ByteArray,
) {
    private val writer = SongWriter(object : SongWriteStore {
        override suspend fun pending(): PendingSongWrite? = dataStore.data.first()[PENDING_WRITE_KEY]?.let {
            json.decodeFromString(PendingSongWrite.serializer(), it)
        }

        override suspend fun stage(write: PendingSongWrite) {
            dataStore.edit { it[PENDING_WRITE_KEY] = json.encodeToString(PendingSongWrite.serializer(), write) }
        }

        override suspend fun writeFile(write: PendingSongWrite) {
            require(TransferProtocol.nameProblem(write.name) == null) { "Invalid song filename" }
            fileShare.write("$SONGS_DIR/${write.name}") { it.write(write.text) }
        }

        override suspend fun clear() { dataStore.edit { it.remove(PENDING_WRITE_KEY) } }
    })

    /** [text] is null when the file exceeds the size limit. */
    private class Loaded(val text: String?)

    suspend fun refresh(): LibraryIndex = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            writer.recover()
            seedLocked()
            val prefs = dataStore.data.first()
            val old = decodeIndex(prefs[INDEX_KEY])
            val seeded = prefs[SEEDED_KEY].orEmpty()
            val files = fileShare.list(SONGS_DIR)
                .filter { it.isFile && LibraryIndexer.isSongFile(it.name) }
                .map { FileStat(it.name, it.lastModified.toEpochMilliseconds()) }
            val index = LibraryIndexer.reindex(old, files, seeded, System.currentTimeMillis(), ::parseHeader)
            val names = files.map { it.name }.toSet()
            dataStore.edit { p ->
                p[INDEX_KEY] = json.encodeToString(LibraryIndex.serializer(), index)
                val settings = decodeSettings(p[SETTINGS_KEY])
                val pruned = settings.filterKeys { it in names }
                if (pruned.size != settings.size) {
                    p[SETTINGS_KEY] = json.encodeToString(settingsSerializer, pruned)
                }
            }
            LibraryEvents.index.value = index
            index
        }
    }

    /** [refresh] that can't take the app down: an I/O or DataStore failure just leaves the old index. */
    suspend fun refreshSafely(): LibraryIndex? = try {
        refresh()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    suspend fun seedSamplesIfNeeded() = withContext(Dispatchers.IO) {
        refreshMutex.withLock { seedLocked() }
    }

    /** Copies bundled samples listed in assets/samples/index.txt that were never seeded before. */
    private suspend fun seedLocked() {
        val names = try {
            TextDecoding.decode(readAsset("$SAMPLES_DIR/index.txt"))
                .lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        } catch (e: Exception) {
            return
        }
        val seeded = dataStore.data.first()[SEEDED_KEY].orEmpty()
        val pending = names.filter { it !in seeded }
        if (pending.isEmpty()) return
        val done = HashSet<String>()
        for (name in pending) {
            try {
                if (!fileShare.exists("$SONGS_DIR/$name")) {
                    val text = TextDecoding.decode(readAsset("$SAMPLES_DIR/$name"))
                    fileShare.write("$SONGS_DIR/$name") { it.write(text) }
                }
                done.add(name)
            } catch (e: Exception) {
                // leave it unseeded; tried again on the next refresh
            }
        }
        if (done.isNotEmpty()) {
            dataStore.edit { it[SEEDED_KEY] = seeded + done }
        }
    }

    private fun readLoaded(file: String): Loaded? =
        fileShare.read("$SONGS_DIR/$file", Charsets.ISO_8859_1) { r -> Loaded(readLimited(r)) }

    private fun readLimited(r: InputStreamReader): String? {
        val sb = StringBuilder()
        val buf = CharArray(8192)
        while (true) {
            val n = r.read(buf)
            if (n < 0) break
            sb.append(buf, 0, n)
            if (sb.length > MAX_CHARS) return null
        }
        return sb.toString()
    }

    /** SHA-256 hex of the stored song [name] normalized to UTF-8, or null when there is no such file. */
    suspend fun songHash(name: String): String? = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            writer.recover()
            try {
                val text = readLoaded(name)?.text ?: return@withContext if (fileShare.exists("$SONGS_DIR/$name")) "unreadable" else null
                MessageDigest.getInstance("SHA-256").digest(decodeLatin1(text).toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
            } catch (e: Exception) {
                if (fileShare.exists("$SONGS_DIR/$name")) "unreadable" else null
            }
        }
    }

    /** Writes a received song into the shared songs folder (the caller re-indexes with [refresh]). */
    suspend fun writeSong(name: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val text = TextDecoding.decode(bytes)
        require(TransferProtocol.nameProblem(name) == null) { "Invalid song filename" }
        refreshMutex.withLock { writer.write(name, text) }
    }

    private fun decodeLatin1(text: String): String = TextDecoding.decode(text.toByteArray(Charsets.ISO_8859_1))

    private fun parseHeader(file: String): SongEntryHeader {
        val fallbackTitle = file.substringBeforeLast('.', file)
        return try {
            val loaded = readLoaded(file)
            when {
                loaded == null -> SongEntryHeader(fallbackTitle, null, null, null, SongError.UNREADABLE)
                loaded.text == null -> SongEntryHeader(fallbackTitle, null, null, null, SongError.TOO_LARGE)
                else -> {
                    val r = ChordProParser.parse(decodeLatin1(loaded.text), file)
                    val m = r.song.meta
                    val err = when (r.issue) {
                        ParseIssue.NOT_CHORDPRO -> SongError.NOT_CHORDPRO
                        ParseIssue.EMPTY -> SongError.EMPTY
                        null -> null
                    }
                    SongEntryHeader(m.title, m.artist, m.key, m.capo, err)
                }
            }
        } catch (e: Exception) {
            SongEntryHeader(fallbackTitle, null, null, null, SongError.UNREADABLE)
        }
    }

    /** Fully parses a song; null if the file is missing, unreadable or too large. */
    suspend fun readSong(file: String): ParseResult? = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            writer.recover()
            try {
                val text = readLoaded(file)?.text ?: return@withContext null
                ChordProParser.parse(decodeLatin1(text), file)
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun delete(file: String): Boolean = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            writer.recover()
            fileShare.delete("$SONGS_DIR/$file")
        }
    }

    /** Deletes every file in [files] (an artist's songs); returns how many were removed. Settings are pruned by [refresh]. */
    suspend fun deleteAll(files: List<String>): Int = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            writer.recover()
            files.count { file ->
                try {
                    fileShare.delete("$SONGS_DIR/$file")
                } catch (e: Exception) {
                    false
                }
            }
        }
    }

    suspend fun allSettings(): Map<String, SongSettings> = try {
        decodeSettings(dataStore.data.first()[SETTINGS_KEY])
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyMap()
    }

    suspend fun settings(file: String): SongSettings = allSettings()[file] ?: SongSettings()

    suspend fun saveSettings(file: String, s: SongSettings) {
        dataStore.edit { p ->
            val map = decodeSettings(p[SETTINGS_KEY]).toMutableMap()
            map[file] = s
            p[SETTINGS_KEY] = json.encodeToString(settingsSerializer, map)
        }
        LibraryEvents.settingsChanged.value = LibraryEvents.settingsChanged.value + 1
    }

    /** Reads the app-wide chord notation (Brazilian when never set) and publishes it to [LibraryEvents.notation]. */
    suspend fun loadNotation(): ChordNotation {
        val stored = try {
            dataStore.data.first()[NOTATION_KEY]
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val notation = ChordNotation.entries.firstOrNull { it.name == stored } ?: ChordNotation.BRAZILIAN
        LibraryEvents.notation.value = notation
        return notation
    }

    suspend fun saveNotation(notation: ChordNotation) {
        LibraryEvents.notation.value = notation
        dataStore.edit { p -> p[NOTATION_KEY] = notation.name }
    }

    /** Loads assets/chords.json (once, cached) off the main thread. */
    suspend fun voicings(): VoicingLibrary {
        LibraryEvents.voicingCache?.let { return it }
        return withContext(Dispatchers.IO) {
            LibraryEvents.voicingCache ?: VoicingLibrary.fromJson(readAsset("chords.json").toString(Charsets.UTF_8))
                .also { LibraryEvents.voicingCache = it }
        }
    }

    private fun decodeIndex(s: String?): LibraryIndex =
        if (s == null) LibraryIndex() else try {
            json.decodeFromString(LibraryIndex.serializer(), s)
        } catch (e: Exception) {
            LibraryIndex() // corrupted blob: fall back to a full re-scan
        }

    private fun decodeSettings(s: String?): Map<String, SongSettings> =
        if (s == null) emptyMap() else try {
            json.decodeFromString(settingsSerializer, s)
        } catch (e: Exception) {
            emptyMap()
        }
}
