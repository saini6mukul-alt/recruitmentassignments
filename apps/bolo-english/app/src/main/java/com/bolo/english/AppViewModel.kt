package com.bolo.english

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bolo.english.talk.ChatController
import com.bolo.english.talk.TranslateController
import com.bolo.english.translate.GeminiClient
import com.bolo.english.translate.GeminiException
import com.bolo.english.translate.MlKitTranslator
import com.bolo.english.translate.Phrasebook
import com.bolo.english.translate.TranslationService
import com.bolo.english.progress.SavedSentence
import kotlinx.coroutines.launch
import com.bolo.english.lesson.AttemptLogic
import com.bolo.english.lesson.AttemptOutcome
import com.bolo.english.lesson.Lesson
import com.bolo.english.lesson.LessonBook
import com.bolo.english.lesson.LessonParser
import com.bolo.english.lesson.Step
import com.bolo.english.lesson.StepType
import com.bolo.english.progress.Progress
import com.bolo.english.progress.ProgressLogic
import com.bolo.english.progress.ProgressRepository
import com.bolo.english.progress.Settings
import com.bolo.english.scoring.ScoreResult
import com.bolo.english.scoring.Scorer
import com.bolo.english.speech.SpeechEvent
import com.bolo.english.speech.SpeechListener
import com.bolo.english.voice.TaraVoice
import java.io.File
import java.time.LocalDate
import kotlin.math.roundToInt

sealed interface Screen {
    data object Onboarding : Screen
    data object Home : Screen
    data object Lesson : Screen
    data class LessonDone(val lessonId: String, val stars: Int) : Screen
    data object Stickers : Screen
    data object Translate : Screen
    data object Chat : Screen
    data object Sentences : Screen
    data object Parent : Screen
}

enum class Phase { TARA_SPEAKING, WAITING, LISTENING, FEEDBACK }

/** State of the lesson being played. */
data class LessonRun(
    val lesson: Lesson,
    val index: Int = 0,
    val failed: Int = 0,
    val notHeard: Int = 0,
    val phase: Phase = Phase.TARA_SPEAKING,
    val bubble: String = "",
    val heard: String = "",
    val result: ScoreResult? = null,
    val showModel: Boolean = false,
    val hindiHelp: String? = null,
    val stepStars: List<Int> = emptyList(),
    val startedAt: Long = SystemClock.elapsedRealtime(),
) {
    val step: Step get() = lesson.steps[index]
    val canSkip: Boolean get() = AttemptLogic.canSkip(failed, notHeard) && phase == Phase.WAITING
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val book: LessonBook = LessonParser.parse(app.assets.open("lessons.json").bufferedReader().use { it.readText() })
    private val repo = ProgressRepository(File(app.filesDir, "progress.json"))
    private val voice = TaraVoice(app)
    private val speech = SpeechListener(app)

    var progress by mutableStateOf(repo.load())
        private set
    var screen by mutableStateOf<Screen>(if (progress.isOnboarded) Screen.Home else Screen.Onboarding)
        private set
    var run by mutableStateOf<LessonRun?>(null)
        private set

    val speechAvailable: Boolean get() = speech.isAvailable
    val hindiVoiceAvailable: Boolean get() = voice.hindiAvailable

    // ---------- Hindi -> English and chat ----------
    private val phrasebook = Phrasebook.parse(app.assets.open("phrasebook.json").bufferedReader().use { it.readText() })
    private val mlKit = MlKitTranslator()
    val gemini = GeminiClient(
        key = { progress.settings.geminiKey },
        cachedModel = { progress.settings.geminiModel },
        saveModel = { m -> save(progress.copy(settings = progress.settings.copy(geminiModel = m))) },
        age = { progress.ageBand },
    )
    val translate = TranslateController(viewModelScope, voice, speech,
        service = { TranslationService(phrasebook, if (gemini.hasKey) gemini.translator else null, mlKit) },
        progress = { progress }, save = ::save)
    val chat = ChatController(viewModelScope, voice, speech, gemini, childName = { progress.childName })

    var keyStatus by mutableStateOf("")
        private set

    init {
        voice.setSlow(progress.settings.slowSpeech)
        // Fetch the free on-device Hindi translation model in the background (once, ~30 MB).
        viewModelScope.launch { runCatching { mlKit.ensureModel() } }
    }

    fun openTranslate(saved: SavedSentence? = null) { go(Screen.Translate); translate.open(saved) }
    fun openChat() { go(Screen.Chat); if (chat.hasKey) chat.open() }

    fun setGeminiKey(key: String) {
        save(progress.copy(settings = progress.settings.copy(geminiKey = key.trim(), geminiModel = "")))
        keyStatus = ""
    }

    fun testGeminiKey() {
        if (!gemini.hasKey) { keyStatus = "पहले key डालकर Save करें।"; return }
        keyStatus = "जाँच हो रही है…"
        viewModelScope.launch {
            keyStatus = try {
                "✅ Key काम कर रही है (मॉडल: ${gemini.testKey()})"
            } catch (e: GeminiException) {
                when (e.problem) {
                    com.bolo.english.translate.GeminiProblem.BAD_KEY -> "❌ Key सही नहीं है। दोबारा कॉपी करके डालें।"
                    com.bolo.english.translate.GeminiProblem.OFFLINE -> "❌ इंटरनेट नहीं है।"
                    com.bolo.english.translate.GeminiProblem.QUOTA -> "⚠️ Key सही है, पर आज की मुफ़्त सीमा पूरी हो गई।"
                    else -> "❌ जाँच नहीं हो पाई: ${e.message?.take(120)}"
                }
            }
        }
    }

    // ---------- navigation ----------
    fun go(s: Screen) {
        stopAudio()
        if (s != Screen.Translate) translate.leave()
        if (s != Screen.Chat) chat.leave()
        screen = s
    }
    fun goHome() { run = null; go(Screen.Home) }

    fun isUnlocked(lessonId: String) = ProgressLogic.isUnlocked(book, progress, lessonId)
    fun streak() = ProgressLogic.streak(progress, LocalDate.now())
    fun nextLessonAfter(id: String): Lesson? {
        val all = book.allLessons
        val i = all.indexOfFirst { it.id == id }
        return all.getOrNull(i + 1)?.takeIf { isUnlocked(it.id) }
    }

    // ---------- settings / profile ----------
    fun onboard(name: String, ageBand: String) {
        save(progress.copy(childName = name.trim(), ageBand = ageBand,
            settings = progress.settings.copy(slowSpeech = ageBand == "4-6")))
        voice.setSlow(progress.settings.slowSpeech)
        screen = Screen.Home
        voice.speak("Hello ${progress.childName}! I am Tara. Let us learn English together!")
    }

    fun updateProfile(name: String, ageBand: String) = save(progress.copy(childName = name.trim().ifEmpty { progress.childName }, ageBand = ageBand))

    fun updateSettings(s: Settings) {
        save(progress.copy(settings = s))
        voice.setSlow(s.slowSpeech)
    }

    fun resetProgress() = save(Progress(childName = progress.childName, ageBand = progress.ageBand, settings = progress.settings))

    private fun save(p: Progress) { progress = p; repo.save(p) }

    // ---------- lesson player ----------
    fun startLesson(lesson: Lesson) {
        stopAudio()
        run = LessonRun(lesson)
        screen = Screen.Lesson
        presentStep()
    }

    private fun presentStep() {
        val r = run ?: return
        val step = r.step
        run = r.copy(phase = Phase.TARA_SPEAKING, bubble = step.say, heard = "", result = null,
            showModel = step.type == StepType.REPEAT, hindiHelp = null)
        say(step.say) { setPhase(Phase.WAITING) }
    }

    fun hearAgain() {
        val r = run ?: return
        if (r.phase == Phase.LISTENING) return
        val text = if (r.showModel) Scorer.speakable(r.step.answer) else r.step.say
        run = r.copy(phase = Phase.TARA_SPEAKING)
        say(text) { setPhase(AttemptLogic.phaseAfterTalk(run?.result)) }
    }

    fun listen() {
        val r = run ?: return
        if (r.phase != Phase.WAITING) return
        voice.stop()
        run = r.copy(phase = Phase.LISTENING, heard = "", result = null)
        speech.start { event ->
            when (event) {
                SpeechEvent.Ready -> Unit
                is SpeechEvent.Partial -> run = run?.copy(heard = event.text)
                is SpeechEvent.Results -> handleAttempt(event.alternatives)
                is SpeechEvent.Error -> handleAttempt(null, recogniserProblem = !event.noMatch)
            }
        }
    }

    fun stopListening() {
        speech.stop()
        if (run?.phase == Phase.LISTENING) setPhase(Phase.WAITING)
    }

    private fun handleAttempt(alternatives: List<String>?, recogniserProblem: Boolean = false) {
        speech.stop()
        val r = run ?: return
        val step = r.step
        val result = alternatives?.let { Scorer.score(it, step) }
        val heard = alternatives?.firstOrNull() ?: ""
        when (AttemptLogic.next(r.failed, result, progress.settings.hindiHelp)) {
            AttemptOutcome.NOT_HEARD -> {
                val notHeard = r.notHeard + 1
                val msg = when {
                    recogniserProblem && notHeard >= AttemptLogic.NOT_HEARD_BEFORE_SKIP ->
                        "I cannot hear you right now. Ask a grown-up to check the internet or the offline English pack. You can tap Skip."
                    notHeard >= AttemptLogic.NOT_HEARD_BEFORE_SKIP -> "I still did not hear you. Try again, or tap Skip."
                    else -> "I did not hear you. Tap the microphone and try again!"
                }
                run = r.copy(phase = Phase.TARA_SPEAKING, bubble = msg, heard = "", notHeard = notHeard)
                say(msg) { setPhase(Phase.WAITING) }
            }
            AttemptOutcome.PASS -> {
                val stars = result!!.stars
                val praise = when (stars) { 3 -> "Excellent!"; 2 -> "Very good!"; else -> "Good try!" }
                val follow = when (step.type) {
                    StepType.ROLEPLAY -> Scorer.chooseReply(alternatives, step) ?: Scorer.speakable(step.answer)
                    StepType.QUESTION -> step.reply ?: "Well done!"
                    else -> Scorer.speakable(step.answer)
                }
                val bubble = "$praise $follow"
                run = r.copy(phase = Phase.TARA_SPEAKING, bubble = bubble, heard = heard, result = result,
                    showModel = true, stepStars = r.stepStars + stars)
                say(bubble) { setPhase(Phase.FEEDBACK) }
            }
            AttemptOutcome.RETRY -> {
                recordMisses(result!!)
                val msg = "Good try! Listen and say it with me: ${Scorer.speakable(step.answer)}"
                run = r.copy(phase = Phase.TARA_SPEAKING, failed = r.failed + 1, bubble = msg, heard = heard,
                    result = result, showModel = true)
                say(msg) { setPhase(Phase.WAITING) }
            }
            AttemptOutcome.HINDI_HELP -> {
                recordMisses(result!!)
                run = r.copy(phase = Phase.TARA_SPEAKING, failed = r.failed + 1, bubble = "Let me help you.",
                    heard = heard, result = result, showModel = true, hindiHelp = step.hindi)
                sayHindi(step.hindi) {
                    say("Now you try: ${Scorer.speakable(step.answer)}") { setPhase(Phase.WAITING) }
                }
            }
        }
    }

    private fun recordMisses(result: ScoreResult) {
        val missed = result.marks.filter { !it.heard && it.word != "___" }.map { it.word }
        if (missed.isNotEmpty()) save(ProgressLogic.recordMisses(progress, missed))
    }

    fun skip() {
        val r = run ?: return
        run = r.copy(stepStars = r.stepStars + 0)
        next()
    }

    fun next() {
        voice.stop()
        val r = run ?: return
        if (r.index + 1 < r.lesson.steps.size) {
            run = r.copy(index = r.index + 1, failed = 0, notHeard = 0)
            presentStep()
        } else finishLesson(r)
    }

    private fun finishLesson(r: LessonRun) {
        val stars = lessonStars(r.stepStars)
        val seconds = (SystemClock.elapsedRealtime() - r.startedAt) / 1000
        save(ProgressLogic.completeLesson(progress, r.lesson.id, stars, LocalDate.now(), seconds))
        run = null
        screen = Screen.LessonDone(r.lesson.id, stars)
        voice.speak(if (stars == 3) "Wow! Three stars! You are a superstar!" else "Well done! You finished the lesson!")
    }

    /** Average of step stars, at least one star for finishing. */
    private fun lessonStars(steps: List<Int>): Int =
        if (steps.isEmpty()) 1 else steps.average().roundToInt().coerceIn(1, 3)

    private fun setPhase(p: Phase) { run = run?.copy(phase = p) }

    private fun say(text: String, onDone: () -> Unit = {}) = voice.speak(text, onDone = onDone)

    private fun sayHindi(text: String, onDone: () -> Unit) {
        if (voice.hindiAvailable) voice.speak(text, hindi = true, onDone = onDone) else onDone()
    }

    fun stopAudio() {
        translate.stop()
        chat.stop()
        voice.stop()
        speech.stop()
        run?.let { if (it.phase == Phase.LISTENING || it.phase == Phase.TARA_SPEAKING) run = it.copy(phase = AttemptLogic.phaseAfterTalk(it.result)) }
    }

    override fun onCleared() {
        mlKit.close()
        speech.destroy()
        voice.shutdown()
    }
}
