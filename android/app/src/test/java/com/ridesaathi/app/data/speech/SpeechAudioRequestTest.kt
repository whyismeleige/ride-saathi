package com.ridesaathi.app.data.speech

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class SpeechAudioRequestTest {
    private class Connection(url: URL, val bytes: ByteArray = "ID3-audio".toByteArray(), val status: Int = 200, val media: String = "audio/mpeg") : HttpURLConnection(url) {
        val body = ByteArrayOutputStream()
        var disconnected = false
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getOutputStream() = body
        override fun getInputStream() = ByteArrayInputStream(bytes)
        override fun getResponseCode() = status
        override fun getContentType() = media
        override fun getContentLengthLong() = bytes.size.toLong()
    }

    @Test fun postsUnicodeTextOnlyToBackendAndReturnsAudio() {
        lateinit var connection: Connection
        val request = SpeechAudioRequest("https://backend.example/") { url -> Connection(url).also { connection = it } }
        val text = "घर & ఇల్లు"
        assertArrayEquals("ID3-audio".toByteArray(), request.fetch(text, "hi"))
        assertEquals("https://backend.example/v1/speech/synthesize", connection.url.toString())
        assertEquals("POST", connection.requestMethod)
        assertEquals("audio/mpeg", connection.getRequestProperty("Accept"))
        assertFalse(connection.instanceFollowRedirects)
        assertFalse(connection.useCaches)
        val body = JSONObject(connection.body.toString("UTF-8"))
        assertEquals(text, body.getString("text"))
        assertEquals("hi", body.getString("language"))
        assertEquals(2, body.length())
        assertTrue(connection.disconnected)
    }

    @Test fun failuresNeverReadOrExposeProviderBody() {
        for (status in listOf(302, 400, 429, 503)) {
            val connection = Connection(URL("https://backend.example"), "private upstream details".toByteArray(), status)
            val request = SpeechAudioRequest("https://backend.example") { connection }
            val error = assertThrows(IOException::class.java) { request.fetch("Private address", "en") }
            assertEquals("Speech unavailable", error.message)
            assertTrue(connection.disconnected)
        }
    }

    @Test fun rejectsEmptyNonAudioAndOversizedResponses() {
        val url = URL("https://backend.example")
        for (connection in listOf(Connection(url, byteArrayOf()), Connection(url, media = "application/json"), Connection(url, ByteArray(SpeechAudioRequest.MAX_AUDIO_BYTES + 1)))) {
            val request = SpeechAudioRequest("https://backend.example") { connection }
            assertThrows(IOException::class.java) { request.fetch("Home", "en") }
            assertTrue(connection.disconnected)
        }
    }

    @Test fun cancelledOrUnconfiguredRequestsNeverOpenAConnection() {
        var opened = 0
        val open: (URL) -> HttpURLConnection = { opened++; Connection(it) }
        val cancelled = SpeechAudioRequest("https://backend.example", open)
        cancelled.cancel()
        assertThrows(IOException::class.java) { cancelled.fetch("Home", "en") }
        assertThrows(IOException::class.java) { SpeechAudioRequest("", open).fetch("Home", "en") }
        assertThrows(IOException::class.java) { SpeechAudioRequest("https://backend.example", open).fetch("x".repeat(2001), "en") }
        assertEquals(0, opened)
    }
}
