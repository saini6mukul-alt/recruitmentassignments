package com.bolo.english.translate

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiTest {
    private fun apiReply(text: String) = buildString {
        append("""{"candidates":[{"content":{"role":"model","parts":[{"text":""")
        append(Json.encodeToString(kotlinx.serialization.json.JsonPrimitive.serializer(), kotlinx.serialization.json.JsonPrimitive(text)))
        append("""}]}}]}""")
    }

    @Test fun translateRequestCarriesTextJsonModeAndInstruction() {
        val req = Json.parseToJsonElement(GeminiJson.translateRequest("मुझे पानी चाहिए", "7-9")).jsonObject
        val text = req["contents"]!!.jsonArray[0].jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        assertEquals("मुझे पानी चाहिए", text)
        assertEquals("application/json", req["generationConfig"]!!.jsonObject["responseMimeType"]!!.jsonPrimitive.content)
        val sys = req["systemInstruction"]!!.jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        assertTrue(sys.contains("7-9"))
    }

    @Test fun chatRequestMapsTurnsToRoles() {
        val req = Json.parseToJsonElement(GeminiJson.chatRequest(listOf(
            ChatTurn(fromChild = true, text = "hello tara"), ChatTurn(fromChild = false, text = "Hello! How are you?"),
            ChatTurn(fromChild = true, text = "i am fine"),
        ), "4-6")).jsonObject
        val roles = req["contents"]!!.jsonArray.map { it.jsonObject["role"]!!.jsonPrimitive.content }
        assertEquals(listOf("user", "model", "user"), roles)
    }

    @Test fun parsesTranslation() {
        val t = GeminiJson.parseTranslation("मुझे पानी चाहिए", apiReply(
            """{"english":"I want water.","explanation_hi":"चाहिए का मतलब want है।","words":[{"hi":"पानी","en":"water"},{"hi":"चाहिए","en":"want"}]}"""))!!
        assertEquals("I want water.", t.english)
        assertEquals("मुझे पानी चाहिए", t.hindi)
        assertEquals("चाहिए का मतलब want है।", t.explanation)
        assertEquals(listOf("पानी" to "water", "चाहिए" to "want"), t.words)
        assertEquals(Source.GEMINI, t.source)
    }

    @Test fun parsesFencedJsonWithExtraText() {
        val t = GeminiJson.parseTranslation("x", apiReply("Here you go:\n```json\n{\"english\":\"Let us play.\"}\n```"))
        assertEquals("Let us play.", t?.english)
    }

    @Test fun garbageGivesNull() {
        assertNull(GeminiJson.parseTranslation("x", apiReply("sorry, I cannot")))
        assertNull(GeminiJson.parseTranslation("x", "not json at all"))
        assertNull(GeminiJson.parseTranslation("x", apiReply("""{"english":"  "}""")))
    }

    @Test fun parsesChatReply() {
        val r = GeminiJson.parseChat(apiReply("""{"reply_en":"Very good! What is your favourite colour?","reply_hi":"शाबाश!","correction":"We say: I am fine."}"""))!!
        assertEquals("Very good! What is your favourite colour?", r.english)
        assertEquals("शाबाश!", r.hindi)
        assertEquals("We say: I am fine.", r.correction)
    }

    @Test fun chatReplyOptionalFieldsDefaultToEmpty() {
        val r = GeminiJson.parseChat(apiReply("""{"reply_en":"Hello!"}"""))!!
        assertEquals("", r.hindi)
        assertEquals("", r.correction)
    }

    @Test fun readsApiError() {
        val e = GeminiJson.errorOf("""{"error":{"code":429,"message":"Quota exceeded","status":"RESOURCE_EXHAUSTED"}}""")!!
        assertEquals(429, e.code)
        assertEquals("RESOURCE_EXHAUSTED", e.status)
        assertNull(GeminiJson.errorOf(apiReply("{}")))
    }

    @Test fun picksNewestStableFlashModel() {
        val models = """{"models":[
          {"name":"models/gemini-2.5-flash","supportedGenerationMethods":["generateContent","countTokens"]},
          {"name":"models/gemini-3.5-flash","supportedGenerationMethods":["generateContent"]},
          {"name":"models/gemini-3.5-flash-lite","supportedGenerationMethods":["generateContent"]},
          {"name":"models/gemini-4.0-flash-preview","supportedGenerationMethods":["generateContent"]},
          {"name":"models/gemini-3.6-flash-image","supportedGenerationMethods":["generateContent"]},
          {"name":"models/gemini-3.6-flash-tts","supportedGenerationMethods":["generateContent"]},
          {"name":"models/gemini-3.6-pro","supportedGenerationMethods":["generateContent"]},
          {"name":"models/text-embedding-004","supportedGenerationMethods":["embedContent"]}
        ]}"""
        assertEquals("gemini-3.5-flash", GeminiJson.pickModel(models))
    }

    @Test fun fallsBackToLiteThenPreview() {
        assertEquals("gemini-3.5-flash-lite", GeminiJson.pickModel("""{"models":[
          {"name":"models/gemini-3.5-flash-lite","supportedGenerationMethods":["generateContent"]},
          {"name":"models/gemini-4.0-flash-preview","supportedGenerationMethods":["generateContent"]}]}"""))
        assertEquals("gemini-4.0-flash-preview", GeminiJson.pickModel("""{"models":[
          {"name":"models/gemini-4.0-flash-preview","supportedGenerationMethods":["generateContent"]}]}"""))
    }

    @Test fun noUsableModelGivesNull() {
        assertNull(GeminiJson.pickModel("""{"models":[{"name":"models/text-embedding-004","supportedGenerationMethods":["embedContent"]}]}"""))
        assertNull(GeminiJson.pickModel("garbage"))
    }

    @Test fun classifiesErrors() {
        assertEquals(GeminiProblem.BAD_KEY, GeminiJson.problemOf(400, """{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","status":"INVALID_ARGUMENT"}}"""))
        assertEquals(GeminiProblem.BAD_KEY, GeminiJson.problemOf(403, """{"error":{"code":403,"message":"denied","status":"PERMISSION_DENIED"}}"""))
        assertEquals(GeminiProblem.QUOTA, GeminiJson.problemOf(429, """{"error":{"code":429,"message":"Quota exceeded","status":"RESOURCE_EXHAUSTED"}}"""))
        assertEquals(GeminiProblem.MODEL, GeminiJson.problemOf(404, """{"error":{"code":404,"message":"models/x is not found","status":"NOT_FOUND"}}"""))
        assertEquals(GeminiProblem.OTHER, GeminiJson.problemOf(500, "oops"))
    }
}
