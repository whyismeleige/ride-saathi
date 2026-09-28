package com.ridesaathi.app.localization

object Words {
    fun get(language: String, key: String): String =
        when (language) {
            "hi" -> hi[key]
            "te" -> te[key]
            else -> en[key]
        }
            ?: en[key] ?: key
}
