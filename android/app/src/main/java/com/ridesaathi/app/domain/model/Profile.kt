package com.ridesaathi.app.domain.model

data class Profile(
    val name: String = "",
    val language: String = "en",
    val completed: Boolean = false,
    val introSeen: Boolean = false,
    val tutorialSeen: Boolean = false
)
