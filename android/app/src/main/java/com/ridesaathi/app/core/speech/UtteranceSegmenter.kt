package com.ridesaathi.app.core.speech

import java.io.ByteArrayOutputStream
import java.util.ArrayDeque

/** 20 ms PCM frames + VAD decisions. No Android dependencies; all limits are frame based. */
internal class UtteranceSegmenter {
    data class Event(val started: Boolean = false, val pcm: ByteArray? = null, val tooLong: Boolean = false)
    private val preRoll = ArrayDeque<ByteArray>()
    private var speechFrames = 0
    private var silenceFrames = 0
    private var audio: ByteArrayOutputStream? = null
    private var suppressTail = 0

    fun accept(frame: ByteArray, voiced: Boolean, playback: Boolean, echoCancelled: Boolean): Event {
        require(frame.size == 640)
        // Without AEC, never treat speaker output as a user. Tap-to-interrupt still works.
        if (playback && !echoCancelled) {
            reset()
            suppressTail = 15
            return Event()
        }
        if (suppressTail > 0) { suppressTail--; return Event() }
        val current = audio
        if (current == null) {
            preRoll.addLast(frame.copyOf())
            if (preRoll.size > 15) preRoll.removeFirst()
            speechFrames = if (voiced) speechFrames + 1 else 0
            if (speechFrames < 8) return Event()
            audio = ByteArrayOutputStream().apply { preRoll.forEach { write(it) } }
            preRoll.clear()
            silenceFrames = 0
            return Event(started = true)
        }
        current.write(frame)
        silenceFrames = if (voiced) 0 else silenceFrames + 1
        if (current.size() >= 20 * 16000 * 2) {
            // Never submit a truncated sentence that could turn "yes ... don't" into consent.
            reset()
            return Event(tooLong = true)
        }
        if (silenceFrames >= 40) {
            val result = current.toByteArray()
            reset()
            return Event(pcm = result)
        }
        return Event()
    }

    fun reset() {
        preRoll.clear(); speechFrames = 0; silenceFrames = 0; audio = null; suppressTail = 0
    }
}
