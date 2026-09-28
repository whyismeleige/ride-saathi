package com.ridesaathi.app.feature.home

import com.ridesaathi.app.AppSession
import com.ridesaathi.app.core.speech.SpeechEvent
import com.ridesaathi.app.core.speech.SpeechRecognizerController
import com.ridesaathi.app.domain.search.DestinationChoices
import com.ridesaathi.app.domain.search.DestinationResolver
import com.ridesaathi.app.domain.search.SearchChoice
import com.ridesaathi.app.domain.search.SpeechText
import com.ridesaathi.app.domain.search.VoiceCommands
import com.ridesaathi.app.domain.search.VoiceDecision
import com.ridesaathi.app.navigation.AppScreen

internal class VoiceController(private val app: AppSession) {
    val state = VoiceUiState()
    private val speech = SpeechRecognizerController(app.activity)

    fun toggleListening() {
        if (state.listening) stopListening() else requestMicrophone()
    }

    fun canListen() = with(app) {
        screen in listOf(AppScreen.Home, AppScreen.RideConfirmation) ||
                (screen == AppScreen.DestinationSearch && destination.destinationSearch?.loading == false)
    }

    fun requestMicrophone(): Unit = with(app) {
        shared.cancelSharedLocation()
        permissions.requestMicrophone { granted ->
            if (canListen()) {
                if (granted) startListening()
                else {
                    message = word("micDenied"); speak(message)
                }
            }
        }
    }

    fun startListening(): Unit = with(app) {
        if (!canListen() || ride.state.handoffInProgress) return
        shared.cancelSharedLocation()
        if (!speech.available()) {
            message = word("voiceUnavailable"); return
        }
        speechOutput.stop()
        stopListening()
        state.speechTranscript = ""
        state.transcriptIsFinal = false
        state.listening = true
        message = ""
        speech.start(locale().toLanguageTag()) { event ->
            when (event) {
                is SpeechEvent.Partial -> {
                    state.speechTranscript = event.text; state.transcriptIsFinal = false
                }

                is SpeechEvent.Final -> {
                    stopListening()
                    state.speechTranscript = event.text
                    state.transcriptIsFinal = true
                    handleSpeech(event.text)
                }

                SpeechEvent.Failed -> {
                    stopListening(); message = word("speechFailed")
                }
            }
        }
    }

    fun stopListening() {
        app.speechOutput.clearCompletion()
        speech.stop()
        state.listening = false
    }

    fun handleSpeech(raw: String): Unit = with(app) {
        if (!canListen() || ride.state.handoffInProgress) return
        if (raw.isBlank()) {
            message = word("speechFailed"); return
        }
        if (screen == AppScreen.DestinationSearch) {
            destination.handleSearchSpeech(raw); return
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
        resolveDestination(raw)
    }

    fun resolveDestination(raw: String, queryIsExtracted: Boolean = false): Unit = with(app) {
        val matched = DestinationResolver.matches(raw, places)
        when (matched.size) {
            0 -> destination.beginDestinationSearch(raw, extractPhrase = !queryIsExtracted)
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
