package com.bolo.english.translate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Talks to Google's Gemini API with the parent's free key. The model is chosen automatically from the
 * key's model list and cached; it is chosen again once if Google has retired it.
 */
class GeminiClient(
    private val key: () -> String,
    private val cachedModel: () -> String,
    private val saveModel: (String) -> Unit,
    private val age: () -> String,
) {
    val hasKey: Boolean get() = key().isNotBlank()

    val translator = TextTranslator { hindi ->
        if (!hasKey) null else GeminiJson.parseTranslation(hindi, generate(GeminiJson.translateRequest(hindi, age())))
    }

    /** Tara's reply in the conversation; throws [GeminiException] with a reason the screen can explain. */
    suspend fun chat(history: List<ChatTurn>): TaraReply =
        GeminiJson.parseChat(generate(GeminiJson.chatRequest(history, age())))
            ?: throw GeminiException(GeminiProblem.OTHER, "unreadable reply")

    /** Checks the key and returns the chosen model name. */
    suspend fun testKey(): String = withContext(Dispatchers.IO) { chooseModel(force = true) }

    private suspend fun generate(body: String): String = withContext(Dispatchers.IO) {
        if (!hasKey) throw GeminiException(GeminiProblem.NO_KEY)
        var model = cachedModel().ifBlank { chooseModel(force = false) }
        try {
            post(model, body)
        } catch (e: GeminiException) {
            if (e.problem != GeminiProblem.MODEL) throw e
            model = chooseModel(force = true)
            post(model, body)
        }
    }

    private fun chooseModel(force: Boolean): String {
        if (!force && cachedModel().isNotBlank()) return cachedModel()
        val (code, text) = request("GET", "$BASE/models?pageSize=1000", null)
        if (code != 200) throw GeminiException(GeminiJson.problemOf(code, text), text.take(200))
        val model = GeminiJson.pickModel(text) ?: throw GeminiException(GeminiProblem.MODEL, "no free text model found")
        saveModel(model)
        return model
    }

    private fun post(model: String, body: String): String {
        val (code, text) = request("POST", "$BASE/models/$model:generateContent", body)
        if (code != 200) throw GeminiException(GeminiJson.problemOf(code, text), text.take(200))
        return text
    }

    private fun request(method: String, url: String, body: String?): Pair<Int, String> {
        val conn = try {
            (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 15_000
                readTimeout = 40_000
                setRequestProperty("x-goog-api-key", key().trim())
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                if (body != null) {
                    doOutput = true
                    outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
            }
        } catch (e: IOException) {
            throw GeminiException(GeminiProblem.OFFLINE, e.message ?: "offline")
        }
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            code to (stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "")
        } catch (e: IOException) {
            throw GeminiException(GeminiProblem.OFFLINE, e.message ?: "offline")
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val BASE = "https://generativelanguage.googleapis.com/v1beta"
    }
}
