package com.ridesaathi.app.core.deeplink

import android.content.Intent
import android.net.Uri
import com.ridesaathi.app.domain.model.SavedPlace

object UberHandoff {
    const val packageName = "com.ubercab"

    fun uri(place: SavedPlace, pickupLat: Double, pickupLng: Double): Uri =
        Uri.parse("uber://riderequest").buildUpon()
            .appendQueryParameter("pickup[latitude]", pickupLat.toString())
            .appendQueryParameter("pickup[longitude]", pickupLng.toString())
            .appendQueryParameter("dropoff[latitude]", place.latitude.toString())
            .appendQueryParameter("dropoff[longitude]", place.longitude.toString())
            .appendQueryParameter("dropoff[nickname]", place.name)
            .appendQueryParameter("dropoff[formatted_address]", place.address)
            .build()

    fun intent(place: SavedPlace, pickupLat: Double, pickupLng: Double): Intent =
        Intent(Intent.ACTION_VIEW, uri(place, pickupLat, pickupLng)).setPackage(packageName)
}
