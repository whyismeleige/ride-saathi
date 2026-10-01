package com.ridesaathi.app.core.speech

import org.junit.Assert.*
import org.junit.Test

class UtteranceSegmenterTest {
    private fun frame(value: Byte = 0) = ByteArray(640) { value }

    @Test fun shortNoiseBurstsDoNotInterrupt() {
        val gate = UtteranceSegmenter()
        repeat(10) {
            repeat(7) { assertFalse(gate.accept(frame(), true, true, true).started) }
            assertFalse(gate.accept(frame(), false, true, true).started)
        }
    }

    @Test fun sustainedSpeechInterruptsOnceAndPreservesPreRoll() {
        val gate = UtteranceSegmenter()
        repeat(7) { gate.accept(frame(1), false, true, true) }
        repeat(7) { assertFalse(gate.accept(frame(2), true, true, true).started) }
        assertTrue(gate.accept(frame(3), true, true, true).started)
        repeat(39) { assertNull(gate.accept(frame(), false, false, true).pcm) }
        val audio = requireNotNull(gate.accept(frame(), false, false, true).pcm)
        assertEquals(55 * 640, audio.size)
        assertEquals(1.toByte(), audio[0])
        assertEquals(2.toByte(), audio[7 * 640])
        assertEquals(3.toByte(), audio[14 * 640])
    }

    @Test fun speakerEchoIsSuppressedWithoutAecIncludingTail() {
        val gate = UtteranceSegmenter()
        repeat(100) { assertFalse(gate.accept(frame(), true, true, false).started) }
        repeat(15) { assertFalse(gate.accept(frame(), true, false, false).started) }
        repeat(7) { assertFalse(gate.accept(frame(), true, false, false).started) }
        assertTrue(gate.accept(frame(), true, false, false).started)
    }

    @Test fun resetDropsOldAudioAndOverlongSpeechIsNeverSubmitted() {
        val gate = UtteranceSegmenter()
        repeat(8) { gate.accept(frame(1), true, false, true) }
        gate.reset()
        repeat(999) { assertNull(gate.accept(frame(2), true, false, true).pcm) }
        val result = gate.accept(frame(2), true, false, true)
        assertTrue(result.tooLong)
        assertNull(result.pcm)
    }
}
