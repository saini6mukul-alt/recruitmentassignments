package com.bolo.english.lesson

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonParserTest {

    private fun step(id: String, say: String = "Say cat", answer: String = "cat", hindi: String = "बिल्ली") =
        """{"id":"$id","type":"repeat","say":"$say","answer":"$answer","hindi":"$hindi"}"""

    private fun book(steps: List<String>, lessonId: String = "l1", extraLesson: String = "") = """
        {"themes":[{"id":"t1","title":"Animals","emoji":"🐾","lessons":[
          {"id":"$lessonId","title":"Pets","emoji":"🐶","sticker":"🐱","steps":[${steps.joinToString(",")}]}
          $extraLesson
        ]}]}
    """.trimIndent()

    private val fiveSteps = (1..5).map { step("s$it") }

    @Test
    fun parsesMinimalBook() {
        val parsed = LessonParser.parse(book(fiveSteps))
        assertEquals(1, parsed.themes.size)
        assertEquals("Pets", parsed.allLessons.single().title)
        assertEquals(StepType.REPEAT, parsed.allLessons.single().steps.first().type)
    }

    @Test
    fun parsesOptionalFields() {
        val roleplay = """{"id":"r1","type":"roleplay","say":"Hello! What fruit do you want?","answer":"I want a mango",
            "accepted":["i want {any}"],"hindi":"मुझे आम चाहिए","reply":"Here you go!",
            "branches":[{"keywords":["mango"],"reply":"Yummy mango!"}],"picture":"🥭"}"""
        val parsed = LessonParser.parse(book(fiveSteps.take(4) + roleplay))
        val s = parsed.allLessons.single().steps.last()
        assertEquals(StepType.ROLEPLAY, s.type)
        assertEquals(listOf("i want {any}"), s.accepted)
        assertEquals("Yummy mango!", s.branches.single().reply)
        assertEquals("Here you go!", s.reply)
        assertEquals("🥭", s.picture)
    }

    @Test
    fun validBookHasNoErrors() {
        assertEquals(emptyList<String>(), LessonValidator.validate(LessonParser.parse(book(fiveSteps))))
    }

    @Test
    fun reportsDuplicateStepIds() {
        val errors = LessonValidator.validate(LessonParser.parse(book(fiveSteps + step("s1"))))
        assertTrue(errors.toString(), errors.any { "duplicate" in it && "s1" in it })
    }

    @Test
    fun reportsDuplicateLessonIds() {
        val second = """,{"id":"l1","title":"Again","emoji":"🐶","sticker":"🐱","steps":[${fiveSteps.joinToString(",")}]}"""
        val errors = LessonValidator.validate(LessonParser.parse(book(fiveSteps, extraLesson = second)))
        assertTrue(errors.toString(), errors.any { "duplicate" in it && "l1" in it })
    }

    @Test
    fun reportsBlankText() {
        val errors = LessonValidator.validate(LessonParser.parse(book(fiveSteps.take(4) + step("s5", answer = " "))))
        assertTrue(errors.toString(), errors.any { "s5" in it && "answer" in it })
    }

    @Test
    fun reportsTooFewAndTooManySteps() {
        val few = LessonValidator.validate(LessonParser.parse(book(fiveSteps.take(4))))
        assertTrue(few.toString(), few.any { "steps" in it })
        val many = LessonValidator.validate(LessonParser.parse(book((1..9).map { step("s$it") })))
        assertTrue(many.toString(), many.any { "steps" in it })
    }

    @Test
    fun reportsUnknownSlot() {
        val bad = """{"id":"s5","type":"question","say":"Q","answer":"my name is Riya","accepted":["my name is {name}"],"hindi":"मेरा नाम"}"""
        val errors = LessonValidator.validate(LessonParser.parse(book(fiveSteps.take(4) + bad)))
        assertTrue(errors.toString(), errors.any { "{name}" in it })
    }
}
