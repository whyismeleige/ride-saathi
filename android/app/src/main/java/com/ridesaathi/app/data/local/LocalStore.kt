package com.ridesaathi.app.data.local

import android.content.Context
import com.ridesaathi.app.data.places.ProviderEndpoints
import com.ridesaathi.app.domain.model.Profile
import com.ridesaathi.app.domain.model.SavedPlace
import org.json.JSONArray
import org.json.JSONObject

class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("ride_saathi", Context.MODE_PRIVATE)

    fun mapEndpoint(): String =
        prefs.getString("map_endpoint", ProviderEndpoints.MAP) ?: ProviderEndpoints.MAP

    fun profile(): Profile = try {
        parseProfile(prefs.getString("profile", "{}") ?: "{}")
    } catch (_: Exception) {
        Profile()
    }

    fun saveProfile(profile: Profile) {
        prefs.edit().putString("profile", serializeProfile(profile)).apply()
    }

    fun places(): List<SavedPlace> = try {
        val items = JSONArray(prefs.getString("places", "[]") ?: "[]")
        (0 until items.length()).map { index ->
            val item = items.getJSONObject(index)
            val aliases = item.optJSONArray("aliases") ?: JSONArray()
            SavedPlace(
                id = item.getString("id"), name = item.getString("name"),
                aliases = (0 until aliases.length()).map { aliases.getString(it) },
                address = item.getString("address"), latitude = item.getDouble("latitude"),
                longitude = item.getDouble("longitude"), isHome = item.optBoolean("isHome")
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun savePlaces(places: List<SavedPlace>) {
        val items = JSONArray()
        places.forEach { place ->
            items.put(
                JSONObject().put("id", place.id).put("name", place.name)
                    .put("aliases", JSONArray(place.aliases)).put("address", place.address)
                    .put("latitude", place.latitude).put("longitude", place.longitude)
                    .put("isHome", place.isHome)
            )
        }
        prefs.edit().putString("places", items.toString()).apply()
    }

    companion object {
        fun parseProfile(json: String): Profile {
            val data = JSONObject(json)
            return Profile(
                name = data.optString("name"),
                language = data.optString("language", "en"),
                completed = data.optBoolean("completed"),
                introSeen = data.optBoolean("introSeen"),
                tutorialSeen = data.optBoolean("tutorialSeen")
            )
        }

        fun serializeProfile(profile: Profile): String =
            JSONObject()
                .put("name", profile.name)
                .put("language", profile.language)
                .put("completed", profile.completed)
                .put("introSeen", profile.introSeen)
                .put("tutorialSeen", profile.tutorialSeen)
                .toString()
    }
}
