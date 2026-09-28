package com.ridesaathi.app.data.places

import java.net.HttpURLConnection
import java.net.URL

data class HttpResult(val statusCode: Int, val body: String)

/** No URLs, API keys, response bodies, or user queries are included in failures. */
internal fun httpGet(url: String): HttpResult {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        // Never forward a credential-bearing URL through a redirect.
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("X-Request-Id", java.util.UUID.randomUUID().toString())
        val status = connection.responseCode
        val body =
            if (status in 200..299) connection.inputStream.bufferedReader().use { it.readText() }
            else connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        return HttpResult(status, body)
    } finally {
        connection.disconnect()
    }
}
