package com.bolo.english.translate

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

enum class Source { PHRASEBOOK, GEMINI, MLKIT }

/** A Hindi sentence and its English, with optional teaching help. */
data class Translation(
    val hindi: String,
    val english: String,
    val explanation: String = "",
    val words: List<Pair<String, String>> = emptyList(),
    val source: Source,
)

data class ChatTurn(val fromChild: Boolean, val text: String)

data class TaraReply(val english: String, val hindi: String, val correction: String)

data class GeminiError(val code: Int, val status: String, val message: String)

enum class GeminiProblem { NO_KEY, BAD_KEY, QUOTA, MODEL, OFFLINE, OTHER }

class GeminiException(val problem: GeminiProblem, message: String = problem.name) : Exception(message)

/** Builds Gemini API requests and reads its replies. No networking here. */
object GeminiJson {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun translateInstruction(age: String) = """
        You are Tara, a kind English teacher for an Indian child aged $age years.
        The child gives a sentence in Hindi (Devanagari or Roman script) or mixed Hindi and English.
        Translate it into natural, correct, simple English that a child would say. Keep the exact meaning,
        tense, negation and the speaker's gender. If the input is already English, correct its grammar.
        Reply ONLY with JSON: {"english": string, "explanation_hi": string, "words": [{"hi": string, "en": string}]}.
        "explanation_hi" is one or two very short sentences in simple Hindi (Devanagari) explaining the key words
        or grammar for a child. "words" has up to 5 key Hindi words with their English.
        If the text is not suitable for a child, set "english" to "Let us talk about something else."
    """.trimIndent()

    private fun chatInstruction(age: String) = """
        You are Tara, a friendly parrot who teaches spoken English to an Indian child aged $age years.
        The child may speak Hindi, English or a mix. Always reply in simple, correct English, at most two short
        sentences, and usually ask one simple question to keep the child talking.
        If the child made an English mistake, put the corrected sentence in "correction" as "We say: ...".
        If the child spoke Hindi, put the English way to say it in "correction" as "In English: ...".
        Otherwise "correction" is empty. Put a very short, simple Hindi line in "reply_hi" only when it helps
        the child understand; otherwise leave it empty.
        Talk only about child-friendly topics such as family, school, play, animals, food, colours, feelings
        and stories. Never ask for or repeat personal details such as full name, address, phone number or
        school name. If the child mentions something unsafe, scary or sad, reply kindly, suggest talking to a
        parent or teacher, and gently return to English practice. Never use the word "wrong"; be encouraging.
        Reply ONLY with JSON: {"reply_en": string, "reply_hi": string, "correction": string}.
    """.trimIndent()

    fun translateRequest(hindi: String, age: String): String = buildJsonObject {
        putJsonObject("systemInstruction") { putJsonArray("parts") { addJsonObject { put("text", translateInstruction(age)) } } }
        putJsonArray("contents") {
            addJsonObject {
                put("role", "user")
                putJsonArray("parts") { addJsonObject { put("text", hindi) } }
            }
        }
        putJsonObject("generationConfig") {
            put("responseMimeType", "application/json")
            put("temperature", 0.2)
        }
    }.toString()

    fun chatRequest(history: List<ChatTurn>, age: String): String = buildJsonObject {
        putJsonObject("systemInstruction") { putJsonArray("parts") { addJsonObject { put("text", chatInstruction(age)) } } }
        putJsonArray("contents") {
            history.takeLast(20).forEach { turn ->
                addJsonObject {
                    put("role", if (turn.fromChild) "user" else "model")
                    putJsonArray("parts") { addJsonObject { put("text", turn.text) } }
                }
            }
        }
        putJsonObject("generationConfig") {
            put("responseMimeType", "application/json")
            put("temperature", 0.7)
        }
    }.toString()

    fun parseTranslation(hindi: String, response: String): Translation? {
        val obj = modelJson(response) ?: return null
        val english = obj.str("english")?.trim().orEmpty()
        if (english.isEmpty()) return null
        val words = (obj["words"] as? JsonArray).orEmpty().mapNotNull { w ->
            val o = w as? JsonObject ?: return@mapNotNull null
            val hi = o.str("hi")?.trim().orEmpty()
            val en = o.str("en")?.trim().orEmpty()
            if (hi.isEmpty() || en.isEmpty()) null else hi to en
        }
        return Translation(hindi, english, obj.str("explanation_hi")?.trim().orEmpty(), words, Source.GEMINI)
    }

    fun parseChat(response: String): TaraReply? {
        val obj = modelJson(response) ?: return null
        val english = obj.str("reply_en")?.trim().orEmpty()
        if (english.isEmpty()) return null
        return TaraReply(english, obj.str("reply_hi")?.trim().orEmpty(), obj.str("correction")?.trim().orEmpty())
    }

    fun errorOf(response: String): GeminiError? = runCatching {
        val err = json.parseToJsonElement(response).jsonObject["error"]?.jsonObject ?: return null
        GeminiError(err["code"]?.jsonPrimitive?.intOrNull ?: 0, err.str("status").orEmpty(), err.str("message").orEmpty())
    }.getOrNull()

    fun problemOf(httpCode: Int, body: String): GeminiProblem {
        val e = errorOf(body)
        val msg = e?.message.orEmpty().lowercase()
        return when {
            httpCode == 429 || e?.status == "RESOURCE_EXHAUSTED" -> GeminiProblem.QUOTA
            httpCode == 401 || httpCode == 403 || "api key" in msg || e?.status == "PERMISSION_DENIED" ||
                e?.status == "UNAUTHENTICATED" -> GeminiProblem.BAD_KEY
            httpCode == 404 || ("model" in msg && "not found" in msg) -> GeminiProblem.MODEL
            else -> GeminiProblem.OTHER
        }
    }

    /** Picks the newest stable "flash" text model; falls back to flash-lite, then preview models. */
    fun pickModel(modelsJson: String): String? = runCatching {
        val excluded = listOf("image", "tts", "audio", "live", "embedding", "thinking", "exp", "computer", "native", "robotics")
        json.parseToJsonElement(modelsJson).jsonObject["models"]!!.jsonArray
            .map { it.jsonObject }
            .filter { m -> m["supportedGenerationMethods"]?.jsonArray?.any { it.jsonPrimitive.content == "generateContent" } == true }
            .mapNotNull { it.str("name")?.removePrefix("models/") }
            .filter { it.startsWith("gemini-") && "flash" in it && excluded.none { e -> e in it } }
            .sortedWith(
                compareBy<String> { "preview" in it }
                    .thenBy { "lite" in it }
                    .thenByDescending { version(it) }
                    .thenBy { it.length },
            )
            .firstOrNull()
    }.getOrNull()

    private fun version(id: String): Double =
        Regex("gemini-(\\d+(?:\\.\\d+)?)").find(id)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

    /** The JSON object the model wrote, tolerating code fences and extra text around it. */
    private fun modelJson(response: String): JsonObject? = runCatching {
        val root = json.parseToJsonElement(response).jsonObject
        val text = root["candidates"]!!.jsonArray[0].jsonObject["content"]!!.jsonObject["parts"]!!.jsonArray
            .joinToString("") { it.jsonObject.str("text").orEmpty() }
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        json.parseToJsonElement(text.substring(start, end + 1)).jsonObject
    }.getOrNull()

    private fun JsonObject.str(key: String): String? = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull

}
