package com.bolo.english.talk

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bolo.english.lesson.AttemptLogic
import com.bolo.english.lesson.Step
import com.bolo.english.lesson.StepType
import com.bolo.english.progress.Progress
import com.bolo.english.progress.ProgressLogic
import com.bolo.english.progress.SavedSentence
import com.bolo.english.scoring.ScoreResult
import com.bolo.english.scoring.Scorer
import com.bolo.english.speech.SpeechEvent
import com.bolo.english.speech.SpeechListener
import com.bolo.english.translate.Source
import com.bolo.english.translate.Translation
import com.bolo.english.translate.TranslationService
import com.bolo.english.voice.TaraVoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

enum class TalkPhase { IDLE, LISTENING_HINDI, TRANSLATING, TARA_SPEAKING, READY, LISTENING_ENGLISH }

data class TranslateState(
    val phase: TalkPhase = TalkPhase.IDLE,
    val bubble: String = "कोई भी बात हिंदी में बोलो या लिखो, मैं बताऊँगी उसे English में कैसे बोलते हैं!",
    val heardHindi: String = "",
    val translation: Translation? = null,
    val heardEnglish: String = "",
    val result: ScoreResult? = null,
    val failed: Int = 0,
)

/** "हिंदी → English": translate what the child says, then let them practise saying it in English. */
class TranslateController(
    private val scope: CoroutineScope,
    private val voice: TaraVoice,
    private val speech: SpeechListener,
    private val service: () -> TranslationService,
    private val progress: () -> Progress,
    private val save: (Progress) -> Unit,
) {
    var state by mutableStateOf(TranslateState())
        private set

    /** False once the child leaves the screen, so late results do not make Tara talk elsewhere. */
    private var visible = false

    fun reset() { stop(); state = TranslateState() }

    fun leave() { visible = false; stop() }

    fun open(saved: SavedSentence?) {
        reset()
        visible = true
        if (saved != null) show(Translation(saved.hindi, saved.english, source = Source.PHRASEBOOK), save = false)
        else voice.speak("Say anything in Hindi. I will tell you how to say it in English!")
    }

    fun listenHindi() {
        if (state.phase == TalkPhase.TRANSLATING) return
        voice.stop()
        state = TranslateState(phase = TalkPhase.LISTENING_HINDI, bubble = "सुन रही हूँ… हिंदी में बोलो!")
        speech.start(SpeechListener.HINDI) { e ->
            when (e) {
                is SpeechEvent.Partial -> state = state.copy(heardHindi = e.text)
                is SpeechEvent.Results -> { speech.stop(); translate(e.alternatives.first()) }
                is SpeechEvent.Error -> {
                    speech.stop()
                    state = state.copy(phase = TalkPhase.IDLE, bubble = "मुझे सुनाई नहीं दिया। माइक दबाकर फिर से बोलो, या नीचे लिखो।")
                }
                SpeechEvent.Ready -> Unit
            }
        }
    }

    fun translate(hindi: String) {
        val text = hindi.trim()
        if (text.isEmpty()) return
        stop()
        state = TranslateState(phase = TalkPhase.TRANSLATING, heardHindi = text, bubble = "Tara सोच रही है… (पहली बार थोड़ा समय लग सकता है)")
        scope.launch {
            val t = service().translate(text)
            if (!visible) return@launch
            if (t == null) {
                state = state.copy(phase = TalkPhase.IDLE,
                    bubble = "माफ़ करना, मैं इसका अनुवाद नहीं कर पाई। छोटा वाक्य बोलकर देखो, या इंटरनेट चालू करके फिर कोशिश करो।")
            } else show(t, save = true)
        }
    }

    private fun show(t: Translation, save: Boolean) {
        if (save) save(ProgressLogic.saveSentence(progress(), SavedSentence(t.hindi, t.english, 0, System.currentTimeMillis())))
        state = state.copy(phase = TalkPhase.TARA_SPEAKING, heardHindi = t.hindi, translation = t, result = null, heardEnglish = "", failed = 0,
            bubble = "English में ऐसे बोलते हैं:")
        voice.speak("In English, we say: ${t.english}") {
            voice.speak("Now you say it!") { state = state.copy(phase = TalkPhase.READY, bubble = "अब तुम बोलो! 🎤 दबाओ और English में बोलो।") }
        }
    }

    fun hearEnglish() {
        val t = state.translation ?: return
        voice.speak(t.english)
    }

    fun hearSlowly() {
        val t = state.translation ?: return
        voice.speak(t.english.split(Regex("\\s+")).joinToString(", "))
    }

    fun listenEnglish() {
        val t = state.translation ?: return
        if (state.phase == TalkPhase.TRANSLATING) return
        voice.stop()
        state = state.copy(phase = TalkPhase.LISTENING_ENGLISH, heardEnglish = "", result = null, bubble = "सुन रही हूँ… English में बोलो!")
        speech.start(SpeechListener.ENGLISH) { e ->
            when (e) {
                is SpeechEvent.Partial -> state = state.copy(heardEnglish = e.text)
                is SpeechEvent.Results -> { speech.stop(); judge(t, e.alternatives) }
                is SpeechEvent.Error -> {
                    speech.stop()
                    state = state.copy(phase = TalkPhase.READY, bubble = "मुझे सुनाई नहीं दिया। फिर से 🎤 दबाकर बोलो।")
                }
                SpeechEvent.Ready -> Unit
            }
        }
    }

    private fun judge(t: Translation, alternatives: List<String>) {
        val step = Step(id = "free", type = StepType.REPEAT, say = t.english, answer = t.english, hindi = t.hindi)
        val result = Scorer.score(alternatives, step)
        save(ProgressLogic.updateSentenceStars(progress(), t.english, result.stars))
        val missed = result.marks.filter { !it.heard }.map { it.word }
        if (missed.isNotEmpty() && !result.passed) save(ProgressLogic.recordMisses(progress(), missed))
        val failed = if (result.passed) state.failed else state.failed + 1
        val (en, hi) = when {
            result.stars == 3 -> "Excellent! You said it perfectly!" to "शाबाश! बिल्कुल सही बोला!"
            result.passed -> "Very good! Say it once more: ${t.english}" to "बहुत अच्छे! एक बार और बोलो।"
            AttemptLogic.canSkip(failed) -> "Let us say it slowly together: ${t.english.split(" ").joinToString(", ")}" to "चलो धीरे-धीरे साथ में बोलते हैं।"
            else -> "Good try! Listen: ${t.english}" to "अच्छी कोशिश! ध्यान से सुनो और फिर बोलो।"
        }
        state = state.copy(phase = TalkPhase.TARA_SPEAKING, heardEnglish = alternatives.first(), result = result, failed = failed, bubble = hi)
        voice.speak(en) { state = state.copy(phase = TalkPhase.READY) }
    }

    fun stop() {
        speech.stop()
        voice.stop()
        if (state.phase == TalkPhase.LISTENING_HINDI) state = state.copy(phase = TalkPhase.IDLE)
        if (state.phase == TalkPhase.LISTENING_ENGLISH || state.phase == TalkPhase.TARA_SPEAKING) {
            state = state.copy(phase = if (state.translation != null) TalkPhase.READY else TalkPhase.IDLE)
        }
    }
}
