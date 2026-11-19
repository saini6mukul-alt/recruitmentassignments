package com.bolo.english.talk

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bolo.english.speech.SpeechEvent
import com.bolo.english.speech.SpeechListener
import com.bolo.english.translate.ChatTurn
import com.bolo.english.translate.GeminiClient
import com.bolo.english.translate.GeminiException
import com.bolo.english.translate.GeminiProblem
import com.bolo.english.voice.TaraVoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

data class ChatMessage(val fromChild: Boolean, val text: String, val hindi: String = "", val correction: String = "")

data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val listening: String? = null, // language code while listening
    val partial: String = "",
    val busy: Boolean = false,
    val notice: String = "",
)

/** "Tara से बातें": free conversation with Tara through Gemini, child-safe instructions. */
class ChatController(
    private val scope: CoroutineScope,
    private val voice: TaraVoice,
    private val speech: SpeechListener,
    private val gemini: GeminiClient,
    private val childName: () -> String,
) {
    var state by mutableStateOf(ChatState())
        private set

    val hasKey: Boolean get() = gemini.hasKey

    private var visible = false

    fun leave() { visible = false; stop() }

    fun open() {
        visible = true
        stop()
        if (state.messages.isEmpty()) {
            val hello = ChatMessage(false, "Hello ${childName()}! I am Tara. Let us talk in English. How are you today?",
                "नमस्ते! चलो English में बातें करते हैं। तुम आज कैसे हो?")
            state = ChatState(messages = listOf(hello))
            speakReply(hello)
        }
    }

    fun clear() { stop(); state = ChatState(); open() }

    fun listen(language: String) {
        if (state.busy) return
        voice.stop()
        state = state.copy(listening = language, partial = "", notice = "")
        speech.start(language) { e ->
            when (e) {
                is SpeechEvent.Partial -> state = state.copy(partial = e.text)
                is SpeechEvent.Results -> { speech.stop(); state = state.copy(listening = null, partial = ""); send(e.alternatives.first()) }
                is SpeechEvent.Error -> {
                    speech.stop()
                    state = state.copy(listening = null, partial = "", notice = "मुझे सुनाई नहीं दिया। फिर से 🎤 दबाकर बोलो।")
                }
                SpeechEvent.Ready -> Unit
            }
        }
    }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || state.busy) return
        val messages = state.messages + ChatMessage(true, t)
        state = state.copy(messages = messages, busy = true, notice = "")
        scope.launch {
            try {
                val reply = gemini.chat(messages.map { ChatTurn(it.fromChild, it.text) })
                val msg = ChatMessage(false, reply.english, reply.hindi, reply.correction)
                state = state.copy(messages = state.messages + msg, busy = false)
                if (visible) speakReply(msg)
            } catch (e: GeminiException) {
                state = state.copy(busy = false, notice = noticeFor(e.problem))
            } catch (e: Exception) {
                state = state.copy(busy = false, notice = noticeFor(GeminiProblem.OTHER))
            }
        }
    }

    fun speakReply(m: ChatMessage) {
        voice.speak(m.text) {
            if (m.correction.isNotBlank()) voice.speak(m.correction.removePrefix("In English:").removePrefix("We say:").let { "We say: $it" }) {
                if (m.hindi.isNotBlank() && voice.hindiAvailable) voice.speak(m.hindi, hindi = true)
            } else if (m.hindi.isNotBlank() && voice.hindiAvailable) voice.speak(m.hindi, hindi = true)
        }
    }

    fun stop() {
        speech.stop()
        voice.stop()
        state = state.copy(listening = null, partial = "")
    }

    private fun noticeFor(p: GeminiProblem) = when (p) {
        GeminiProblem.NO_KEY -> "Tara से बातें करने के लिए पैरेंट कॉर्नर (⚙️) में Gemini key डालें।"
        GeminiProblem.BAD_KEY -> "Gemini key सही नहीं लग रही। पैरेंट कॉर्नर (⚙️) में key जाँचें।"
        GeminiProblem.QUOTA -> "आज की मुफ़्त सीमा पूरी हो गई। थोड़ी देर बाद या कल फिर कोशिश करें। तब तक 'हिंदी → English' और लेसन चलते रहेंगे।"
        GeminiProblem.OFFLINE -> "इंटरनेट नहीं है। इंटरनेट चालू करके फिर कोशिश करो।"
        GeminiProblem.MODEL, GeminiProblem.OTHER -> "Tara अभी जवाब नहीं दे पाई। फिर से कोशिश करो।"
    }
}
