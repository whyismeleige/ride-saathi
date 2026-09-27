package com.ridesaathi.app

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

data class SharedLocation(val latitude: Double, val longitude: Double, val label: String? = null)

/** Extracts destination coordinates; shortened links are expanded by SharedLocationResolver. */
object SharedLocationParser {
    private const val number = "[+-]?\\d+(?:\\.\\d+)?"
    private const val pair = "($number)\\s*,\\s*($number)"
    private val coordinate = Regex("^\\s*$pair(?:\\s*\\((.*)\\))?\\s*$")
    private val url = Regex("(?:https?://|geo:)[^\\s<>]+", RegexOption.IGNORE_CASE)
    private val pathCoordinates = Regex("/@$pair(?=[,/]|$)")
    private val encodedCoordinates = Regex("!3d($number)!4d($number)(?=[!/?&#]|$)")
    private val mapsHosts = setOf("maps.google.com", "maps.google.co.in")
    private val googleHosts = setOf("www.google.com", "google.com", "www.google.co.in", "google.co.in")

    fun parse(sharedText: String): SharedLocation? =
        supportedUris(sharedText).firstNotNullOfOrNull(::parseUri)

    internal fun supportedUris(sharedText: String): List<String> = url.findAll(sharedText)
        .map { match ->
            var value = match.value.trimEnd('.', ',', ']', '}')
            // Keep a geo query's label parentheses, but remove surrounding prose punctuation.
            while (value.endsWith(')') && value.count { it == ')' } > value.count { it == '(' })
                value = value.dropLast(1)
            value
        }.filter { value ->
            try { URI(value).scheme.equals("geo", true) || isSupportedNetworkUrl(value) }
            catch (_: Exception) { false }
        }.distinct().toList()

    internal fun isSupportedNetworkUrl(value: String): Boolean = try {
        val uri = URI(value)
        val host = uri.host?.lowercase(Locale.US)
        uri.scheme.lowercase(Locale.US) in setOf("http", "https") &&
            uri.rawUserInfo == null && uri.port == -1 && when (host) {
                "maps.app.goo.gl" -> uri.path.orEmpty().length > 1
                "goo.gl" -> uri.path.orEmpty().startsWith("/maps/")
                in mapsHosts -> true
                in googleHosts -> uri.path == "/maps" || uri.path.orEmpty().startsWith("/maps/")
                else -> false
            }
    } catch (_: Exception) { false }

    fun formattedCoordinates(location: SharedLocation): String =
        String.format(Locale.US, "%.6f, %.6f", location.latitude, location.longitude)

    internal fun addressQuery(value: String): String? = try {
        val uri = URI(value)
        val params = queryParameters(uri.rawQuery)
        val placeName = uri.rawPath.orEmpty().substringAfter("/maps/place/", "")
            .substringBefore('/').takeIf { it.isNotBlank() }?.let(::decode)
        (placeName ?: params["destination"] ?: params["daddr"] ?: params["q"] ?: params["query"])
            ?.trim()?.takeIf {
                it.length in 3..500 && !it.startsWith("place_id:", true) &&
                    !it.startsWith("@") && !it.startsWith("data=") &&
                    !Regex("^[+\\-\\d.,\\s]+$").matches(it)
            }
    } catch (_: Exception) { null }

    private fun parseUri(value: String): SharedLocation? = try {
        val uri = URI(value)
        val rawQuery = uri.rawQuery ?: uri.rawSchemeSpecificPart
            .substringAfter('?', missingDelimiterValue = "")
        val params = queryParameters(rawQuery)
        val candidates = listOfNotNull(params["destination"], params["daddr"], params["q"], params["query"])
        candidates.firstNotNullOfOrNull(::parseCoordinateText)
            // The place's pin is more precise than /@lat,lng (the viewport center).
            ?: encodedCoordinates.find(uri.path.orEmpty())?.let(::locationFromMatch)
            ?: if (uri.scheme.equals("geo", true)) {
                parseCoordinateText(uri.schemeSpecificPart.substringBefore('?').substringBefore(';'))
            } else {
                pathCoordinates.find(uri.path.orEmpty())?.let(::locationFromMatch)
            }
    } catch (_: Exception) { null }

    private fun parseCoordinateText(value: String): SharedLocation? {
        val match = coordinate.matchEntire(value) ?: return null
        return locationFromMatch(match)?.copy(label = match.groupValues[3].trim().takeIf { it.isNotBlank() })
    }

    private fun locationFromMatch(match: MatchResult): SharedLocation? {
        val latitude = match.groupValues[1].toDoubleOrNull() ?: return null
        val longitude = match.groupValues[2].toDoubleOrNull() ?: return null
        return if (latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
            !(latitude == 0.0 && longitude == 0.0)) SharedLocation(latitude, longitude) else null
    }

    private fun queryParameters(rawQuery: String?): Map<String, String> = rawQuery.orEmpty()
        .split('&').mapNotNull { part ->
            val pieces = part.split('=', limit = 2)
            if (pieces.size != 2) null else decode(pieces[0]).lowercase(Locale.US) to decode(pieces[1])
        }.toMap()

    private fun decode(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
}
