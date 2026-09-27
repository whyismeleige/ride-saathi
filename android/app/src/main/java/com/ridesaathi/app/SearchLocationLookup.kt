package com.ridesaathi.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

/** Recent device position for bounded search, independent of the fresh pickup location request. */
class SearchLocationLookup(private val context: Context) {
    fun lookup(callback: (PlaceCandidate?) -> Unit): () -> Unit {
        val granted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (!granted) { callback(null); return {} }
        val handler = Handler(Looper.getMainLooper())
        val token = CancellationTokenSource()
        var finished = false
        var timeout: Runnable? = null
        fun finish(candidate: PlaceCandidate?) {
            if (finished) return
            finished = true
            timeout?.let(handler::removeCallbacks)
            token.cancel()
            callback(candidate)
        }
        timeout = Runnable { finish(null) }.also { handler.postDelayed(it, 8_000) }
        try {
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .setMaxUpdateAgeMillis(120_000).setDurationMillis(8_000).build()
            LocationServices.getFusedLocationProviderClient(context).getCurrentLocation(request, token.token)
                .addOnSuccessListener { location ->
                    val age = location?.let { (SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos) / 1_000_000 }
                    finish(location?.takeIf {
                        age != null && age in 0..120_000 && it.hasAccuracy() &&
                            it.accuracy.isFinite() && it.accuracy in 0f..5_000f &&
                            it.latitude.isFinite() && it.longitude.isFinite() &&
                            it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0
                    }?.let { PlaceCandidate("", it.latitude, it.longitude) })
                }.addOnFailureListener { finish(null) }
        } catch (_: SecurityException) { finish(null) }
        catch (_: IllegalStateException) { finish(null) }
        return {
            finished = true
            timeout?.let(handler::removeCallbacks)
            token.cancel()
        }
    }
}
