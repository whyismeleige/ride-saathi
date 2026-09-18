package com.ridesaathi.app

import android.net.Uri
import android.os.SystemClock
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class PlaceCandidate(val address: String, val latitude: Double, val longitude: Double)

interface PlaceSearchProvider {
    fun search(query: String, language: String): List<PlaceCandidate>
}

class NominatimPlaceSearchProvider(private val endpoint: String) : PlaceSearchProvider {
    override fun search(query: String, language: String): List<PlaceCandidate> {
        val cacheKey = "$endpoint|$language|${query.trim().lowercase(Locale.ROOT)}"
        synchronized(lock) {
            cache[cacheKey]?.let { return it }
            val wait = 1_000 - (SystemClock.elapsedRealtime() - lastRequestAt)
            if (wait > 0) Thread.sleep(wait)
            lastRequestAt = SystemClock.elapsedRealtime()
            return fetch(query, language).also { result ->
                if (cache.size >= 20) cache.remove(cache.keys.first())
                cache[cacheKey] = result
            }
        }
    }

    private fun fetch(query: String, language: String): List<PlaceCandidate> {
        val url = Uri.parse(endpoint).buildUpon()
            .appendQueryParameter("q", query)
            .appendQueryParameter("format", "jsonv2")
            .appendQueryParameter("limit", "5")
            .appendQueryParameter("accept-language", language)
            .build().toString()
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("User-Agent", "RideSaathi/0.1 (com.ridesaathi.app; prototype)")
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode != 200) throw java.io.IOException("Search HTTP ${connection.responseCode}")
            val results = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
            return (0 until results.length()).mapNotNull { index ->
                val item = results.getJSONObject(index)
                val address = item.optString("display_name").trim()
                val lat = item.optString("lat").toDoubleOrNull()
                val lon = item.optString("lon").toDoubleOrNull()
                if (address.isBlank() || lat == null || lon == null || !lat.isFinite() || !lon.isFinite() ||
                    lat !in -90.0..90.0 || lon !in -180.0..180.0) null
                else PlaceCandidate(address, lat, lon)
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        val lock = Any()
        val cache = linkedMapOf<String, List<PlaceCandidate>>()
        var lastRequestAt = 0L
    }
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
    const val SEARCH = "https://nominatim.openstreetmap.org/search"
    const val MAP = "https://www.openstreetmap.org/export/embed.html"

    fun valid(value: String): Boolean {
        val uri = Uri.parse(value)
        return uri.scheme == "https" && !uri.host.isNullOrBlank() && !uri.encodedAuthority.orEmpty().contains('@') &&
            uri.query.isNullOrBlank() && uri.fragment.isNullOrBlank()
    }
}
