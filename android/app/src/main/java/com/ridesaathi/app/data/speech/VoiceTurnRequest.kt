package com.ridesaathi.app.data.speech

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal data class VoiceTurn(val transcript: String, val query: String?, val degraded: Boolean)

internal class VoiceTurnRequest(private val baseUrl: String, private val open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }) {
    @Volatile private var cancelled = false
    @Volatile private var connection: HttpURLConnection? = null
    fun cancel() { cancelled = true; connection?.disconnect() }

    fun send(pcm: ByteArray, language: String, context: String): VoiceTurn {
        if (baseUrl.isBlank() || language !in listOf("en", "hi", "te") || context !in listOf("home", "editing", "choices", "confirmation")) throw IOException("Voice unavailable")
        val url = URL("${baseUrl.trim().trimEnd('/')}/v1/speech/turn?language=$language&context=$context")
        if (url.protocol !in listOf("http", "https") || url.userInfo != null || url.ref != null) throw IOException("Voice unavailable")
        val conn = open(url)
        connection = conn
        try {
            checkCancelled()
            conn.requestMethod = "POST"
            conn.connectTimeout = 3_000
            conn.readTimeout = 28_000
            conn.instanceFollowRedirects = false
            conn.useCaches = false
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "audio/wav")
            conn.setRequestProperty("Accept", "application/json")
            val wav = wav(pcm)
            conn.setFixedLengthStreamingMode(wav.size)
            conn.outputStream.use { it.write(wav) }
            checkCancelled()
            if (conn.responseCode != 200) throw IOException("Voice unavailable")
            val output = ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val buffer = ByteArray(4096)
                while (true) {
                    checkCancelled()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > 16384) throw IOException("Voice unavailable")
                    output.write(buffer, 0, count)
                }
            }
            checkCancelled()
            return parseTurn(output.toString("UTF-8"), context)
        } finally { conn.disconnect(); connection = null }
    }

    private fun checkCancelled() { if (cancelled || Thread.currentThread().isInterrupted) throw IOException("Voice cancelled") }

    companion object {
        fun parseTurn(body: String, context: String): VoiceTurn {
            val json = JSONObject(body)
            val transcript = json.getString("transcript")
            if (transcript.length > 2000) throw IOException("Voice unavailable")
            val raw = if (json.isNull("destination_query")) null else json.getString("destination_query")
            // Validate again at the client boundary; never let an LLM response confirm a ride.
            val query = raw?.takeIf { context in listOf("home", "editing") && json.optString("intent") == "destination" &&
                it.length in 2..200 && it.isNotBlank() && transcript.contains(it) }
            return VoiceTurn(transcript, query, json.optBoolean("degraded", false))
        }

        fun wav(pcm: ByteArray): ByteArray {
            require(pcm.size in 3200..640000 && pcm.size % 2 == 0)
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray()).putInt(36 + pcm.size).put("WAVEfmt ".toByteArray())
                .putInt(16).putShort(1).putShort(1).putInt(16000).putInt(32000).putShort(2).putShort(16)
                .put("data".toByteArray()).putInt(pcm.size)
            return header.array() + pcm
        }
    }
}
