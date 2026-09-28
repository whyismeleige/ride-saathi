package com.ridesaathi.app

import com.ridesaathi.app.core.permissions.AppPermissions
import com.ridesaathi.app.data.places.RideSaathiPlaceSearchProvider
import com.ridesaathi.app.domain.model.PlaceCandidate
import com.ridesaathi.app.domain.search.PlaceSearchProvider

/** Small injection seam shared by address, destination, and shared-location searches. */
internal class AppDependencies(permissions: AppPermissions) {
    var searchProvider: (PlaceCandidate) -> PlaceSearchProvider = { center ->
        RideSaathiPlaceSearchProvider(BuildConfig.API_BASE_URL, center)
    }
    var destinationSearchProvider: (PlaceCandidate) -> PlaceSearchProvider = { center ->
        RideSaathiPlaceSearchProvider(BuildConfig.API_BASE_URL, center)
    }
    var destinationLocationLookup: ((PlaceCandidate?) -> Unit) -> (() -> Unit) =
        permissions::lookupSearchLocation
}
