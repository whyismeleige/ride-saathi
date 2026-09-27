package com.ridesaathi.app

import org.junit.Assert.assertEquals
import org.junit.Test

class DestinationResolverTest {
    private val home = SavedPlace(name = "Home", aliases = emptyList(), address = "Home address",
        latitude = 17.0, longitude = 78.0, isHome = true)
    private val doctor = SavedPlace(name = "Doctor", aliases = listOf("hospital", "अस्पताल", "ఆసుపత్రి"),
        address = "Clinic address", latitude = 17.1, longitude = 78.1)

    @Test fun resolvesSavedNamesAndAliasesAcrossLanguages() {
        assertEquals(listOf(doctor), DestinationResolver.matches("hospital jaana hai", listOf(home, doctor)))
        assertEquals(listOf(doctor), DestinationResolver.matches("अस्पताल जाना है", listOf(home, doctor)))
        assertEquals(listOf(doctor), DestinationResolver.matches("ఆసుపత్రి వెళ్లాలి", listOf(home, doctor)))
        assertEquals(listOf(home), DestinationResolver.matches("घर", listOf(home, doctor)))
    }

    @Test fun neverGuessesAnUnsavedDestination() {
        assertEquals(emptyList<SavedPlace>(), DestinationResolver.matches("airport", listOf(home, doctor)))
    }

    @Test fun leavesAmbiguityForTheUser() {
        val other = doctor.copy(id = "other", name = "Clinic", aliases = listOf("pharmacy"))
        assertEquals(2, DestinationResolver.matches("doctor and pharmacy", listOf(doctor, other)).size)
    }

    @Test fun englishNamesMatchHindiTranscriptsWithoutManualAliases() {
        val examples = mapOf(
            "Doctor" to "मुझे डॉक्टर के पास जाना है", "Hospital" to "अस्पताल जाना है",
            "Temple" to "मंदिर जाना है", "Ramesh" to "रमेश के पास जाना है",
            "Ramesh House" to "रमेश के घर जाना है", "Deepak" to "दीपक",
            "Sharma Clinic" to "शर्मा क्लिनिक जाना है", "Apollo" to "अपोलो",
            "Rajiv" to "राजीव", "Bank" to "बैंक", "Sanjay" to "संजय",
            "Market" to "बाज़ार", "Office" to "ऑफिस"
        )
        examples.forEach { (name, speech) ->
            val place = doctor.copy(name = name, aliases = emptyList())
            assertEquals("$name / $speech", listOf(place), DestinationResolver.matches(speech, listOf(home, place)))
        }
    }

    @Test fun hindiNamesAlsoMatchEnglishAndMixedScriptSpeech() {
        val place = doctor.copy(name = "रमेश क्लिनिक", aliases = emptyList())
        assertEquals(listOf(place), DestinationResolver.matches("Ramesh clinic please", listOf(home, place)))
        assertEquals(listOf(place), DestinationResolver.matches("रमेश clinic जाना है", listOf(home, place)))
    }

    @Test fun respectsWordBoundariesAndPreservesExplicitVowels() {
        val ram = doctor.copy(name = "Ram", aliases = listOf("Park"))
        listOf("program", "parking", "homework", "रोम", "आराम").forEach { speech ->
            assertEquals(speech, emptyList<SavedPlace>(), DestinationResolver.matches(speech, listOf(home, ram)))
        }
    }

    @Test fun normalizesCasePunctuationAndWhitespace() {
        val place = doctor.copy(name = "Ramesh House", aliases = emptyList())
        assertEquals(listOf(place), DestinationResolver.matches("RAMESH’S   HOUSE, please!", listOf(place)))
        assertEquals(listOf(doctor), DestinationResolver.matches("Doctor!", listOf(doctor)))
        assertEquals(emptyList<SavedPlace>(), DestinationResolver.matches(" ... ", listOf(home, doctor)))
    }

    @Test fun returnsEveryCrossScriptCandidateInsteadOfGuessing() {
        val other = doctor.copy(id = "other", name = "डॉक्टर", aliases = emptyList())
        assertEquals(listOf(doctor, other), DestinationResolver.matches("डॉक्टर", listOf(doctor, other)))
    }

    @Test fun validatesCrossScriptDuplicatesIncludingReservedHomeNames() {
        val duplicate = doctor.copy(id = "new", name = "डॉक्टर", aliases = emptyList())
        assertEquals(true, DestinationResolver.conflicts(duplicate, listOf(home, doctor)))
        assertEquals(false, DestinationResolver.conflicts(doctor, listOf(home, doctor)))
        assertEquals(true, DestinationResolver.conflicts(duplicate.copy(name = "घर"), listOf(home)))
        assertEquals(false, DestinationResolver.conflicts(duplicate.copy(name = "Dentist"), listOf(home, doctor)))
    }

    @Test fun customTranslationsRemainExplicitAliases() {
        val place = doctor.copy(name = "Son's house", aliases = listOf("बेटे का घर"))
        assertEquals(listOf(place), DestinationResolver.matches("बेटे का घर", listOf(place)))
    }
    @Test fun prefersBetaKaGharOverGharInEitherScriptRegardlessOfSavedOrder() {
        val ghar = home.copy(name = "ghar")
        val son = doctor.copy(name = "beta ka ghar", aliases = emptyList())
        val orders = listOf(listOf(ghar, son), listOf(son, ghar))
        orders.forEach { places ->
            listOf("beta ka ghar", "mujhe beta ke ghar jana hai", "बेटा का घर",
                "मुझे बेटे के घर जाना है", "beta के घर", "बेटे ka ghar").forEach { speech ->
                assertEquals(speech, listOf(son), DestinationResolver.matches(speech, places))
            }
            listOf("ghar", "घर जाना है").forEach { speech ->
                assertEquals(speech, listOf(ghar), DestinationResolver.matches(speech, places))
            }
        }
    }

    @Test fun prefersSpecificAliasAndPreservesDistinctMentions() {
        val son = doctor.copy(name = "Son's House", aliases = listOf("beta ka ghar"))
        assertEquals(listOf(son), DestinationResolver.matches("बेटे के घर जाना है", listOf(home, son)))
        assertEquals(setOf(home, son), DestinationResolver.matches("घर या बेटे के घर", listOf(home, son)).toSet())
        assertEquals(false, DestinationResolver.conflicts(son, listOf(home)))
    }

    @Test fun equalSpecificMatchesStillNeedClarification() {
        val son = doctor.copy(name = "beta ka ghar", aliases = emptyList())
        val other = son.copy(id = "other", name = "बेटे का घर")
        assertEquals(listOf(son, other), DestinationResolver.matches("बेटे के घर", listOf(home, son, other)))
    }

}
