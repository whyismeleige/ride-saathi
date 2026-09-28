package com.ridesaathi.app.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

/** Fresh high-accuracy pickup position; intentionally stricter than the nearby-search lookup. */
internal class PickupLocationLookup(private val context: Context) {
    enum class Failure { PermissionDenied, Unavailable }

    private var generation = 0
    private var cancellation: CancellationTokenSource? = null
    fun cancel() {
        generation++; cancellation?.cancel(); cancellation = null
    }

    fun lookup(onSuccess: (Location) -> Unit, onFailure: (Failure) -> Unit) {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER) && !manager.isProviderEnabled(
                LocationManager.NETWORK_PROVIDER
            )
        ) {
            onFailure(Failure.Unavailable); return
        }
        if (listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ).none {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        ) {
            onFailure(Failure.PermissionDenied); return
        }
        cancel()
        val current = generation
        val token = CancellationTokenSource().also { cancellation = it }
        val request = CurrentLocationRequest.Builder().setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(0).setDurationMillis(15_000).build()
        LocationServices.getFusedLocationProviderClient(context)
            .getCurrentLocation(request, token.token)
            .addOnSuccessListener { location ->
                if (current != generation) return@addOnSuccessListener
                if (location == null || (location.hasAccuracy() && location.accuracy > 250f)) onFailure(
                    Failure.Unavailable
                )
                else onSuccess(location)
            }.addOnFailureListener { if (current == generation) onFailure(Failure.Unavailable) }
    }
}
