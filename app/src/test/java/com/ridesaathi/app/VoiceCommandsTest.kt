package com.ridesaathi.app

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceCommandsTest {
    @Test fun acceptsExplicitConfirmationInBothLanguages() {
        listOf("Yes, open Uber", "हाँ", "हां चलो").forEach {
            assertEquals(it, VoiceDecision.Yes, VoiceCommands.decision(it))
        }
    }

    @Test fun negativesAlwaysOverrideAffirmatives() {
        listOf("yes no", "हाँ नहीं", "हाँ मत खोलो", "no thanks", "not yes", "don't open Uber", "नही").forEach {
            assertEquals(it, VoiceDecision.No, VoiceCommands.decision(it))
        }
    }

    @Test fun neverConfirmsOrCancelsFromPartOfAWord() {
        listOf("yesterday", "eyes", "यहाँ", "यहां", "Noida", "Bandra", "बंदरा").forEach {
            assertEquals(it, VoiceDecision.Unknown, VoiceCommands.decision(it))
        }
    }

    @Test fun cancelsExplicitly() {
        listOf("cancel please", "stop", "रद्द करें", "बंद करो", "कैंसल", "रुको", "yes cancel").forEach {
            assertEquals(it, VoiceDecision.Cancel, VoiceCommands.decision(it))
        }
    }
}
