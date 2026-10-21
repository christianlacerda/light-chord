package com.christianlacerda.chords.transfer

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HttpReply(val status: Int, val body: ByteArray)

/** The only network surface of the transfer client; fakes implement it in tests. */
interface TransferHttp {
    /** GETs [url]; fails with [TransferException.Unreachable] on I/O errors and when the body exceeds [maxBytes]. */
    suspend fun get(url: String, maxBytes: Int): HttpReply

    /** POSTs a JSON [body]; same failure rules as [get]. */
    suspend fun postJson(url: String, body: String): HttpReply
}

class UrlConnectionHttp(
    private val connectTimeoutMs: Int = 5_000,
    private val readTimeoutMs: Int = 15_000,
) : TransferHttp {

    override suspend fun get(url: String, maxBytes: Int): HttpReply = request(url, null, maxBytes)

    override suspend fun postJson(url: String, body: String): HttpReply =
        request(url, body.toByteArray(Charsets.UTF_8), 64 * 1024)

    private suspend fun request(url: String, post: ByteArray?, maxBytes: Int): HttpReply =
        withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = connectTimeoutMs
                conn.readTimeout = readTimeoutMs
                conn.instanceFollowRedirects = false
                conn.useCaches = false
                if (post != null) {
                    conn.requestMethod = "POST"
                    conn.doOutput = true
                    conn.setFixedLengthStreamingMode(post.size)
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conn.outputStream.use { it.write(post) }
                }
                val status = conn.responseCode
                val stream = if (status in 200..299) conn.inputStream else conn.errorStream
                val out = ByteArrayOutputStream()
                if (stream != null) {
                    stream.use { s ->
                        val buf = ByteArray(8192)
                        while (true) {
                            val n = s.read(buf)
                            if (n < 0) break
                            if (out.size() + n > maxBytes) throw TransferException.Protocol("too large")
                            out.write(buf, 0, n)
                        }
                    }
                }
                HttpReply(status, out.toByteArray())
            } catch (e: IOException) {
                throw TransferException.Unreachable(e.message ?: "I/O error")
            } finally {
                conn?.disconnect()
            }
        }
}
