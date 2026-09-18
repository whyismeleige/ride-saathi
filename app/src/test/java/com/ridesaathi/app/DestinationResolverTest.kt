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
}
