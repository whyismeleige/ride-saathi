package com.ridesaathi.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilePersistenceTest {
    @Test fun olderProfilesDefaultTutorialFlagsToFalse() {
        val profile = LocalStore.parseProfile("""{"name":"Pilot","language":"hi","completed":true}""")

        assertEquals("Pilot", profile.name)
        assertEquals("hi", profile.language)
        assertTrue(profile.completed)
        assertFalse(profile.introSeen)
        assertFalse(profile.tutorialSeen)
    }

    @Test fun tutorialFlagsRoundTripThroughJson() {
        val original = Profile(
            name = "Pilot",
            language = "te",
            completed = true,
            introSeen = true,
            tutorialSeen = true
        )

        val parsed = LocalStore.parseProfile(LocalStore.serializeProfile(original))

        assertEquals(original, parsed)
    }
}
