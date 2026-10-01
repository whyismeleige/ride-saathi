package com.ridesaathi.app.core.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Device TTS is a fallback; hold the latest prompt while its engine initializes. */
internal class DeviceSpeechOutput(context: Context) : SpeechOutput {
    private val handler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var engine: TextToSpeech? = null
    private var initialized = false
    private var ready = false
    private var closed = false
    private data class Prompt(
        val text: String, val locale: Locale, val id: String, val done: (SpeechResult) -> Unit
    )
    private var pending: Prompt? = null

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            handler.post {
                if (!closed) {
                    initialized = true
                    ready = status == TextToSpeech.SUCCESS
                    playPending()
                }
            }
        }
        engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                handler.post { complete(utteranceId, SpeechResult.COMPLETED) }
            }
            @Suppress("OVERRIDE_DEPRECATION")
            @Deprecated("Required by Android")
            override fun onError(utteranceId: String?) {
                handler.post { complete(utteranceId, SpeechResult.FAILED) }
            }
            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                handler.post { complete(utteranceId, SpeechResult.CANCELLED) }
            }
        })
    }

    override fun speak(text: String, locale: Locale, id: String, done: (SpeechResult) -> Unit) {
        stop()
        if (closed) { done(SpeechResult.FAILED); return }
        pending = Prompt(text, locale, id, done)
        if (initialized) playPending()
    }

    private fun playPending() {
        val prompt = pending ?: return
        val tts = engine
        if (!ready || tts == null || tts.setLanguage(prompt.locale) < 0) {
            complete(prompt.id, SpeechResult.FAILED)
            return
        }
        tts.setSpeechRate(1.22f)
        tts.setAudioAttributes(AudioAttributes.Builder().setUsage(
            if (audioManager.mode == AudioManager.MODE_IN_COMMUNICATION) AudioAttributes.USAGE_VOICE_COMMUNICATION
            else AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        if (tts.speak(prompt.text, TextToSpeech.QUEUE_FLUSH, null, prompt.id) != TextToSpeech.SUCCESS) {
            complete(prompt.id, SpeechResult.FAILED)
        }
    }

    private fun complete(id: String?, result: SpeechResult) {
        val prompt = pending ?: return
        if (prompt.id != id) return
        pending = null
        prompt.done(result)
    }

    override fun stop() {
        pending = null
        engine?.stop()
    }

    override fun close() {
        closed = true
        stop()
        engine?.shutdown()
        engine = null
        handler.removeCallbacksAndMessages(null)
    }
}
