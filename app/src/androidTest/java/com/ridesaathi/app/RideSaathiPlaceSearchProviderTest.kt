package com.ridesaathi.app

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Contract fixtures for the Ride Saathi backend; no credentials or live requests. */
@RunWith(AndroidJUnit4::class)
class RideSaathiPlaceSearchProviderTest {
    private val current = PlaceCandidate("", 17.3, 78.4)
    private val place = """{"address":"Clinic, Hyderabad","latitude":17.4,"longitude":78.5}"""
    private val backendBase = "https://api.ridesaathi.example.com"
    private fun success(vararg places: String) = HttpResult(200, """{"places":[${places.joinToString(",")}]}""")
    private fun error(code: String, status: Int = 503) = HttpResult(status, """{"error":{"code":"$code","message":"temporarily unavailable"}}""")

    @Test fun requestGoesToRideSaathiBackendWithoutCredentials() {
        var request = ""
        val provider = RideSaathiPlaceSearchProvider("$backendBase/", current) {
            request = it
            success(place)
        }
        val results = provider.search("  अस्पताल & Clinic  ", "hi-IN")
        val uri = Uri.parse(request)
        assertEquals("https", uri.scheme)
        assertEquals("api.ridesaathi.example.com", uri.host)
        assertEquals("/v1/places/autocomplete", uri.path)
        assertEquals("अस्पताल & Clinic", uri.getQueryParameter("q"))
        assertEquals("hi", uri.getQueryParameter("language"))
        assertEquals("17.3", uri.getQueryParameter("lat"))
        assertEquals("78.4", uri.getQueryParameter("lng"))
        assertNull(uri.getQueryParameter("api_key"))
        assertFalse(request.contains("olamaps"))
        assertFalse(request.contains("api_key"))
        assertEquals(listOf(PlaceCandidate("Clinic, Hyderabad", 17.4, 78.5)), results)
    }

    @Test fun missingOrInvalidLocationNeverIssuesUnboundedRequests() {
        listOf(null, PlaceCandidate("", Double.NaN, 78.4), PlaceCandidate("", 91.0, 78.4)).forEach { center ->
            expectFailure(PlaceSearchFailure.LOCATION_REQUIRED) {
                RideSaathiPlaceSearchProvider(backendBase, center) { error("Network must not be called") }
                    .search("Clinic", "en")
            }
        }
    }

    @Test fun distantMatchesAreFilteredEvenWhenBackendDoesNotBiasToCurrentLocation() {
        val distant = """{"address":"Clinic, Delhi","latitude":28.61,"longitude":77.21}"""
        var requests = 0
        val provider = RideSaathiPlaceSearchProvider(backendBase, current) {
            requests++
            success("$distant,$place")
        }
        assertEquals(listOf(PlaceCandidate("Clinic, Hyderabad", 17.4, 78.5)), provider.search("Clinic", "te"))
        assertEquals(1, requests)
        assertTrue(RideSaathiPlaceSearchProvider(backendBase, current) { success(distant) }
            .search("Clinic, Delhi", "en").isEmpty())
    }

    @Test fun blankBaseUrlAndShortQueriesDoNotMakeRequests() {
        expectFailure(PlaceSearchFailure.NOT_CONFIGURED) {
            RideSaathiPlaceSearchProvider(" ") { error("Network must not be called") }.search("Clinic", "en")
        }
        assertTrue(RideSaathiPlaceSearchProvider(backendBase, current) { error("Network must not be called") }
            .search("ab", "en").isEmpty())
    }

    @Test fun zeroResultsIsEmpty() {
        assertTrue(RideSaathiPlaceSearchProvider(backendBase, current) { success() }
            .search("Unknown", "en").isEmpty())
    }

    @Test fun invalidEntriesAreNeverConvertedToZeroOrSaved() {
        val invalid = listOf(
            """{"address":"Missing coordinates"}""",
            """{"address":"Missing latitude","longitude":78}""",
            """{"address":"Out of range","latitude":91,"longitude":78}""",
            """{"address":"Not finite","latitude":"NaN","longitude":78}""",
            """{"address":" ","latitude":17,"longitude":78}"""
        )
        val results = RideSaathiPlaceSearchProvider(backendBase, current) {
            success((invalid + place + place).joinToString(","))
        }.search("Clinic", "en")
        assertEquals(listOf(PlaceCandidate("Clinic, Hyderabad", 17.4, 78.5)), results)
        expectFailure(PlaceSearchFailure.INVALID_RESPONSE) {
            RideSaathiPlaceSearchProvider(backendBase, current) { success(invalid.joinToString(",")) }
                .search("Clinic", "en")
        }
    }

    @Test fun malformedAndUnparseableResponsesAreNotReportedAsNoMatches() {
        for (body in listOf("not json", """{"status":"ok"}""")) {
            expectFailure(PlaceSearchFailure.INVALID_RESPONSE) {
                RideSaathiPlaceSearchProvider(backendBase, current) { HttpResult(200, body) }
                    .search("Clinic", "en")
            }
        }
    }

    @Test fun backendErrorCodesKeepTheirDistinctFailures() {
        for ((code, reason) in listOf(
            "NOT_CONFIGURED" to PlaceSearchFailure.NOT_CONFIGURED,
            "ACCESS_DENIED" to PlaceSearchFailure.ACCESS_DENIED,
            "QUOTA" to PlaceSearchFailure.QUOTA,
            "UNAVAILABLE" to PlaceSearchFailure.UNAVAILABLE,
            "INVALID_RESPONSE" to PlaceSearchFailure.INVALID_RESPONSE
        )) {
            expectFailure(reason) {
                RideSaathiPlaceSearchProvider(backendBase, current) { error(code) }.search("Clinic", "en")
            }
        }
    }

    @Test fun httpStatusFallbackMapsWithoutAnErrorBody() {
        for ((status, reason) in listOf(
            401 to PlaceSearchFailure.ACCESS_DENIED,
            403 to PlaceSearchFailure.ACCESS_DENIED,
            429 to PlaceSearchFailure.QUOTA,
            400 to PlaceSearchFailure.UNAVAILABLE,
            502 to PlaceSearchFailure.UNAVAILABLE,
            504 to PlaceSearchFailure.UNAVAILABLE
        )) {
            expectFailure(reason) {
                RideSaathiPlaceSearchProvider(backendBase, current) { HttpResult(status, "") }
                    .search("Clinic", "en")
            }
        }
    }

    @Test fun failuresNeverLeakCredentialsUrlsOrQueries() {
        val secrets = listOf("api.ridesaathi.example.com", "Clinic", "api_key", "secret-key")
        val scenarios = listOf(
            RideSaathiPlaceSearchProvider(backendBase, current) { HttpResult(401, "denied") },
            RideSaathiPlaceSearchProvider(backendBase, current) { HttpResult(429, "too many") },
            RideSaathiPlaceSearchProvider(backendBase, current) { throw java.net.SocketTimeoutException() },
            RideSaathiPlaceSearchProvider(backendBase, current) { HttpResult(200, "not json") },
            RideSaathiPlaceSearchProvider("", current)
        )
        scenarios.forEach { provider ->
            try {
                provider.search("Clinic", "en")
                fail("Expected a failure")
            } catch (error: PlaceSearchException) {
                secrets.forEach { secret -> assertFalse(error.message.orEmpty().contains(secret)) }
            } catch (_: Exception) {
                // SocketTimeoutException propagates as a plain IOException, matching the old app.
            }
        }
    }

    private fun expectFailure(reason: PlaceSearchFailure, action: () -> Unit) {
        try { action(); fail("Expected $reason") }
        catch (error: PlaceSearchException) { assertEquals(reason, error.reason) }
    }
}