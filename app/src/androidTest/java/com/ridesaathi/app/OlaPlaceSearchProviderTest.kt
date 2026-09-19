package com.ridesaathi.app

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Contract fixtures from Ola's public OpenAPI schema; no credentials or live requests. */
@RunWith(AndroidJUnit4::class)
class OlaPlaceSearchProviderTest {
    private val prediction = """{"description":"Clinic, Hyderabad","geometry":{"location":{"lat":17.4,"lng":78.5}},"place_id":"ola-platform:clinic"}"""
    private fun response(items: String = prediction) = """{"status":"ok","predictions":[$items],"error_message":""}"""

    @Test fun requestEncodesInputUsesLanguageCodeAndBiasesAroundHome() {
        var request = ""
        val provider = OlaPlaceSearchProvider("test key&", PlaceCandidate("Home", 17.3, 78.4)) {
            request = it
            response()
        }
        val results = provider.search("  अस्पताल & Clinic  ", "hi-IN")
        val uri = Uri.parse(request)
        assertEquals("https", uri.scheme)
        assertEquals("api.olamaps.io", uri.host)
        assertEquals("/places/v1/autocomplete", uri.path)
        assertEquals("अस्पताल & Clinic", uri.getQueryParameter("input"))
        assertEquals("hi", uri.getQueryParameter("language"))
        assertEquals("test key&", uri.getQueryParameter("api_key"))
        assertEquals("17.3,78.4", uri.getQueryParameter("location"))
        assertEquals("false", uri.getQueryParameter("strictbounds"))
        assertEquals(listOf(PlaceCandidate("Clinic, Hyderabad", 17.4, 78.5)), results)
    }

    @Test fun firstHomeSearchDoesNotInventLocationOrIssueDetailsCalls() {
        var count = 0
        val provider = OlaPlaceSearchProvider("test") {
            count++
            assertNull(Uri.parse(it).getQueryParameter("location"))
            response()
        }
        provider.search("Delhi", "en-IN")
        assertEquals(1, count)
    }

    @Test fun missingKeyAndShortQueriesDoNotMakeRequests() {
        expectFailure(PlaceSearchFailure.NOT_CONFIGURED) {
            OlaPlaceSearchProvider(" ") { error("Network must not be called") }.search("Clinic", "en")
        }
        assertTrue(OlaPlaceSearchProvider("test") { error("Network must not be called") }
            .search("ab", "en").isEmpty())
    }

    @Test fun documentedZeroResultsIsEmpty() {
        assertTrue(OlaPlaceSearchProvider("test") {
            """{"status":"ok","error_message":"ZERO RESULTS","predictions":[]}"""
        }.search("Unknown", "en").isEmpty())
    }

    @Test fun invalidCoordinatesAreNeverConvertedToZeroOrSaved() {
        val invalid = listOf(
            """{"description":"Missing geometry"}""",
            """{"description":"Missing latitude","geometry":{"location":{"lng":78}}}""",
            """{"description":"Out of range","geometry":{"location":{"lat":91,"lng":78}}}""",
            """{"description":"Not finite","geometry":{"location":{"lat":"NaN","lng":78}}}""",
            """{"description":" ","geometry":{"location":{"lat":17,"lng":78}}}"""
        )
        val results = OlaPlaceSearchProvider("test") {
            response((invalid + prediction + prediction).joinToString(","))
        }.search("Clinic", "en")
        assertEquals(listOf(PlaceCandidate("Clinic, Hyderabad", 17.4, 78.5)), results)
        expectFailure(PlaceSearchFailure.INVALID_RESPONSE) {
            OlaPlaceSearchProvider("test") { response(invalid.joinToString(",")) }.search("Clinic", "en")
        }
    }

    @Test fun malformedAndErrorResponsesAreNotReportedAsNoMatches() {
        for (body in listOf("not json", """{"status":"ok"}""")) {
            expectFailure(PlaceSearchFailure.INVALID_RESPONSE) {
                OlaPlaceSearchProvider("test") { body }.search("Clinic", "en")
            }
        }
        for ((status, reason) in listOf("over_query_limit" to PlaceSearchFailure.QUOTA,
            "request_denied" to PlaceSearchFailure.ACCESS_DENIED, "error" to PlaceSearchFailure.UNAVAILABLE)) {
            expectFailure(reason) {
                OlaPlaceSearchProvider("test") { """{"status":"$status","predictions":[]}""" }
                    .search("Clinic", "en")
            }
        }
    }

    private fun expectFailure(reason: PlaceSearchFailure, action: () -> Unit) {
        try { action(); fail("Expected $reason") }
        catch (error: PlaceSearchException) { assertEquals(reason, error.reason) }
    }
}
