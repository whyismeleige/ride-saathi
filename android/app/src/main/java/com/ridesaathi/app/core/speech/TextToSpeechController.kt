package com.ridesaathi.app.core.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Owns utterance identity so stale, failed, or background completions never start listening. */
internal class TextToSpeechController(
    context: Context,
    private val locale: () -> Locale,
    private val active: () -> Boolean
) {
    private val handler = Handler(Looper.getMainLooper())
    private var sequence = 0L
    private var engine: TextToSpeech? = null
    internal var pendingUtterance: String? = null
    internal var afterUtterance: (() -> Unit)? = null

    init {
        engine =
            TextToSpeech(context) { status -> if (status == TextToSpeech.SUCCESS) updateLanguage() }
        engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                handler.post { finishUtterance(utteranceId, true) }
            }

            @Suppress("OVERRIDE_DEPRECATION")
            @Deprecated("Required by Android")
            override fun onError(utteranceId: String?) {
                handler.post { finishUtterance(utteranceId, false) }
            }
        })
    }

    fun updateLanguage() {
        engine?.language = locale()
    }

    fun speak(value: String, after: (() -> Unit)? = null) {
        pendingUtterance = "ride-saathi-${++sequence}"
        afterUtterance = after
        updateLanguage()
        if (engine?.speak(
                value,
                TextToSpeech.QUEUE_FLUSH,
                null,
                pendingUtterance
            ) != TextToSpeech.SUCCESS
        ) clearCompletion()
    }

    internal fun finishUtterance(id: String?, success: Boolean) {
        if (id == null || id != pendingUtterance) return
        val action = afterUtterance
        clearCompletion()
        if (success && active()) action?.invoke()
    }

    fun clearCompletion() {
        pendingUtterance = null; afterUtterance = null
    }

    fun stop() {
        clearCompletion(); engine?.stop()
    }

    fun close() {
        stop(); engine?.shutdown(); engine = null; handler.removeCallbacksAndMessages(null)
    }
}
