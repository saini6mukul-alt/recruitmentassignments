package com.bolo.english.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/** Tara's voice, using the phone's free text-to-speech in English (India) and Hindi. */
class TaraVoice(context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val callbacks = mutableMapOf<String, () -> Unit>()
    private val counter = AtomicInteger()
    private var ready = false
    private var initDone = false
    private var pending: (() -> Unit)? = null
    private var slow = false

    private val english = Locale("en", "IN")
    private val hindi = Locale("hi", "IN")

    var hindiAvailable: Boolean = false
        private set

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        main.post {
            initDone = true
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                hindiAvailable = tts.isLanguageAvailable(hindi) >= TextToSpeech.LANG_AVAILABLE
                tts.setPitch(1.25f) // a bright, parrot-like voice
            }
            pending?.invoke()
            pending = null
        }
    }.also { engine ->
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) = finish(utteranceId)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finish(utteranceId)
            override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId)
        })
    }

    private fun finish(id: String?) {
        main.post { id?.let { callbacks.remove(it)?.invoke() } }
    }

    fun setSlow(value: Boolean) { slow = value }

    /** Speaks [text]; [onDone] runs on the main thread when finished (or immediately if speech is unavailable). */
    fun speak(text: String, hindi: Boolean = false, onDone: () -> Unit = {}) {
        if (!ready) {
            when {
                initDone -> onDone() // no speech engine: carry on silently (text is still on screen)
                pending == null -> pending = { speak(text, hindi, onDone) }
                else -> onDone()
            }
            return
        }
        val useHindi = hindi && hindiAvailable
        tts.language = if (useHindi) this.hindi else english
        tts.setSpeechRate(if (slow) 0.65f else 0.85f)
        val id = "u${counter.incrementAndGet()}"
        callbacks[id] = onDone
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) {
            callbacks.remove(id)
            onDone()
        }
    }

    fun stop() {
        tts.stop()
        callbacks.clear()
    }

    fun shutdown() {
        callbacks.clear()
        tts.shutdown()
    }
}
