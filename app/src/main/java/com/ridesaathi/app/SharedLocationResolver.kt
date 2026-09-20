package com.ridesaathi.app

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

sealed interface SharedDestination {
    data class Coordinates(val location: SharedLocation) : SharedDestination
    data class Address(val query: String, val sharedAddress: String) : SharedDestination
}

/** Only redirect headers are read. Address-only links require a user-selected search result. */
class SharedLocationResolver(
    private val redirect: (String) -> String? = ::googleMapsRedirect
) {
    fun resolve(sharedText: String): SharedDestination? {
        SharedLocationParser.parse(sharedText)?.let { return SharedDestination.Coordinates(it) }
        val links = SharedLocationParser.supportedUris(sharedText)
            .filter(SharedLocationParser::isSupportedNetworkUrl).take(3)
        var failure: IOException? = null
        for (link in links) {
            var current = link.replaceFirst(Regex("^http:", RegexOption.IGNORE_CASE), "https:")
            val visited = mutableSetOf<String>()
            try {
                for (hop in 0..5) {
                    if (Thread.currentThread().isInterrupted) throw InterruptedException("Share cancelled")
                    if (!SharedLocationParser.isSupportedNetworkUrl(current) ||
                        !URI(current).scheme.equals("https", true) || !visited.add(current)) break
                    SharedLocationParser.parse(current)?.let { return SharedDestination.Coordinates(it) }
                    SharedLocationParser.addressQuery(current)?.let { address ->
                        // Autocomplete works best with the place name. Keep the full shared address
                        // for the rider to compare; never silently select the first search match.
                        val name = address.substringBefore(',').trim().takeIf { it.length >= 3 } ?: address
                        return SharedDestination.Address(name, address)
                    }
                    if (hop == 5) break
                    val next = redirect(current) ?: break
                    current = URI(current).resolve(next).toASCIIString()
                }
            } catch (error: IOException) {
                failure = error
            } catch (_: IllegalArgumentException) {
                // A malformed redirect is an unresolved link, never a guessed destination.
            } catch (_: java.net.URISyntaxException) {
                // A malformed redirect is an unresolved link.
            }
        }
        failure?.let { throw it }
        return null
    }
}

private fun googleMapsRedirect(url: String): String? {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("User-Agent", "Mozilla/5.0")
        return when (connection.responseCode) {
            301, 302, 303, 307, 308 -> connection.getHeaderField("Location")
            in 200..299, 404, 410 -> null
            else -> throw IOException("Shared link unavailable")
        }
    } finally {
        connection.disconnect()
    }
}
