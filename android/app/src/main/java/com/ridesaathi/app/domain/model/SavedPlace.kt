package com.ridesaathi.app.domain.model

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
