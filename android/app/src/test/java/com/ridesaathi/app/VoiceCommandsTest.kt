package com.ridesaathi.app

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceCommandsTest {
    @Test fun acceptsExplicitConfirmationInAllLanguages() {
        listOf("Yes, open Uber", "हाँ", "हां चलो", "అవును, Uber తెరువు").forEach {
            assertEquals(it, VoiceDecision.Yes, VoiceCommands.decision(it))
        }
    }

    @Test fun negativesAlwaysOverrideAffirmatives() {
        listOf("yes no", "हाँ नहीं", "हाँ मत खोलो", "no thanks", "not yes", "don't open Uber", "नही", "అవును కాదు", "వద్దు").forEach {
            assertEquals(it, VoiceDecision.No, VoiceCommands.decision(it))
        }
    }

    @Test fun neverConfirmsOrCancelsFromPartOfAWord() {
        listOf("yesterday", "eyes", "यहाँ", "यहां", "Noida", "Bandra", "बंदरा").forEach {
            assertEquals(it, VoiceDecision.Unknown, VoiceCommands.decision(it))
        }
    }

    @Test fun cancelsExplicitly() {
        listOf("cancel please", "stop", "रद्द करें", "बंद करो", "कैंसल", "रुको", "yes cancel", "రద్దు చేయండి", "ఆపండి").forEach {
            assertEquals(it, VoiceDecision.Cancel, VoiceCommands.decision(it))
        }
    }
}
