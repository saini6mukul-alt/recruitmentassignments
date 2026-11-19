package com.bolo.english.translate

import kotlinx.coroutines.CancellationException

/** Something that can turn Hindi into English, or return null if it cannot. */
fun interface TextTranslator {
    suspend fun translate(hindi: String): Translation?
}

/** Tries the checked phrasebook first, then the online teacher (Gemini, if set up), then on-device ML Kit. */
class TranslationService(
    private val phrasebook: Phrasebook,
    private val online: TextTranslator?,
    private val offline: TextTranslator,
) {
    suspend fun translate(hindi: String): Translation? {
        val text = hindi.trim()
        if (text.isEmpty()) return null
        phrasebook.match(text)?.let { return Translation(text, it.en, it.tip, emptyList(), Source.PHRASEBOOK) }
        online?.let { tryOrNull { it.translate(text) } }?.let { return it }
        return tryOrNull { offline.translate(text) }
    }

    private suspend fun tryOrNull(block: suspend () -> Translation?): Translation? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
