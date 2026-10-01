package com.ridesaathi.app.core.speech

import java.util.Locale

internal enum class SpeechResult { COMPLETED, FAILED, CANCELLED }

/** Output callbacks and coordinator methods run on the main thread. */
internal interface SpeechOutput {
    fun speak(text: String, locale: Locale, id: String, done: (SpeechResult) -> Unit)
    fun stop()
    fun close()
}

/** Online first, one device fallback, and no stale completion can start listening. */
internal class SpeechCoordinator(
    private val online: SpeechOutput,
    private val device: SpeechOutput,
    private val locale: () -> Locale,
    private val active: () -> Boolean,
    private val onSpeakingChanged: (Boolean) -> Unit = {},
    private val onInterrupted: () -> Unit = {}
) {
    @Volatile var isSpeaking = false
        private set
    private var sequence = 0L
    private var usingDevice = false
    private var closed = false
    internal var pendingUtterance: String? = null
    internal var afterUtterance: (() -> Unit)? = null

    fun speak(text: String, after: (() -> Unit)? = null) {
        stop()
        if (closed || !active() || text.isBlank()) return
        val id = "ride-saathi-${++sequence}"
        val language = locale()
        pendingUtterance = id
        isSpeaking = true
        onSpeakingChanged(true)
        afterUtterance = after
        usingDevice = false
        online.speak(text, language, id) onlineDone@{ result ->
            if (pendingUtterance != id || usingDevice) return@onlineDone
            if (result == SpeechResult.FAILED && active() && !closed) {
                usingDevice = true
                device.speak(text, language, id) fallbackDone@{ fallback ->
                    if (pendingUtterance != id) return@fallbackDone
                    finishUtterance(id, fallback == SpeechResult.COMPLETED)
                    if (fallback == SpeechResult.CANCELLED) onInterrupted()
                }
            } else {
                finishUtterance(id, result == SpeechResult.COMPLETED)
                if (result == SpeechResult.CANCELLED) onInterrupted()
            }
        }
    }

    internal fun finishUtterance(id: String?, success: Boolean) {
        if (id == null || id != pendingUtterance) return
        val action = afterUtterance
        clearCompletion()
        if (success && active() && !closed) action?.invoke()
    }

    fun clearCompletion() {
        pendingUtterance = null
        afterUtterance = null
        isSpeaking = false
        onSpeakingChanged(false)
    }

    fun stop() {
        clearCompletion()
        online.stop()
        device.stop()
    }

    fun close() {
        closed = true
        stop()
        online.close()
        device.close()
    }
}
