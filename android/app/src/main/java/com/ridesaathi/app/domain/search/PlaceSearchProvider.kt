package com.ridesaathi.app.domain.search

import com.ridesaathi.app.domain.model.PlaceCandidate

interface PlaceSearchProvider {
    fun search(query: String, language: String): List<PlaceCandidate>
}

enum class PlaceSearchFailure { NOT_CONFIGURED, ACCESS_DENIED, QUOTA, UNAVAILABLE, INVALID_RESPONSE, LOCATION_REQUIRED }

class PlaceSearchException(val reason: PlaceSearchFailure) : java.io.IOException(reason.name)
