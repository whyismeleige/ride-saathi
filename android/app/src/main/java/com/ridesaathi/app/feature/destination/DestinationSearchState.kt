package com.ridesaathi.app.feature.destination

import com.ridesaathi.app.domain.model.PlaceCandidate

data class DestinationSearchState(
    val query: String = "",
    val candidates: List<PlaceCandidate> = emptyList(),
    val page: Int = 0,
    val loading: Boolean = false,
    val editing: Boolean = false,
    val error: String? = null,
    val retryable: Boolean = false
) {
    val visible: List<PlaceCandidate> get() = candidates.drop(page * 3).take(3)
    val hasMore: Boolean get() = (page + 1) * 3 < candidates.size
}
