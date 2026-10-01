package com.ridesaathi.app.data.speech

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** One cancellable POST. Spoken text stays out of URLs, logs, and disk caches. */
internal class SpeechAudioRequest(
    private val baseUrl: String,
    private val open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }
) {
    @Volatile private var cancelled = false
    @Volatile private var connection: HttpURLConnection? = null

    fun cancel() {
        cancelled = true
        connection?.disconnect()
    }

    fun fetch(text: String, language: String): ByteArray {
        if (cancelled || baseUrl.isBlank() || text.length > 2000) throw IOException("Speech unavailable")
        val url = URL("${baseUrl.trim().trimEnd('/')}/v1/speech/synthesize")
        if (url.protocol !in setOf("https", "http") || url.userInfo != null || url.query != null || url.ref != null) {
            throw IOException("Speech unavailable")
        }
        val conn = open(url)
        connection = conn
        try {
            checkCancelled()
            conn.requestMethod = "POST"
            conn.connectTimeout = 3_000
            conn.readTimeout = 10_000
            conn.instanceFollowRedirects = false
            conn.useCaches = false
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Accept", "audio/mpeg")
            conn.setRequestProperty("X-Request-Id", UUID.randomUUID().toString())
            val body = JSONObject().put("text", text).put("language", language).toString().toByteArray(Charsets.UTF_8)
            conn.setFixedLengthStreamingMode(body.size)
            conn.outputStream.use { it.write(body) }
            checkCancelled()
            if (conn.responseCode != 200 || conn.contentType?.substringBefore(';') != "audio/mpeg") {
                throw IOException("Speech unavailable")
            }
            if (conn.contentLengthLong > MAX_AUDIO_BYTES) throw IOException("Speech unavailable")
            return conn.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    checkCancelled()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > MAX_AUDIO_BYTES) throw IOException("Speech unavailable")
                    output.write(buffer, 0, count)
                }
                checkCancelled()
                if (output.size() == 0) throw IOException("Speech unavailable")
                output.toByteArray()
            }
        } finally {
            conn.disconnect()
            connection = null
        }
    }

    private fun checkCancelled() {
        if (cancelled || Thread.currentThread().isInterrupted) throw IOException("Speech cancelled")
    }

    companion object { const val MAX_AUDIO_BYTES = 2 * 1024 * 1024 }
}
