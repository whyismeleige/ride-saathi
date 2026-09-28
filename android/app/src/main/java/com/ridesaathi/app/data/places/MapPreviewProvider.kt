package com.ridesaathi.app.data.places

import android.net.Uri
import java.util.Locale

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
            .appendQueryParameter(
                "bbox",
                String.format(Locale.US, "%f,%f,%f,%f", west, south, east, north)
            )
            .appendQueryParameter("layer", "mapnik")
            .appendQueryParameter("marker", String.format(Locale.US, "%f,%f", latitude, longitude))
            .build().toString()
    }
}

object ProviderEndpoints {
    const val MAP = "https://www.openstreetmap.org/export/embed.html"

    fun valid(value: String): Boolean {
        val uri = Uri.parse(value)
        return uri.scheme == "https" && !uri.host.isNullOrBlank() && !uri.encodedAuthority.orEmpty()
            .contains('@') &&
                uri.query.isNullOrBlank() && uri.fragment.isNullOrBlank()
    }
}
