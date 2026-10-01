package com.ridesaathi.app.core.speech

import android.content.Context
import java.util.Locale

/** Online neural speech with a device fallback and activity-lifetime cancellation. */
internal class TextToSpeechController(
    context: Context,
    locale: () -> Locale,
    active: () -> Boolean,
    backendBaseUrl: String
) {
    var onSpeakingChanged: (Boolean) -> Unit = {}
    var onInterrupted: () -> Unit = {}
    private val coordinator = SpeechCoordinator(
        OnlineSpeechOutput(context, backendBaseUrl), DeviceSpeechOutput(context), locale, active,
        { onSpeakingChanged(it) }, { onInterrupted() }
    )
    val isSpeaking get() = coordinator.isSpeaking

    internal var pendingUtterance: String?
        get() = coordinator.pendingUtterance
        set(value) { coordinator.pendingUtterance = value }
    internal var afterUtterance: (() -> Unit)?
        get() = coordinator.afterUtterance
        set(value) { coordinator.afterUtterance = value }

    // The next utterance captures the new locale. Cancel any old-language audio now.
    fun updateLanguage() = coordinator.stop()
    fun speak(value: String, after: (() -> Unit)? = null) = coordinator.speak(value, after)
    internal fun finishUtterance(id: String?, success: Boolean) = coordinator.finishUtterance(id, success)
    fun clearCompletion() = coordinator.clearCompletion()
    fun stop() = coordinator.stop()
    fun close() = coordinator.close()
}
