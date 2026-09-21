package com.ridesaathi.app

import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class PlaceCandidate(val address: String, val latitude: Double, val longitude: Double)

interface PlaceSearchProvider {
    fun search(query: String, language: String): List<PlaceCandidate>
}

enum class PlaceSearchFailure { NOT_CONFIGURED, ACCESS_DENIED, QUOTA, UNAVAILABLE, INVALID_RESPONSE, LOCATION_REQUIRED }

class PlaceSearchException(val reason: PlaceSearchFailure) : java.io.IOException(reason.name)

/** No URLs, API keys, response bodies, or user queries are included in failures. */
internal fun olaGet(url: String): String {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        // Never forward a credential-bearing URL through a redirect.
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("X-Request-Id", java.util.UUID.randomUUID().toString())
        when (connection.responseCode) {
            200 -> return connection.inputStream.bufferedReader().use { it.readText() }
            401, 403 -> throw PlaceSearchException(PlaceSearchFailure.ACCESS_DENIED)
            429 -> throw PlaceSearchException(PlaceSearchFailure.QUOTA)
            else -> throw PlaceSearchException(PlaceSearchFailure.UNAVAILABLE)
        }
    } finally {
        connection.disconnect()
    }
}

/** Ola autocomplete includes geometry; no per-suggestion Details requests are needed. */
class OlaPlaceSearchProvider(
    private val apiKey: String,
    private val center: PlaceCandidate? = null,
    private val get: (String) -> String = ::olaGet
) : PlaceSearchProvider {
    override fun search(query: String, language: String): List<PlaceCandidate> {
        if (apiKey.isBlank()) throw PlaceSearchException(PlaceSearchFailure.NOT_CONFIGURED)
        if (Thread.currentThread().isInterrupted) throw InterruptedException("Search cancelled")
        if (query.trim().length < 3) return emptyList()
        val origin = center?.takeIf(SearchBoundary::valid)
            ?: throw PlaceSearchException(PlaceSearchFailure.LOCATION_REQUIRED)
        val url = Uri.parse(ProviderEndpoints.SEARCH).buildUpon()
            .appendQueryParameter("input", query.trim())
            .appendQueryParameter("language", language.substringBefore('-').lowercase(Locale.ROOT))
            .appendQueryParameter("api_key", apiKey.trim())
        url.appendQueryParameter("location", "${origin.latitude},${origin.longitude}")
        url.appendQueryParameter("radius", SearchBoundary.RADIUS_METERS.toString())
        url.appendQueryParameter("strictbounds", "true")
        val body = get(url.build().toString())
        if (Thread.currentThread().isInterrupted) throw InterruptedException("Search cancelled")
        try {
            val response = org.json.JSONObject(body)
            when (response.optString("status").lowercase(Locale.ROOT)) {
                "ok", "zero_results" -> Unit
                "over_query_limit" -> throw PlaceSearchException(PlaceSearchFailure.QUOTA)
                "request_denied" -> throw PlaceSearchException(PlaceSearchFailure.ACCESS_DENIED)
                else -> throw PlaceSearchException(PlaceSearchFailure.UNAVAILABLE)
            }
            val results = response.optJSONArray("predictions")
                ?: throw PlaceSearchException(PlaceSearchFailure.INVALID_RESPONSE)
            val candidates = (0 until results.length()).mapNotNull { index ->
                val item = results.optJSONObject(index) ?: return@mapNotNull null
                val address = item.optString("description").trim()
                val location = item.optJSONObject("geometry")?.optJSONObject("location")
                    ?: return@mapNotNull null
                val lat = location.optDouble("lat", Double.NaN)
                val lon = location.optDouble("lng", Double.NaN)
                if (address.isBlank() || address == "null" || !validCoordinates(lat, lon)) null
                else PlaceCandidate(address, lat, lon)
            }.distinct()
            if (results.length() > 0 && candidates.isEmpty())
                throw PlaceSearchException(PlaceSearchFailure.INVALID_RESPONSE)
            // Enforce the boundary locally even if the service returns out-of-area suggestions.
            return SearchBoundary.filter(origin, candidates)
        } catch (_: org.json.JSONException) {
            throw PlaceSearchException(PlaceSearchFailure.INVALID_RESPONSE)
        }
    }

    private fun validCoordinates(lat: Double, lon: Double) =
        lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0
}

interface MapPreviewProvider {
    fun url(latitude: Double, longitude: Double): String
}

class OpenStreetMapPreviewProvider(private val endpoint: String) : MapPreviewProvider {
    override fun url(latitude: Double, longitude: Double): String {
        val west = (longitude - 0.004).coerceAtLeast(-180.0)
        val east = (longitude + 0.004).coerceAtMost(180.0)
        val south = (latitude - 0.003).coerceAtLeast(-90.0)
        val north = (latitude + 0.003).coerceAtMost(90.0)
        return Uri.parse(endpoint).buildUpon()
            .appendQueryParameter("bbox", String.format(Locale.US, "%f,%f,%f,%f", west, south, east, north))
            .appendQueryParameter("layer", "mapnik")
            .appendQueryParameter("marker", String.format(Locale.US, "%f,%f", latitude, longitude))
            .build().toString()
    }
}

object ProviderEndpoints {
    const val SEARCH = "https://api.olamaps.io/places/v1/autocomplete"
    const val MAP = "https://www.openstreetmap.org/export/embed.html"

    fun valid(value: String): Boolean {
        val uri = Uri.parse(value)
        return uri.scheme == "https" && !uri.host.isNullOrBlank() && !uri.encodedAuthority.orEmpty().contains('@') &&
            uri.query.isNullOrBlank() && uri.fragment.isNullOrBlank()
    }
}
