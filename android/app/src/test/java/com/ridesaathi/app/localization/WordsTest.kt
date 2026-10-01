package com.ridesaathi.app.localization

import org.junit.Assert.*
import org.junit.Test

class WordsTest {
    @Test
    fun everyExistingKeyRemainsAvailableInAllThreeLanguages() {
        assertEquals(155, en.size)
        assertEquals(en.keys, hi.keys)
        assertEquals(en.keys, te.keys)
        for ((language, dictionary) in listOf("en" to en, "hi" to hi, "te" to te)) {
            dictionary.forEach { (key, value) ->
                assertTrue("$language/$key must not be blank", value.isNotBlank())
                assertEquals(value, Words.get(language, key))
            }
        }
    }

    @Test
    fun unsupportedLanguagesUseEnglishAndUnknownKeysRemainVisible() {
        assertEquals(Words.get("en", "welcome"), Words.get("unknown", "welcome"))
        assertEquals("missing-key", Words.get("hi", "missing-key"))
    }

    @Test
    fun speechLocalesPreserveRegionalLanguageTags() {
        assertEquals("en-IN", languageLocale("en").toLanguageTag())
        assertEquals("hi-IN", languageLocale("hi").toLanguageTag())
        assertEquals("te-IN", languageLocale("te").toLanguageTag())
        assertEquals("en-IN", languageLocale("unknown").toLanguageTag())
    }
}
