package com.ridesaathi.app.core.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

internal sealed interface SpeechEvent {
    data class Partial(val text: String) : SpeechEvent
    data class Final(val text: String) : SpeechEvent
    data object Failed : SpeechEvent
}

/** A single recognition session; cancellation invalidates callbacks before destroying Android resources. */
internal class SpeechRecognizerController(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var generation = 0
    private var timeout: Runnable? = null
    fun available() = SpeechRecognizer.isRecognitionAvailable(context)
    fun start(language: String, onEvent: (SpeechEvent) -> Unit) {
        stop()
        val current = generation
        fun emit(event: SpeechEvent) {
            if (generation != current) return
            if (event !is SpeechEvent.Partial) stop()
            onEvent(event)
        }
        timeout = Runnable { emit(SpeechEvent.Failed) }.also { handler.postDelayed(it, 20_000) }
        try {
            val service = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = service
            service.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onError(error: Int) = emit(SpeechEvent.Failed)
                override fun onResults(results: Bundle?) = emit(
                    SpeechEvent.Final(
                        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull().orEmpty()
                    )
                )

                override fun onPartialResults(partialResults: Bundle?) {
                    val heard =
                        partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                    if (!heard.isNullOrBlank()) emit(SpeechEvent.Partial(heard))
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            service.startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            )
        } catch (_: RuntimeException) {
            emit(SpeechEvent.Failed)
        }
    }

    fun stop() {
        generation++
        timeout?.let(handler::removeCallbacks)
        timeout = null
        val old = recognizer
        recognizer = null
        old?.cancel()
        old?.destroy()
    }
}
