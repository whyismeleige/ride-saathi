package com.ridesaathi.app.data.places

import android.net.Uri
import com.ridesaathi.app.domain.model.PlaceCandidate
import com.ridesaathi.app.domain.search.PlaceSearchException
import com.ridesaathi.app.domain.search.PlaceSearchFailure
import com.ridesaathi.app.domain.search.PlaceSearchProvider
import com.ridesaathi.app.domain.search.SearchBoundary
import java.util.Locale

/**
 * Talks only to the Ride Saathi backend, which holds the upstream provider
 * credentials. The APK never learns which upstream provider the server uses.
 */
class RideSaathiPlaceSearchProvider(
    private val baseUrl: String,
    private val center: PlaceCandidate? = null,
    private val get: (String) -> HttpResult = ::httpGet
) : PlaceSearchProvider {
    override fun search(query: String, language: String): List<PlaceCandidate> {
        val base = baseUrl.trim().trimEnd('/')
        if (base.isEmpty()) throw PlaceSearchException(PlaceSearchFailure.NOT_CONFIGURED)
        if (Thread.currentThread().isInterrupted) throw InterruptedException("Search cancelled")
        val trimmed = query.trim()
        if (trimmed.length < 3) return emptyList()
        val origin = center?.takeIf(SearchBoundary::valid)
            ?: throw PlaceSearchException(PlaceSearchFailure.LOCATION_REQUIRED)
        val url = Uri.parse("$base/v1/places/autocomplete").buildUpon()
            .appendQueryParameter("q", trimmed)
            .appendQueryParameter("language", language.substringBefore('-').lowercase(Locale.ROOT))
            .appendQueryParameter("lat", origin.latitude.toString())
            .appendQueryParameter("lng", origin.longitude.toString())
            .build().toString()
        val result = get(url)
        if (Thread.currentThread().isInterrupted) throw InterruptedException("Search cancelled")
        return interpret(result)
    }

    private fun interpret(result: HttpResult): List<PlaceCandidate> {
        if (result.statusCode !in 200..299) {
            throw PlaceSearchException(reasonForFailure(result))
        }
        return try {
            val response = org.json.JSONObject(result.body)
            val places = response.optJSONArray("places")
                ?: throw PlaceSearchException(PlaceSearchFailure.INVALID_RESPONSE)
            val candidates = (0 until places.length()).mapNotNull { index ->
                val item = places.optJSONObject(index) ?: return@mapNotNull null
                val address = item.optString("address").trim()
                val latitude = item.optDouble("latitude", Double.NaN)
                val longitude = item.optDouble("longitude", Double.NaN)
                if (address.isBlank() || address == "null" || !validCoordinates(
                        latitude,
                        longitude
                    )
                ) null
                else PlaceCandidate(address, latitude, longitude)
            }.distinct()
            if (places.length() > 0 && candidates.isEmpty())
                throw PlaceSearchException(PlaceSearchFailure.INVALID_RESPONSE)
            // Enforce the boundary locally even if the backend returns out-of-area suggestions.
            SearchBoundary.filter(requireNotNull(center), candidates)
        } catch (_: org.json.JSONException) {
            throw PlaceSearchException(PlaceSearchFailure.INVALID_RESPONSE)
        }
    }

    private fun reasonForFailure(result: HttpResult): PlaceSearchFailure {
        val code = try {
            org.json.JSONObject(result.body).optJSONObject("error")?.optString("code")
        } catch (_: org.json.JSONException) {
            null
        }
        return when (code) {
            "NOT_CONFIGURED" -> PlaceSearchFailure.NOT_CONFIGURED
            "ACCESS_DENIED" -> PlaceSearchFailure.ACCESS_DENIED
            "QUOTA" -> PlaceSearchFailure.QUOTA
            "UNAVAILABLE" -> PlaceSearchFailure.UNAVAILABLE
            "INVALID_RESPONSE" -> PlaceSearchFailure.INVALID_RESPONSE
            else -> when (result.statusCode) {
                in 401..403 -> PlaceSearchFailure.ACCESS_DENIED
                429 -> PlaceSearchFailure.QUOTA
                else -> PlaceSearchFailure.UNAVAILABLE
            }
        }
    }

    private fun validCoordinates(lat: Double, lon: Double) =
        lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0
}
