package com.ridesaathi.app.domain.model

sealed interface SharedDestination {
    data class Coordinates(val location: SharedLocation) : SharedDestination
    data class Address(val query: String, val sharedAddress: String) : SharedDestination
}
