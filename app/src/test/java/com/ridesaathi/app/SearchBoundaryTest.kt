package com.ridesaathi.app

import org.junit.Assert.*
import org.junit.Test

class SearchBoundaryTest {
    private val hyderabad = PlaceCandidate("Hyderabad", 17.385, 78.4867)

    @Test fun keepsHyderabadOutskirtsAndAirportButExcludesOtherCities() {
        val local = listOf(
            PlaceCandidate("Secunderabad", 17.4399, 78.4983),
            PlaceCandidate("Airport", 17.2403, 78.4294),
            PlaceCandidate("Patancheru", 17.5333, 78.2645)
        )
        val distant = listOf(
            PlaceCandidate("Delhi", 28.6139, 77.2090),
            PlaceCandidate("Mumbai", 19.0760, 72.8777),
            PlaceCandidate("Warangal", 17.9689, 79.5941)
        )
        assertEquals(local, SearchBoundary.filter(hyderabad, distant + local))
        assertTrue(SearchBoundary.filter(hyderabad, distant).isEmpty())
    }

    @Test fun enforcesFiftyKilometersInEveryDirection() {
        // One degree of latitude on the sphere used by the boundary is about 111.2 km.
        val center = PlaceCandidate("", 0.0, 0.0)
        val inside = Math.toDegrees(49_999.0 / 6_371_000.0)
        val outside = Math.toDegrees(50_001.0 / 6_371_000.0)
        listOf(-1, 1).forEach { direction ->
            assertTrue(SearchBoundary.contains(center, PlaceCandidate("", direction * inside, 0.0)))
            assertTrue(SearchBoundary.contains(center, PlaceCandidate("", 0.0, direction * inside)))
            assertFalse(SearchBoundary.contains(center, PlaceCandidate("", direction * outside, 0.0)))
            assertFalse(SearchBoundary.contains(center, PlaceCandidate("", 0.0, direction * outside)))
        }
        assertTrue(SearchBoundary.contains(center, center))
        assertFalse(SearchBoundary.contains(center, PlaceCandidate("", inside, inside)))
    }

    @Test fun boundaryMovesWithUserAndRejectsInvalidCoordinates() {
        val delhi = PlaceCandidate("Delhi", 28.6139, 77.2090)
        assertTrue(SearchBoundary.contains(delhi, delhi))
        assertFalse(SearchBoundary.contains(delhi, hyderabad))
        listOf(PlaceCandidate("", Double.NaN, 0.0), PlaceCandidate("", 91.0, 0.0),
            PlaceCandidate("", 0.0, 181.0), PlaceCandidate("", 0.0, Double.POSITIVE_INFINITY)).forEach {
            assertFalse(SearchBoundary.contains(it, hyderabad))
            assertFalse(SearchBoundary.contains(hyderabad, it))
        }
    }

    @Test fun handlesAntimeridianAndPolesWithoutWideningBoundary() {
        assertTrue(SearchBoundary.contains(PlaceCandidate("", 0.0, 179.9), PlaceCandidate("", 0.0, -179.9)))
        assertTrue(SearchBoundary.contains(PlaceCandidate("", 89.9, 0.0), PlaceCandidate("", 89.9, 180.0)))
        assertFalse(SearchBoundary.contains(PlaceCandidate("", 0.0, 0.0), PlaceCandidate("", 0.0, 180.0)))
    }
}
