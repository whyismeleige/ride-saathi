package com.ridesaathi.app.core.permissions

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.ridesaathi.app.core.location.SearchLocationLookup
import com.ridesaathi.app.domain.model.PlaceCandidate

/** Activity Result registration and cancellation-aware permission/location requests. */
internal class AppPermissions(private val activity: ComponentActivity) {
    private val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    private var pendingSearchPermission: ((Boolean) -> Unit)? = null
    private var pendingMicrophone: ((Boolean) -> Unit)? = null
    private var pendingPickup: ((Boolean) -> Unit)? = null
    var searchPermissionInFlight = false
        private set
    private val searchLocationPermission =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            searchPermissionInFlight = false
            val callback = pendingSearchPermission
            pendingSearchPermission = null
            callback?.invoke(result.values.any { it })
        }
    private val microphonePermission =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val callback = pendingMicrophone
            pendingMicrophone = null
            callback?.invoke(granted)
        }
    private val pickupPermission =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val callback = pendingPickup
            pendingPickup = null
            callback?.invoke(result.values.any { it })
        }

    fun hasLocation() = locationPermissions.any {
        ContextCompat.checkSelfPermission(
            activity,
            it
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasMicrophone() = ContextCompat.checkSelfPermission(
        activity,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    fun requestMicrophone(callback: (Boolean) -> Unit) {
        if (hasMicrophone()) callback(true)
        else {
            pendingMicrophone =
                callback; microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun requestPickup(callback: (Boolean) -> Unit) {
        if (hasLocation()) callback(true)
        else {
            pendingPickup = callback; pickupPermission.launch(locationPermissions)
        }
    }

    fun lookupSearchLocation(callback: (PlaceCandidate?) -> Unit): () -> Unit {
        var active = true
        var cancelLookup: () -> Unit = {}
        val onPermission: (Boolean) -> Unit = { granted ->
            if (active) {
                if (granted) cancelLookup =
                    SearchLocationLookup(activity).lookup { if (active) callback(it) }
                else callback(null)
            }
        }
        if (hasLocation()) onPermission(true)
        else {
            pendingSearchPermission = onPermission
            if (!searchPermissionInFlight) {
                searchPermissionInFlight = true
                searchLocationPermission.launch(locationPermissions)
            }
        }
        return {
            active = false
            cancelLookup()
            if (pendingSearchPermission === onPermission) pendingSearchPermission = null
        }
    }

    fun openLocationSettings() =
        activity.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))

    fun openAppSettings() = activity.startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${activity.packageName}")
        )
    )

    fun close() {
        pendingSearchPermission = null; pendingMicrophone = null; pendingPickup = null
    }
}
