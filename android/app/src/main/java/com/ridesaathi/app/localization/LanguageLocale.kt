package com.ridesaathi.app.localization

import java.util.Locale

fun languageLocale(language: String): Locale = Locale.forLanguageTag(
    when (language) {
        "hi" -> "hi-IN"
        "te" -> "te-IN"
        else -> "en-IN"
    }
)
