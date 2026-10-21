package com.christianlacerda.chords.transfer

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

private const val TOKEN = "0123456789abcdef0123456789abcdef"

private fun bytesOf(s: String) = s.toByteArray(Charsets.UTF_8)

private fun manifestJson(vararg files: Triple<Int, String, ByteArray>, size: (ByteArray) -> Long = { it.size.toLong() }): String =
    files.joinToString(prefix = """{"version":1,"sender":"Chord Desk","files":[""", postfix = "]}") { (id, name, b) ->
        """{"id":$id,"name":"$name","size":${size(b)},"sha256":"${TransferProtocol.sha256Hex(b)}"}"""
    }

/** Serves canned replies; records POSTs. */
private class FakeHttp(
    val manifest: String? = null,
    val files: Map<Int, ByteArray> = emptyMap(),
    val manifestStatus: Int = 200,
    val unreachable: Boolean = false,
) : TransferHttp {
    val posted = ArrayList<String>()
    val requested = ArrayList<String>()

    override suspend fun get(url: String, maxBytes: Int): HttpReply {
        requested.add(url)
        if (unreachable) throw TransferException.Unreachable("down")
        return when {
            url.contains("/manifest") -> HttpReply(manifestStatus, bytesOf(manifest ?: ""))
            url.contains("/file/") -> {
                val id = url.substringAfter("/file/").substringBefore('?').toInt()
                files[id]?.let { HttpReply(200, it) } ?: HttpReply(404, ByteArray(0))
            }
            else -> HttpReply(404, ByteArray(0))
        }
    }

    override suspend fun postJson(url: String, body: String): HttpReply {
        posted.add(body)
        return HttpReply(204, ByteArray(0))
    }
}

private class FakeHost(
    val existing: Map<String, ByteArray> = emptyMap(),
    val replace: Boolean = true,
) : TransferHost {
    val written = LinkedHashMap<String, ByteArray>()
    val progress = ArrayList<TransferProgress>()
    var asked = 0
    var askedCount = -1

    override suspend fun existingHash(name: String): String? = existing[name]?.let { TransferProtocol.sha256Hex(it) }
    override suspend fun write(name: String, bytes: ByteArray) { written[name] = bytes }
    override suspend fun confirmReplace(conflicts: Int): Boolean { asked++; askedCount = conflicts; return replace }
    override fun onProgress(progress: TransferProgress) { this.progress.add(progress) }
}

class TransferUrlTest {
    @Test
    fun acceptsValidCode() {
        val t = assertNotNull(TransferProtocol.parseQr("http://192.168.1.20:8123/lightchord/v1?t=$TOKEN"))
        assertEquals("http://192.168.1.20:8123/lightchord/v1", t.base)
        assertEquals(TOKEN, t.token)
        assertEquals("http://192.168.1.20:8123/lightchord/v1/manifest?t=$TOKEN", t.url("/manifest"))
    }

    @Test
    fun rejectsEverythingElse() {
        val bad = listOf(
            "https://192.168.1.20:8123/lightchord/v1?t=$TOKEN",
            "http://192.168.1.20:8123/other?t=$TOKEN",
            "http://192.168.1.20:8123/lightchord/v1/?t=$TOKEN",
            "http://192.168.1.20:8123/lightchord/v1",
            "http://192.168.1.20:8123/lightchord/v1?t=abc",
            "http://192.168.1.20:8123/lightchord/v1?t=${TOKEN.uppercase()}",
            "http://192.168.1.20:8123/lightchord/v1?t=${TOKEN}0",
            "http://192.168.1.20:8123/lightchord/v1?x=$TOKEN",
            "http://user@192.168.1.20/lightchord/v1?t=$TOKEN",
            "otpauth://totp/x?secret=1",
            "hello world",
            "",
        )
        for (s in bad) assertNull(TransferProtocol.parseQr(s), s)
    }

    @Test
    fun portIsOptional() {
        assertEquals("http://10.0.0.2/lightchord/v1", TransferProtocol.parseQr("http://10.0.0.2/lightchord/v1?t=$TOKEN")?.base)
    }
}

class ManifestTest {
    private val sha = "a".repeat(64)

    private fun m(name: String, size: Long = 10, id: Int = 0, hash: String = sha) =
        """{"version":1,"files":[{"id":$id,"name":${kotlinx.serialization.json.JsonPrimitive(name)},"size":$size,"sha256":"$hash"}]}"""

    @Test
    fun parsesGoodManifest() {
        val v = TransferProtocol.parseManifest(m("Neil Young - Heart of Gold.cho"))
        assertEquals(1, v.files.size)
        assertEquals("Neil Young - Heart of Gold.cho", v.files[0].name)
        assertTrue(v.rejected.isEmpty())
    }

    @Test
    fun rejectsBadNamesAndExtensions() {
        for (n in listOf("a/b.cho", "a\\b.cho", "..", ".hidden.cho", "song.pdf", "song", "")) {
            val v = TransferProtocol.parseManifest(m(n))
            assertTrue(v.files.isEmpty(), n)
            assertEquals(1, v.rejected.size, n)
        }
        for (n in listOf("a.cho", "a.chopro", "a.crd", "a.txt", "A.CHO")) {
            assertEquals(1, TransferProtocol.parseManifest(m(n)).files.size, n)
        }
    }

    @Test
    fun rejectsTooLargeFile() {
        val v = TransferProtocol.parseManifest(m("a.cho", size = 256L * 1024 + 1))
        assertEquals(listOf(Failure("a.cho", "too large")), v.rejected)
        assertEquals(1, TransferProtocol.parseManifest(m("a.cho", size = 256L * 1024)).files.size)
    }

    @Test
    fun rejectsBadHash() {
        assertEquals("bad hash", TransferProtocol.parseManifest(m("a.cho", hash = "xyz")).rejected.single().reason)
    }

    @Test
    fun rejectsDuplicateNames() {
        val json = """{"version":1,"files":[
            {"id":0,"name":"a.cho","size":1,"sha256":"$sha"},{"id":1,"name":"A.cho","size":1,"sha256":"$sha"}]}"""
        val v = TransferProtocol.parseManifest(json)
        assertEquals(1, v.files.size)
        assertEquals("duplicate name", v.rejected.single().reason)
    }

    @Test
    fun wholeManifestProblems() {
        assertFailsWith<TransferException.Protocol> { TransferProtocol.parseManifest("not json") }
        assertFailsWith<TransferException.Protocol> { TransferProtocol.parseManifest("""{"version":2,"files":[]}""") }
        val many = (0..200).joinToString(prefix = """{"version":1,"files":[""", postfix = "]}") {
            """{"id":$it,"name":"s$it.cho","size":1,"sha256":"$sha"}"""
        }
        assertFailsWith<TransferException.Protocol> { TransferProtocol.parseManifest(many) }
    }
}

class DuplicateAndDoneTest {
    @Test
    fun classifiesByHash() {
        val same = bytesOf("same")
        val files = listOf(
            ManifestFile(0, "new.cho", 1, TransferProtocol.sha256Hex(bytesOf("n"))),
            ManifestFile(1, "same.cho", 4, TransferProtocol.sha256Hex(same)),
            ManifestFile(2, "diff.cho", 4, TransferProtocol.sha256Hex(bytesOf("new"))),
        )
        val have = mapOf("same.cho" to TransferProtocol.sha256Hex(same), "diff.cho" to TransferProtocol.sha256Hex(bytesOf("old")))
        val kinds = TransferProtocol.classify(files) { have[it] }.map { it.kind }
        assertEquals(listOf(FileClass.NEW, FileClass.SAME, FileClass.CONFLICT), kinds)
    }

    @Test
    fun doneJsonShape() {
        val s = TransferSummary(listOf("a.cho"), listOf("b.cho"), listOf("c.cho"), listOf(Failure("d \"x\".cho", "too large")))
        assertEquals(
            """{"received":["a.cho"],"replaced":["b.cho"],"skipped":["c.cho"],"failed":[{"name":"d \"x\".cho","reason":"too large"}]}""",
            TransferProtocol.doneJson(s),
        )
    }
}

class TransferClientTest {
    private val target = TransferProtocol.parseQr("http://1.2.3.4:5/lightchord/v1?t=$TOKEN")!!

    @Test
    fun receivesNewFilesAndReportsDone() = runBlocking<Unit> {
        val a = bytesOf("{title: A}\n[C]a")
        val b = bytesOf("{title: B}")
        val http = FakeHttp(manifestJson(Triple(0, "a.cho", a), Triple(1, "b.cho", b)), mapOf(0 to a, 1 to b))
        val host = FakeHost()
        val s = TransferClient(http, target).run(host)
        assertEquals(listOf("a.cho", "b.cho"), s.received)
        assertEquals(setOf("a.cho", "b.cho"), host.written.keys)
        assertEquals(0, host.asked)
        assertEquals(TransferProgress.Connecting, host.progress.first())
        assertEquals(TransferProgress.Receiving(2, 2), host.progress.last())
        assertEquals(1, http.posted.size)
        assertTrue(http.posted[0].contains(""""received":["a.cho","b.cho"]"""))
    }

    @Test
    fun hashMismatchFailsOnlyThatFile() = runBlocking<Unit> {
        val a = bytesOf("good")
        val b = bytesOf("expected")
        val http = FakeHttp(manifestJson(Triple(0, "a.cho", a), Triple(1, "b.cho", b)), mapOf(0 to a, 1 to bytesOf("tampered")))
        val host = FakeHost()
        val s = TransferClient(http, target).run(host)
        assertEquals(listOf("a.cho"), s.received)
        assertEquals(listOf(Failure("b.cho", "hash mismatch")), s.failed)
        assertTrue("b.cho" !in host.written)
        assertTrue(http.posted.single().contains("hash mismatch"))
    }

    @Test
    fun duplicatesSkipSameAndAskOnceForConflicts() = runBlocking<Unit> {
        val same = bytesOf("same")
        val newer = bytesOf("newer")
        val newer2 = bytesOf("newer2")
        val http = FakeHttp(
            manifestJson(Triple(0, "same.cho", same), Triple(1, "c1.cho", newer), Triple(2, "c2.cho", newer2)),
            mapOf(0 to same, 1 to newer, 2 to newer2),
        )
        val host = FakeHost(existing = mapOf("same.cho" to same, "c1.cho" to bytesOf("o1"), "c2.cho" to bytesOf("o2")))
        val s = TransferClient(http, target).run(host)
        assertEquals(1, host.asked)
        assertEquals(2, host.askedCount)
        assertEquals(listOf("same.cho"), s.skipped)
        assertEquals(listOf("c1.cho", "c2.cho"), s.replaced)
        assertTrue(http.requested.none { it.contains("/file/0") })
    }

    @Test
    fun conflictsSkippedWhenUserSaysSkip() = runBlocking<Unit> {
        val newer = bytesOf("newer")
        val http = FakeHttp(manifestJson(Triple(0, "c.cho", newer)), mapOf(0 to newer))
        val host = FakeHost(existing = mapOf("c.cho" to bytesOf("old")), replace = false)
        val s = TransferClient(http, target).run(host)
        assertEquals(listOf("c.cho"), s.skipped)
        assertTrue(host.written.isEmpty())
        assertTrue(http.requested.none { it.contains("/file/") })
        assertEquals(1, http.posted.size)
    }

    @Test
    fun oversizedManifestEntryIsFailedNotDownloaded() = runBlocking<Unit> {
        val a = bytesOf("x")
        val http = FakeHttp(manifestJson(Triple(0, "big.cho", a), size = { 300L * 1024 }), mapOf(0 to a))
        val s = TransferClient(http, target).run(FakeHost())
        assertEquals(listOf(Failure("big.cho", "too large")), s.failed)
        assertTrue(http.requested.none { it.contains("/file/") })
    }

    @Test
    fun expiredToken() = runBlocking<Unit> {
        val http = FakeHttp(manifestStatus = 401)
        assertFailsWith<TransferException.Expired> { TransferClient(http, target).run(FakeHost()) }
    }

    @Test
    fun unreachable() = runBlocking<Unit> {
        val http = FakeHttp(unreachable = true)
        assertFailsWith<TransferException.Unreachable> { TransferClient(http, target).run(FakeHost()) }
    }

    @Test
    fun endToEndAgainstLocalServer() = runBlocking<Unit> {
        val a = bytesOf("{title: Local}\n[G]hi")
        val manifest = manifestJson(Triple(0, "local.cho", a))
        val posted = ArrayList<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        fun reply(ex: com.sun.net.httpserver.HttpExchange, status: Int, body: ByteArray) {
            if (status == 204) ex.sendResponseHeaders(204, -1) else {
                ex.sendResponseHeaders(status, body.size.toLong())
                ex.responseBody.use { it.write(body) }
            }
            ex.close()
        }
        server.createContext("/lightchord/v1") { ex ->
            if (ex.requestURI.rawQuery != "t=$TOKEN") return@createContext reply(ex, 401, ByteArray(0))
            when (ex.requestURI.path) {
                "/lightchord/v1/manifest" -> reply(ex, 200, bytesOf(manifest))
                "/lightchord/v1/file/0" -> reply(ex, 200, a)
                "/lightchord/v1/done" -> {
                    posted.add(ex.requestBody.readBytes().toString(Charsets.UTF_8))
                    reply(ex, 204, ByteArray(0))
                }
                else -> reply(ex, 404, ByteArray(0))
            }
        }
        server.start()
        try {
            val t = TransferProtocol.parseQr("http://127.0.0.1:${server.address.port}/lightchord/v1?t=$TOKEN")!!
            val host = FakeHost()
            val s = TransferClient(UrlConnectionHttp(), t).run(host)
            assertEquals(listOf("local.cho"), s.received)
            assertTrue(host.written["local.cho"]!!.contentEquals(a))
            assertEquals(1, posted.size)
            assertTrue(posted[0].contains(""""received":["local.cho"]"""))
            val bad = TransferProtocol.parseQr("http://127.0.0.1:${server.address.port}/lightchord/v1?t=${"f".repeat(32)}")!!
            assertFailsWith<TransferException.Expired> { TransferClient(UrlConnectionHttp(), bad).run(FakeHost()) }
        } finally {
            server.stop(0)
        }
    }
}
