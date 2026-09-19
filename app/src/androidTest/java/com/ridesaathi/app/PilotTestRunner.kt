package com.ridesaathi.app

import android.app.Activity
import android.content.res.Configuration
import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner

/** Optional app-only font override; never changes the user's system settings. */
class PilotTestRunner : AndroidJUnitRunner() {
    private var requestedFontScale: Float? = null

    override fun onCreate(arguments: Bundle) {
        requestedFontScale = arguments.getString("fontScale")?.toFloatOrNull()?.also {
            require(it in 1f..2f)
        }
        super.onCreate(arguments)
    }

    @Suppress("DEPRECATION")
    override fun callActivityOnCreate(activity: Activity, icicle: Bundle?) {
        requestedFontScale?.let { scale ->
            // The framework has already accessed Activity resources by this point.
            // Update this QA process's resources before Compose creates its density.
            val resources = activity.resources
            resources.updateConfiguration(Configuration(resources.configuration).apply {
                fontScale = scale
            }, resources.displayMetrics)
        }
        super.callActivityOnCreate(activity, icicle)
    }
}
