package com.ridesaathi.app.localization

import com.ridesaathi.app.domain.search.PlaceSearchException
import com.ridesaathi.app.domain.search.PlaceSearchFailure

internal fun searchFailureKey(error: Throwable) = when ((error as? PlaceSearchException)?.reason) {
    PlaceSearchFailure.NOT_CONFIGURED -> "configured"
    PlaceSearchFailure.ACCESS_DENIED -> "searchAccessDenied"
    PlaceSearchFailure.QUOTA -> "searchQuota"
    PlaceSearchFailure.UNAVAILABLE, PlaceSearchFailure.INVALID_RESPONSE -> "searchUnavailable"
    PlaceSearchFailure.LOCATION_REQUIRED -> "searchLocationRequired"
    null -> "offline"
}
