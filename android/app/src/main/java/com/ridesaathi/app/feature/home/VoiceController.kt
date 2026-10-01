package com.ridesaathi.app.feature.home

import com.ridesaathi.app.AppSession
import android.os.Handler
import android.os.Looper
import com.ridesaathi.app.BuildConfig
import com.ridesaathi.app.core.speech.CaptureEvent
import com.ridesaathi.app.core.speech.VoiceCapture
import com.ridesaathi.app.data.speech.VoiceTurnRequest
import java.util.concurrent.Future
import java.util.concurrent.ScheduledThreadPoolExecutor
import com.ridesaathi.app.domain.search.DestinationChoices
import com.ridesaathi.app.domain.search.DestinationResolver
import com.ridesaathi.app.domain.search.SearchChoice
import com.ridesaathi.app.domain.search.SpeechText
import com.ridesaathi.app.domain.search.VoiceCommands
import com.ridesaathi.app.domain.search.VoiceDecision
import com.ridesaathi.app.navigation.AppScreen

internal class VoiceController(private val app: AppSession) {
    val state = VoiceUiState()
    private val capture = VoiceCapture(app.activity) { app.speechOutput.isSpeaking }
    private val worker = ScheduledThreadPoolExecutor(1).apply { removeOnCancelPolicy = true }
    private val handler = Handler(Looper.getMainLooper())
    private var enabled = false
    private var resumeMicrophone = false
    private var generation = 0L
    private var request: VoiceTurnRequest? = null
    private var task: Future<*>? = null
    private var idle: Runnable? = null

    fun toggleListening() {
        when {
            app.speechOutput.isSpeaking -> { stopListening(); requestMicrophone() }
            state.listening -> endSession()
            else -> requestMicrophone()
        }
    }

    fun canListen() = with(app) {
        screen in listOf(AppScreen.Home, AppScreen.RideConfirmation) ||
                (screen == AppScreen.DestinationSearch && destination.destinationSearch?.loading == false)
    }

    fun requestMicrophone(): Unit = with(app) {
        shared.cancelSharedLocation()
        permissions.requestMicrophone { granted ->
            if (canListen() && !isDestroyed) {
                if (granted) {
                    enabled = true
                    if (foreground) startListening() else resumeMicrophone = true
                }
                else { message = word("micDenied"); speak(message) }
            }
        }
    }

    fun onResume() {
        if (resumeMicrophone) { resumeMicrophone = false; startListening() }
    }

    fun startListening(): Unit = with(app) {
        if (!canListen() || !foreground || isDestroyed || ride.state.handoffInProgress || !permissions.hasMicrophone()) return
        enabled = true
        if (state.listening) return
        shared.cancelSharedLocation()
        speechOutput.stop()
        startCapture()
    }

    /** Called before TTS starts. Monitoring must not flush the prompt it is guarding. */
    fun prepareForPrompt() {
        if (enabled && app.foreground && canListen() && app.permissions.hasMicrophone() && !app.ride.state.handoffInProgress) {
            if (!state.listening) startCapture()
            armIdle()
        } else stopListening()
    }

    private fun startCapture() {
        state.listening = true
        state.speechTranscript = ""
        state.transcriptIsFinal = false
        app.message = ""
        armIdle()
        capture.start { event ->
            if (!enabled || !app.foreground || !canListen() || app.isDestroyed || app.ride.state.handoffInProgress) {
                stopListening(); return@start
            }
            when (event) {
                is CaptureEvent.Ready -> state.bargeInAvailable = event.bargeInAvailable
                CaptureEvent.Started -> {
                    // Keep the recorder running: its pre-roll contains the first syllables.
                    app.speechOutput.stop()
                    cancelTurn()
                    state.speechTranscript = ""
                    state.transcriptIsFinal = false
                    app.message = ""
                    armIdle()
                }
                is CaptureEvent.Utterance -> submit(event.pcm)
                CaptureEvent.TooLong -> { endSession(); app.message = app.word("speechFailed") }
                CaptureEvent.Failed -> { endSession(); app.message = app.word("voiceUnavailable") }
            }
        }
    }

    private fun context() = when (app.screen) {
        AppScreen.RideConfirmation -> "confirmation"
        AppScreen.DestinationSearch -> if (app.destination.destinationSearch?.editing == true) "editing" else "choices"
        else -> "home"
    }

    private fun submit(pcm: ByteArray) {
        cancelTurn()
        val token = generation
        val screen = app.screen
        val selection = app.ride.state.selected
        val search = app.destination.destinationSearch
        val language = app.profile.language
        val context = context()
        val call = VoiceTurnRequest(BuildConfig.API_BASE_URL)
        request = call
        state.processing = true
        armIdle()
        task = worker.submit {
            val result = runCatching { call.send(pcm, language, context) }
            handler.post {
                if (token != generation) return@post
                if (!enabled || !app.foreground || app.isDestroyed ||
                    screen != app.screen || selection != app.ride.state.selected || search != app.destination.destinationSearch ||
                    language != app.profile.language || !canListen()) {
                    stopListening(); return@post
                }
                request = null; task = null; state.processing = false
                result.onSuccess { turn ->
                    state.speechTranscript = turn.transcript
                    state.transcriptIsFinal = true
                    handleSpeech(turn.transcript, turn.query)
                }.onFailure { app.message = app.word("speechFailed") }
                armIdle()
            }
        }
    }

    private fun cancelTurn() {
        generation++
        request?.cancel(); request = null
        task?.cancel(true); task = null
        state.processing = false
    }

    private fun armIdle() {
        idle?.let(handler::removeCallbacks)
        idle = Runnable {
            if (app.speechOutput.isSpeaking || state.processing) armIdle() else endSession()
        }.also { handler.postDelayed(it, 30_000) }
    }

    /** Suspend capture during navigation; keep an explicitly started voice session for the next prompt. */
    fun stopListening() {
        cancelTurn()
        capture.stop()
        app.speechOutput.stop()
        state.listening = false
        idle?.let(handler::removeCallbacks); idle = null
    }

    fun endSession() { enabled = false; resumeMicrophone = false; stopListening() }
    fun close() { endSession(); capture.close(); worker.shutdownNow(); handler.removeCallbacksAndMessages(null) }

    fun handleSpeech(raw: String, interpretedQuery: String? = null): Unit = with(app) {
        if (!canListen() || ride.state.handoffInProgress) return
        if (raw.isBlank()) {
            message = word("speechFailed"); return
        }
        if (screen == AppScreen.DestinationSearch) {
            destination.handleSearchSpeech(raw, interpretedQuery); return
        }
        val decision = if (screen == AppScreen.RideConfirmation) VoiceCommands.decision(raw) else {
            // A place such as “Central bus stop” must not become a stop command.
            val command = SpeechText.tokens(raw).joinToString(" ")
            when {
                DestinationChoices.parse(raw, emptyList()) == SearchChoice.Cancel ||
                        command in listOf(
                    "please stop",
                    "please cancel",
                    "yes cancel",
                    "बंद"
                ) -> VoiceDecision.Cancel

                command in listOf("no", "no thanks", "नहीं", "नही", "मत") -> VoiceDecision.No
                else -> VoiceDecision.Unknown
            }
        }
        if (decision == VoiceDecision.Cancel) {
            ride.cancelRide(); return
        }
        if (screen == AppScreen.RideConfirmation) {
            when (decision) {
                VoiceDecision.Yes -> ride.confirmRide()
                VoiceDecision.No -> destination.returnToChoices()
                else -> message = word("speechFailed")
            }
            return
        }
        if (decision == VoiceDecision.No) {
            message = word("unknown"); return
        }
        resolveDestination(raw, interpretedQuery = interpretedQuery)
    }

    fun resolveDestination(raw: String, queryIsExtracted: Boolean = false, interpretedQuery: String? = null): Unit = with(app) {
        val matched = DestinationResolver.matches(raw, places)
        when (matched.size) {
            0 -> destination.beginDestinationSearch(interpretedQuery ?: raw, extractPhrase = interpretedQuery == null && !queryIsExtracted)
            1 -> ride.choose(matched.first())
            else -> {
                destination.cancelDestinationSearch()
                ride.state.choices = matched; screen = AppScreen.Clarification; message = ""; speak(
                    word("ambiguous")
                )
            }
        }
    }
}
