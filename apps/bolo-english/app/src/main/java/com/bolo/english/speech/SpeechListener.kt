package com.bolo.english.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

sealed interface SpeechEvent {
    data object Ready : SpeechEvent
    data class Partial(val text: String) : SpeechEvent
    data class Results(val alternatives: List<String>) : SpeechEvent
    /** [noMatch] is true when the child said nothing understandable (not a real failure). */
    data class Error(val noMatch: Boolean, val code: Int) : SpeechEvent
}

/** Wraps Android's free speech recogniser (provided by the Google app), English (India). */
class SpeechListener(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null

    val isAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    /** [language] is "en-IN" for English or "hi-IN" for Hindi. */
    fun start(language: String = ENGLISH, onEvent: (SpeechEvent) -> Unit) = start(language, onEvent, preferOffline = true)

    private fun start(language: String, onEvent: (SpeechEvent) -> Unit, preferOffline: Boolean) {
        stop()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = onEvent(SpeechEvent.Ready)
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    ?.let { onEvent(SpeechEvent.Partial(it)) }
            }
            override fun onResults(results: Bundle?) {
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                onEvent(if (list.isEmpty()) SpeechEvent.Error(true, SpeechRecognizer.ERROR_NO_MATCH) else SpeechEvent.Results(list))
            }
            override fun onError(error: Int) {
                // Offline English pack missing: try again online once.
                if (preferOffline && (error == ERROR_LANGUAGE_NOT_SUPPORTED || error == ERROR_LANGUAGE_UNAVAILABLE)) {
                    start(language, onEvent, preferOffline = false)
                    return
                }
                val noMatch = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                onEvent(SpeechEvent.Error(noMatch, error))
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
            // Children pause while thinking; give them a little longer.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        }
        r.startListening(intent)
    }

    fun stop() {
        recognizer?.run { cancel(); destroy() }
        recognizer = null
    }

    fun destroy() = stop()

    companion object {
        const val ENGLISH = "en-IN"
        const val HINDI = "hi-IN"

        // SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED / ERROR_LANGUAGE_UNAVAILABLE (API 31+), as literals for minSdk 26.
        private const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        private const val ERROR_LANGUAGE_UNAVAILABLE = 13
    }
}
