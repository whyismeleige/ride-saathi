package com.ridesaathi.app

import org.junit.Assert.*
import org.junit.Test

class DestinationSearchTest {
    private val places = listOf(
        PlaceCandidate("Apollo Hospital, Jubilee Hills, Hyderabad", 17.4, 78.4),
        PlaceCandidate("Apollo Hospital, Secunderabad", 17.5, 78.5),
        PlaceCandidate("Central bus stop, Hyderabad", 17.3, 78.3)
    )

    @Test fun extractsEnglishTravelPhrasesAndPreservesCityAndAddress() {
        assertEquals("Apollo Hospital, Chennai", DestinationQuery.extract("Please take me to Apollo Hospital, Chennai."))
        assertEquals("St. Mary's Hospital", DestinationQuery.extract("I want to go to St. Mary's Hospital please"))
        assertEquals("Central bus stop", DestinationQuery.extract("Central bus stop"))
        assertEquals("Go To Market Road", DestinationQuery.extract("take me to Go To Market Road"))
    }

    @Test fun extractsHindiAndRomanHindiWithoutRemovingInternalWords() {
        assertEquals("अपोलो अस्पताल हैदराबाद", DestinationQuery.extract("मुझे अपोलो अस्पताल हैदराबाद जाना है"))
        assertEquals("Apollo Hospital", DestinationQuery.extract("Apollo Hospital jaana hai"))
        assertEquals("Apollo Hospital", DestinationQuery.extract("mujhe Apollo Hospital ke paas jaana hai"))
        assertEquals("Apollo Hospital", DestinationQuery.extract("Apollo Hospital ke paas jana hai"))
        assertEquals("राम की दुकान", DestinationQuery.extract("राम की दुकान के पास जाना है"))
    }

    @Test fun emptyOrIncompleteTravelRequestsNeedClarification() {
        listOf("", "!!", "take me to", "मुझे जाना है", "yes", "हाँ", "go", "I want to go to", "to the").forEach {
            assertNull(it, DestinationQuery.extract(it))
        }
    }

    @Test fun acceptsEnglishHindiAndNumericChoices() {
        listOf("two", "second one", "option two", "number 2", "2", "२", "दूसरा", "दूसरी", "दूसरा वाला", "doosra").forEach {
            assertEquals(it, SearchChoice.Select(1), DestinationChoices.parse(it, places))
        }
        assertEquals(SearchChoice.Select(0), DestinationChoices.parse("पहला", places))
        assertEquals(SearchChoice.Select(2), DestinationChoices.parse("three", places))
    }

    @Test fun neverGuessesAmbiguousNamesNumbersOrNegatedChoices() {
        listOf("Apollo Hospital", "Apollo", "first or second", "not first", "4", "yes", "नहीं दूसरा").forEach {
            assertEquals(it, SearchChoice.Unknown, DestinationChoices.parse(it, places))
        }
        assertEquals(SearchChoice.Unknown, DestinationChoices.parse("three", places.take(2)))
        assertEquals(SearchChoice.Unknown, DestinationChoices.parse("one", emptyList()))
    }

    @Test fun selectsUniqueFullNamesAndAddressesIncludingBusStop() {
        assertEquals(SearchChoice.Select(2), DestinationChoices.parse("Central bus stop", places))
        assertEquals(SearchChoice.Select(1), DestinationChoices.parse("Apollo Hospital, Secunderabad", places))
    }

    @Test fun recognizesOnlyExplicitResultCommandsInBothLanguages() {
        listOf("more results", "और नतीजे").forEach { assertEquals(SearchChoice.More, DestinationChoices.parse(it, places)) }
        listOf("previous results", "पिछले नतीजे").forEach { assertEquals(SearchChoice.Previous, DestinationChoices.parse(it, places)) }
        listOf("search again", "फिर खोजें").forEach { assertEquals(SearchChoice.Again, DestinationChoices.parse(it, places)) }
        listOf("repeat options", "फिर सुनाएँ").forEach { assertEquals(SearchChoice.Repeat, DestinationChoices.parse(it, places)) }
        listOf("cancel", "रद्द करें").forEach { assertEquals(SearchChoice.Cancel, DestinationChoices.parse(it, places)) }
    }

    @Test fun paginatesInProviderOrderAndRenumbersEachGroup() {
        val state = DestinationSearchState(candidates = places + PlaceCandidate("Fourth", 1.0, 2.0))
        assertEquals(places, state.visible)
        assertTrue(state.hasMore)
        val next = state.copy(page = 1)
        assertEquals("Fourth", next.visible.single().address)
        assertFalse(next.hasMore)
        assertEquals(SearchChoice.Select(0), DestinationChoices.parse("first", next.visible))
    }
}
