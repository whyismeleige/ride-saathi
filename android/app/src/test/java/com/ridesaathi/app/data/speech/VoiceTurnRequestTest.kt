package com.ridesaathi.app.data.speech

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VoiceTurnRequestTest {
    @Test fun waveFormatMatchesAzureStt() {
        val pcm = ByteArray(32000) { 3 }
        val wav = VoiceTurnRequest.wav(pcm)
        assertEquals("RIFF", String(wav.copyOfRange(0, 4)))
        val header = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(1.toShort(), header.getShort(22))
        assertEquals(16000, header.getInt(24))
        assertEquals(16.toShort(), header.getShort(34))
        assertEquals(pcm.size, header.getInt(40))
        assertArrayEquals(pcm, wav.copyOfRange(44, wav.size))
    }

    @Test fun onlyGroundedHomeOrEditingQueriesAreAccepted() {
        val body = """{"transcript":"Take me to Apollo Hospital","intent":"destination","destination_query":"Apollo Hospital","degraded":false}"""
        assertEquals("Apollo Hospital", VoiceTurnRequest.parseTurn(body, "home").query)
        assertEquals("Apollo Hospital", VoiceTurnRequest.parseTurn(body, "editing").query)
        assertNull(VoiceTurnRequest.parseTurn(body, "confirmation").query)
        assertNull(VoiceTurnRequest.parseTurn(body, "choices").query)
        assertNull(VoiceTurnRequest.parseTurn(body.replace("\"destination_query\":\"Apollo Hospital\"", "\"destination_query\":\"Invented Place\""), "home").query)
    }

    @Test fun degradedModelResultPreservesNegativeConfirmation() {
        val turn = VoiceTurnRequest.parseTurn("""{"transcript":"No, do not confirm","intent":"unclear","destination_query":null,"degraded":true}""", "confirmation")
        assertEquals("No, do not confirm", turn.transcript)
        assertTrue(turn.degraded)
        assertNull(turn.query)
    }

    @Test fun cancelledRequestCannotDeliverAResponse() {
        lateinit var request: VoiceTurnRequest
        var disconnected = false
        val connection = object : HttpURLConnection(URL("https://backend.example")) {
            override fun connect() = Unit
            override fun disconnect() { disconnected = true }
            override fun usingProxy() = false
            override fun getOutputStream() = ByteArrayOutputStream().also { request.cancel() }
            override fun getInputStream() = ByteArrayInputStream(byteArrayOf())
        }
        request = VoiceTurnRequest("https://backend.example") { connection }
        assertThrows(IOException::class.java) { request.send(ByteArray(32000), "en", "home") }
        assertTrue(disconnected)
        assertFalse(connection.instanceFollowRedirects)
    }
}
