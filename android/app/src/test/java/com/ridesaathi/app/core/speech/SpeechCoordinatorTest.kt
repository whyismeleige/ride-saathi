package com.ridesaathi.app.core.speech

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class SpeechCoordinatorTest {
    private class FakeOutput : SpeechOutput {
        data class Call(val text: String, val locale: Locale, val done: (SpeechResult) -> Unit)
        val calls = mutableListOf<Call>()
        var stops = 0
        var closed = false
        override fun speak(text: String, locale: Locale, id: String, done: (SpeechResult) -> Unit) {
            calls += Call(text, locale, done)
        }
        override fun stop() { stops++ }
        override fun close() { closed = true }
    }
    private val online = FakeOutput()
    private val device = FakeOutput()
    private var active = true
    private var language = Locale.forLanguageTag("hi-IN")
    private val coordinator = SpeechCoordinator(online, device, { language }, { active })
    private var completed = 0

    @Test fun onlinePlaybackFinishesOnceWithoutDeviceFallback() {
        coordinator.speak("घर चलें") { completed++ }
        assertEquals(language, online.calls.single().locale)
        assertEquals(0, completed)
        online.calls.single().done(SpeechResult.COMPLETED)
        online.calls.single().done(SpeechResult.COMPLETED)
        assertEquals(1, completed)
        assertTrue(device.calls.isEmpty())
    }

    @Test fun networkFailureFallsBackOnceAndWaitsForActualPlayback() {
        coordinator.speak("घर चलें") { completed++ }
        online.calls.single().done(SpeechResult.FAILED)
        online.calls.single().done(SpeechResult.FAILED)
        online.calls.single().done(SpeechResult.COMPLETED)
        assertEquals(0, completed)
        assertEquals("घर चलें", device.calls.single().text)
        assertEquals(language, device.calls.single().locale)
        device.calls.single().done(SpeechResult.COMPLETED)
        device.calls.single().done(SpeechResult.COMPLETED)
        assertEquals(1, completed)
    }

    @Test fun failureOfBothOutputsNeverStartsListening() {
        coordinator.speak("Home") { completed++ }
        online.calls.single().done(SpeechResult.FAILED)
        device.calls.single().done(SpeechResult.FAILED)
        assertEquals(0, completed)
        assertNull(coordinator.pendingUtterance)
    }

    @Test fun supersededDownloadCannotPlayFallbackOrCompleteNewUtterance() {
        coordinator.speak("Old") { completed += 100 }
        language = Locale.forLanguageTag("te-IN")
        coordinator.speak("New") { completed++ }
        online.calls[0].done(SpeechResult.FAILED)
        online.calls[0].done(SpeechResult.COMPLETED)
        assertTrue(device.calls.isEmpty())
        assertEquals("te", online.calls[1].locale.language)
        online.calls[1].done(SpeechResult.COMPLETED)
        assertEquals(1, completed)
    }

    @Test fun stoppedDownloadDoesNotFallBackOrListen() {
        coordinator.speak("Home") { completed++ }
        coordinator.stop()
        online.calls.single().done(SpeechResult.FAILED)
        online.calls.single().done(SpeechResult.COMPLETED)
        assertTrue(device.calls.isEmpty())
        assertEquals(0, completed)
    }

    @Test fun staleDeviceCallbackCannotCompleteNewSpeech() {
        coordinator.speak("Old") { completed += 100 }
        online.calls.single().done(SpeechResult.FAILED)
        coordinator.speak("New") { completed++ }
        device.calls.single().done(SpeechResult.COMPLETED)
        assertEquals(0, completed)
        online.calls.last().done(SpeechResult.COMPLETED)
        assertEquals(1, completed)
    }

    @Test fun backgroundAndAudioFocusCancellationNeverRestartListening() {
        coordinator.speak("Home") { completed++ }
        online.calls.single().done(SpeechResult.CANCELLED)
        assertTrue(device.calls.isEmpty())
        assertEquals(0, completed)
        coordinator.speak("Home") { completed++ }
        active = false
        online.calls.last().done(SpeechResult.FAILED)
        assertTrue(device.calls.isEmpty())
        coordinator.speak("Background") { completed++ }
        assertEquals(2, online.calls.size)
        assertEquals(0, completed)
    }

    @Test fun closeReleasesBothOutputsAndIgnoresLateSuccess() {
        coordinator.speak("Home") { completed++ }
        coordinator.close()
        online.calls.single().done(SpeechResult.COMPLETED)
        coordinator.speak("Closed") { completed++ }
        assertTrue(online.closed)
        assertTrue(device.closed)
        assertEquals(1, online.calls.size)
        assertEquals(0, completed)
    }

    @Test fun audioFocusLossEndsTheVoiceSessionExactlyOnce() {
        var interruptions = 0
        val speech = SpeechCoordinator(online, device, { language }, { active }, onInterrupted = { interruptions++ })
        speech.speak("Home") { completed++ }
        assertTrue(speech.isSpeaking)
        online.calls.single().done(SpeechResult.CANCELLED)
        online.calls.single().done(SpeechResult.CANCELLED)
        assertEquals(1, interruptions)
        assertFalse(speech.isSpeaking)
        assertEquals(0, completed)
        assertTrue(device.calls.isEmpty())
    }

    @Test fun deviceCancellationDoesNotTriggerRepeatedInterruption() {
        var interruptions = 0
        val speech = SpeechCoordinator(online, device, { language }, { active }, onInterrupted = { interruptions++ })
        speech.speak("Home") { completed++ }
        online.calls.single().done(SpeechResult.FAILED)
        device.calls.single().done(SpeechResult.CANCELLED)
        device.calls.single().done(SpeechResult.CANCELLED)
        assertEquals(1, interruptions)
        assertFalse(speech.isSpeaking)
        assertEquals(0, completed)
    }
}
