package com.bolo.english.translate

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A checked Hindi sentence (with spelling/gender variants), its proper English and a short Hindi tip. */
@Serializable
data class Phrase(val hi: List<String>, val en: String, val tip: String = "")

@Serializable
private data class PhrasebookFile(val phrases: List<Phrase>)

/**
 * Finds a checked translation for common sentences. Matching is strict on purpose: after normalising
 * spelling and punctuation every word must match and only filler words may be extra, so a sentence
 * with a different meaning (e.g. with "नहीं", or लड़की instead of लड़का) never gets the wrong English.
 */
class Phrasebook(val phrases: List<Phrase>) {
    private val index: List<Pair<List<String>, Phrase>> =
        phrases.flatMap { p -> p.hi.map { HindiNormalizer.tokens(it) to p } }

    fun match(text: String): Phrase? {
        val input = HindiNormalizer.tokens(text).filter { it !in FILLERS }
        if (input.isEmpty()) return null
        return index.firstOrNull { (tokens, _) -> sameWords(input, tokens) }?.second
    }

    /** Exact word-for-word match after normalisation: one vowel sign can change the meaning (लड़का/लड़की). */
    private fun sameWords(a: List<String>, b: List<String>): Boolean = a == b

    companion object {
        private val FILLERS = setOf("अरे", "जी", "ओह", "उम्म", "हम्म")
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): Phrasebook = Phrasebook(json.decodeFromString(PhrasebookFile.serializer(), text).phrases)
    }
}
