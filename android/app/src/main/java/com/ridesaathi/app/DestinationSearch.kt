package com.ridesaathi.app

/** Removes only complete travel phrases at the edges, never words inside a place name. */
object DestinationQuery {
    private val wordPattern = Regex("[\\p{L}\\p{M}\\p{N}]+(?:['’][\\p{L}\\p{M}]+)?")
    private val correctionPrefixes = listOf(
        "no", "sorry", "actually", "please", "नहीं", "नही", "सॉरी", "माफ कीजिए", "माफ़ कीजिए",
        "nahi", "nahin", "maaf kijiye", "కాదు", "క్షమించండి", "దయచేసి"
    ).map(SpeechText::tokens).sortedByDescending { it.size }
    private val prefixes = listOf(
        "please take me to", "i would like to go to", "i want to go to", "take me to",
        "can you take me to", "let me go to", "go to", "मुझे", "हमें", "mujhe", "hame", "నన్ను", "నాకు", "మమ్మల్ని", "మాకు", "నేను"
    ).map(SpeechText::tokens).sortedByDescending { it.size }
    private val suffixes = listOf(
        "please", "के पास जाना है", "को जाना है", "जाना है", "ले चलो", "चलना है",
        "ke paas jaana hai", "ke paas jana hai", "ke pas jaana hai", "ke pas jana hai", "ko jaana hai", "ko jana hai",
        "jaana hai", "jana hai", "le chalo", "దగ్గరకు తీసుకెళ్లండి", "దగ్గరికి తీసుకెళ్లండి",
        "దగ్గరకు వెళ్లాలి", "దగ్గరికి వెళ్లాలి", "వెళ్లాలని ఉంది", "వెళ్లాలి", "తీసుకెళ్లండి", "తీసుకెళ్ళండి"
    ).map(SpeechText::tokens).sortedByDescending { it.size }

    /** An explicit new travel request can replace results without confusing it with a choice. */
    fun replacement(raw: String): String? {
        var query = raw.trim()
        while (true) {
            val matches = wordPattern.findAll(query).toList()
            val tokens = matches.map { SpeechText.tokens(it.value).joinToString("") }
            val prefix = correctionPrefixes.firstOrNull { tokens.take(it.size) == it } ?: break
            query = query.substring(matches[prefix.size - 1].range.last + 1)
                .trim().trim(',', '.', '!', '?', '।').trim()
        }
        val tokens = SpeechText.tokens(query)
        if (prefixes.none { tokens.take(it.size) == it }) return null
        return extract(query)
    }

    fun extract(raw: String): String? {
        // Retain original spelling/case and address punctuation in the remaining substring.
        var query = raw.trim().trimEnd('.', '!', '?', '।').trim()
        fun strip(phrases: List<List<String>>, leading: Boolean) {
            val matches = wordPattern.findAll(query).toList()
            val tokens = matches.map { SpeechText.tokens(it.value).joinToString("") }
            val phrase = phrases.firstOrNull {
                if (leading) tokens.take(it.size) == it else tokens.takeLast(it.size) == it
            } ?: return
            query = if (leading) query.substring(matches[phrase.size - 1].range.last + 1)
                else query.substring(0, matches[matches.size - phrase.size].range.first)
            query = query.trim().trim(',', '.', '!', '?', '।').trim()
        }
        strip(prefixes, true)
        strip(suffixes, false)
        val tokens = SpeechText.tokens(query)
        if (query.length < 3 || tokens.isEmpty() || tokens.all {
                it in setOf("please", "yes", "हाँ", "हां", "जाना", "है", "to", "go", "me", "the", "i", "want", "దయచేసి", "అవును", "వెళ్లాలి", "వెళ్లాలని", "ఉంది")
            }) return null
        return query
    }
}

sealed class SearchChoice {
    data class Select(val index: Int) : SearchChoice()
    object More : SearchChoice()
    object Previous : SearchChoice()
    object Again : SearchChoice()
    object Repeat : SearchChoice()
    object Cancel : SearchChoice()
    object Unknown : SearchChoice()
}

object DestinationChoices {
    private fun normalized(value: String) = SpeechText.tokens(value).joinToString(" ")
    private val numbers = listOf(
        listOf("1", "१", "one", "first", "first one", "option one", "number one", "option 1", "number 1", "एक", "पहला", "पहली", "पहला वाला", "पहले वाला", "pehla", "౧", "ఒకటి", "మొదటిది", "మొదటి"),
        listOf("2", "२", "two", "second", "second one", "option two", "number two", "option 2", "number 2", "दो", "दूसरा", "दूसरी", "दूसरा वाला", "दूसरे वाला", "dusra", "doosra", "౨", "రెండు", "రెండోది", "రెండవది"),
        listOf("3", "३", "three", "third", "third one", "option three", "number three", "option 3", "number 3", "तीन", "तीसरा", "तीसरी", "तीसरा वाला", "तीसरे वाला", "teesra", "౩", "మూడు", "మూడోది", "మూడవది")
    )

    fun parse(raw: String, visible: List<PlaceCandidate>): SearchChoice {
        val text = normalized(raw)
        if (text in listOf("cancel", "cancel please", "stop", "रद्द", "रद्द करें", "बंद करो", "कैंसल", "रुको", "రద్దు", "రద్దు చేయండి", "ఆపు", "ఆపండి")) return SearchChoice.Cancel
        if (text in listOf("more", "more results", "next", "next results", "और", "और नतीजे", "अगले", "आगे", "మరిన్ని ఫలితాలు", "ఇంకా", "తర్వాతి ఫలితాలు")) return SearchChoice.More
        if (text in listOf("previous", "previous results", "पिछले", "पिछले नतीजे", "మునుపటి ఫలితాలు")) return SearchChoice.Previous
        if (text in listOf("search again", "new search", "none of these", "फिर खोजें", "दोबारा खोजें", "इनमें से कोई नहीं", "మళ్లీ వెతకండి", "మళ్ళీ వెతకండి", "ఇవేవీ కాదు")) return SearchChoice.Again
        if (text in listOf("repeat", "repeat options", "say again", "फिर सुनाएँ", "फिर सुनाएं", "दोहराओ", "మళ్లీ వినిపించండి", "మళ్ళీ వినిపించండి", "మళ్లీ చెప్పండి")) return SearchChoice.Repeat
        val number = numbers.indexOfFirst { text in it }
        if (number >= 0) return if (number < visible.size) SearchChoice.Select(number) else SearchChoice.Unknown
        // Exact names/addresses only: never guess from a fragment or conflicting numbers.
        val matches = visible.indices.filter {
            text == normalized(visible[it].address) || text == normalized(visible[it].address.substringBefore(','))
        }
        return if (matches.size == 1) SearchChoice.Select(matches.single()) else SearchChoice.Unknown
    }
}

data class DestinationSearchState(
    val query: String = "",
    val candidates: List<PlaceCandidate> = emptyList(),
    val page: Int = 0,
    val loading: Boolean = false,
    val editing: Boolean = false,
    val error: String? = null,
    val retryable: Boolean = false
) {
    val visible: List<PlaceCandidate> get() = candidates.drop(page * 3).take(3)
    val hasMore: Boolean get() = (page + 1) * 3 < candidates.size
}
