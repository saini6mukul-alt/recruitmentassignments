package com.bolo.english.translate

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import com.google.mlkit.nl.translate.Translation as MlKit

/** Google's free on-device Hindi -> English translator. Downloads its model (~30 MB) once. */
class MlKitTranslator : TextTranslator {
    private val translator = MlKit.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.HINDI)
            .setTargetLanguage(TranslateLanguage.ENGLISH)
            .build(),
    )

    @Volatile var modelReady: Boolean = false
        private set

    /** Downloads the Hindi model if needed; false when offline before the first download. */
    suspend fun ensureModel(): Boolean {
        if (modelReady) return true
        modelReady = suspendCancellableCoroutine { cont ->
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
                .addOnSuccessListener { cont.resume(true) }
                .addOnFailureListener { cont.resume(false) }
        }
        return modelReady
    }

    override suspend fun translate(hindi: String): Translation? {
        if (!ensureModel()) return null
        val english = suspendCancellableCoroutine<String?> { cont ->
            translator.translate(hindi)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
        }?.trim()
        if (english.isNullOrEmpty()) return null
        return Translation(hindi, english.replaceFirstChar { it.uppercase() }, source = Source.MLKIT)
    }

    fun close() = translator.close()
}
