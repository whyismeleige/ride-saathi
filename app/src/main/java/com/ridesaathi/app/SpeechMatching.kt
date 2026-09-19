package com.ridesaathi.app

import java.text.Normalizer
import java.util.Locale

/** Whole-token matching preserves Hindi vowel marks and excludes `home` in `homework`. */
object SpeechText {
    fun tokens(value: String): List<String> = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace(Regex("['’]s\\b"), "")
        .replace("\u200c", "").replace("\u200d", "")
        .split(Regex("[^\\p{L}\\p{M}\\p{N}]+")).filter(String::isNotBlank)

    fun contains(text: String, phrase: String): Boolean {
        val words = tokens(phrase)
        return words.isNotEmpty() && tokens(text).windowed(words.size).any { it == words }
    }
}

object DestinationResolver {
    // Transliteration cannot infer meaning. Family-specific translations remain editable aliases.
    private val vocabulary = listOf(
        listOf("home", "घर", "होम", "ghar", "ఇల్లు"),
        listOf("house", "हाउस", "घर", "ghar"),
        listOf("son", "beta", "bete", "बेटा", "बेटे"),
        listOf("doctor", "डॉक्टर", "डाक्टर", "चिकित्सक"),
        listOf("hospital", "हॉस्पिटल", "अस्पताल"),
        listOf("temple", "टेंपल", "मंदिर", "mandir"),
        listOf("office", "ऑफिस", "कार्यालय"),
        listOf("clinic", "क्लिनिक"),
        listOf("market", "मार्केट", "बाज़ार", "बाजार"),
        listOf("bank", "बैंक"),
        listOf("airport", "एयरपोर्ट", "हवाई अड्डा")
    ).map { group -> group.map(SpeechText::tokens) }
    private val possessives = setOf("के", "का", "की", "ke", "ka", "ki")

    private fun variants(alias: String): List<List<String>> {
        val tokens = SpeechText.tokens(alias)
        return (vocabulary.filter { tokens in it }.flatten() + listOf(tokens))
            .map { phrase -> phrase.filterNot { it in possessives } }.distinct()
    }

    fun voiceNames(place: SavedPlace): List<String> = listOf(place.name) + place.aliases +
        if (place.isHome) listOf("home", "घर", "ఇల్లు") else emptyList()

    fun matches(speech: String, places: List<SavedPlace>): List<SavedPlace> {
        val heard = SpeechText.tokens(speech).filterNot { it in possessives }
        if (heard.isEmpty()) return emptyList()
        data class Match(val place: SavedPlace, val start: Int, val end: Int)
        val matches = places.flatMap { place -> voiceNames(place).flatMap { alias ->
            variants(alias).flatMap { phrase ->
                if (phrase.isEmpty()) emptyList() else heard.windowed(phrase.size).mapIndexedNotNull { start, window ->
                    if (window.zip(phrase).all { (a, b) -> equivalent(a, b) }) Match(place, start, start + phrase.size) else null
                }
            }
        } }
        // Prefer "Ramesh House" over the contained "Home", but preserve separate mentions
        // ("home or doctor") and equally specific matches for explicit clarification.
        return matches.filter { match -> matches.none { other ->
            other.start <= match.start && other.end >= match.end &&
                other.end - other.start > match.end - match.start
        } }.map { it.place }.distinctBy { it.id }
    }

    fun conflicts(candidate: SavedPlace, existing: List<SavedPlace>): Boolean = existing
        .filterNot { it.id == candidate.id }.any { other ->
            voiceNames(candidate).any { a -> voiceNames(other).any { b ->
                variants(a).any { av -> variants(b).any { bv ->
                    av.isNotEmpty() && av.size == bv.size && av.zip(bv).all { (x, y) -> equivalent(x, y) }
                } }
            } }
        }

    private fun equivalent(a: String, b: String): Boolean {
        if (a == b) return true
        if (vocabulary.any { listOf(a) in it && listOf(b) in it }) return true
        return HindiName.matches(a, b) || HindiName.matches(b, a)
    }
}

/** Bounded Hindi/Roman spelling comparison, offline and compatible with Android 8.
 * Keep explicit vowels; only inherent schwas may be omitted. Avoid edit-distance guesses
 * and consonant-only keys, which would conflate distinct names such as Ram and Rome.
 */
private object HindiName {
    private val consonants = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "n",
        'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "n",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
        'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "f", 'ब' to "b", 'भ' to "bh", 'म' to "m",
        'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v", 'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h"
    )
    private val vowels = mapOf('अ' to "a", 'आ' to "a", 'इ' to "i", 'ई' to "i", 'उ' to "u", 'ऊ' to "u",
        'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au", 'ऋ' to "ri", 'ऑ' to "o", 'ऍ' to "e")
    private val marks = mapOf('ा' to "a", 'ि' to "i", 'ी' to "i", 'ु' to "u", 'ू' to "u", 'े' to "e",
        'ै' to "ai", 'ो' to "o", 'ौ' to "au", 'ृ' to "ri", 'ॉ' to "o", 'ॅ' to "e")

    fun matches(hindi: String, latin: String): Boolean {
        if (!hindi.any { it in '\u0900'..'\u097f' } || !latin.matches(Regex("[a-z]+"))) return false
        val roman = latin.replace("ee", "i").replace("oo", "u").replace("aa", "a")
            .replace("ph", "f").replace('w', 'v').replace(Regex("c(?!h)"), "k").replace('q', 'k').replace(Regex("([a-z])\\1+"), "$1")
        val source = Normalizer.normalize(hindi, Normalizer.Form.NFD)
        val pattern = StringBuilder()
        var consonantCount = 0
        var index = 0
        while (index < source.length) {
            val char = source[index++]
            val consonant = consonants[char]
            if (consonant != null) {
                consonantCount++
                val nukta = source.getOrNull(index) == '़'
                if (nukta) index++
                pattern.append(if (nukta) when (char) { 'ज' -> "z"; 'ड' -> "r"; 'ढ' -> "rh"; else -> consonant } else consonant)
                val next = source.getOrNull(index)
                when {
                    next == '्' -> index++
                    next in marks -> { pattern.append(marks[next]); index++ }
                    else -> pattern.append("a?")
                }
            } else when {
                char in vowels -> pattern.append(vowels[char])
                char == 'ं' || char == 'ँ' -> pattern.append("[nm]?")
                else -> return false
            }
        }
        // Initials and one-consonant words require an explicit alias.
        return consonantCount >= 2 && roman.matches(Regex(pattern.toString()))
    }
}

enum class VoiceDecision { Cancel, Yes, No, Unknown }

object VoiceCommands {
    fun decision(text: String): VoiceDecision {
        fun has(vararg words: String) = words.any { SpeechText.contains(text, it) }
        return when {
            has("cancel", "stop", "रद्द", "बंद", "कैंसल", "रुको", "రద్దు", "ఆపు") -> VoiceDecision.Cancel
            // A negative always wins, including "yes, no" and "हाँ नहीं".
            has("no", "nope", "not", "don't", "नहीं", "नही", "मत", "కాదు", "వద్దు") -> VoiceDecision.No
            has("yes", "yeah", "हाँ", "हां", "అవును") -> VoiceDecision.Yes
            else -> VoiceDecision.Unknown
        }
    }
}
