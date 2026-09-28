package com.ridesaathi.app.core.deeplink

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.ridesaathi.app.domain.model.SavedPlace

internal class UberDeepLinkLauncher(private val context: Context) {
    fun isInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(UberHandoff.packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    fun launch(place: SavedPlace, latitude: Double, longitude: Double): Boolean = try {
        context.startActivity(UberHandoff.intent(place, latitude, longitude))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

    fun openStore(): Boolean {
        try {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=${UberHandoff.packageName}")
                )
            ); return true
        } catch (_: ActivityNotFoundException) {
            return try {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=${UberHandoff.packageName}")
                    )
                )
                true
            } catch (_: ActivityNotFoundException) {
                false
            }
        }
    }
}
