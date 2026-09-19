package com.ridesaathi.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SavedPlace(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val aliases: List<String>,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val isHome: Boolean = false
)

data class Profile(val name: String = "", val language: String = "en", val completed: Boolean = false)

class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("ride_saathi", Context.MODE_PRIVATE)

    fun searchEndpoint(): String = prefs.getString("search_endpoint", ProviderEndpoints.SEARCH) ?: ProviderEndpoints.SEARCH

    fun mapEndpoint(): String = prefs.getString("map_endpoint", ProviderEndpoints.MAP) ?: ProviderEndpoints.MAP

    fun saveProviderEndpoints(search: String, map: String) {
        prefs.edit().putString("search_endpoint", search).putString("map_endpoint", map).apply()
    }

    fun profile(): Profile = try {
        val data = JSONObject(prefs.getString("profile", "{}") ?: "{}")
        Profile(data.optString("name"), data.optString("language", "en"), data.optBoolean("completed"))
    } catch (_: Exception) { Profile() }

    fun saveProfile(profile: Profile) {
        prefs.edit().putString("profile", JSONObject().put("name", profile.name)
            .put("language", profile.language).put("completed", profile.completed).toString()).apply()
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
    } catch (_: Exception) { emptyList() }

    fun savePlaces(places: List<SavedPlace>) {
        val items = JSONArray()
        places.forEach { place ->
            items.put(JSONObject().put("id", place.id).put("name", place.name)
                .put("aliases", JSONArray(place.aliases)).put("address", place.address)
                .put("latitude", place.latitude).put("longitude", place.longitude)
                .put("isHome", place.isHome))
        }
        prefs.edit().putString("places", items.toString()).apply()
    }
}


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
