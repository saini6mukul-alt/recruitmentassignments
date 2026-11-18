package com.bolo.english.lesson

import com.bolo.english.scoring.Scorer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Checks the real lessons shipped in the app. */
class LessonContentTest {
    private val book = LessonParser.parse(File("src/main/assets/lessons.json").readText())

    @Test fun contentIsValid() = assertEquals(emptyList<String>(), LessonValidator.validate(book))

    @Test fun sixThemesOfFourLessons() {
        assertEquals(6, book.themes.size)
        book.themes.forEach { assertEquals(it.title, 4, it.lessons.size) }
        assertEquals(24, book.allLessons.size)
    }

    @Test fun everyModelAnswerScoresThreeStarsAgainstItself() {
        val weak = book.allLessons.flatMap { l -> l.steps.map { l.id to it } }
            .filter { (_, s) -> Scorer.score(listOf(s.answer.replace("{any}", "sunny")), s).stars != 3 }
            .map { (l, s) -> "$l/${s.id}" }
        assertEquals(emptyList<String>(), weak)
    }

    @Test fun picturesPresentOnPictureSteps() {
        val missing = book.allLessons.flatMap { it.steps }.filter { it.type == StepType.PICTURE && it.picture.isNullOrBlank() }
        assertEquals(emptyList<Step>(), missing)
    }

    @Test fun everyLessonUsesAtLeastTwoActivityTypes() {
        val single = book.allLessons.filter { l -> l.steps.map { it.type }.toSet().size < 2 }.map { it.id }
        assertEquals(emptyList<String>(), single)
    }

    @Test fun neverSaysWrong() {
        val all = book.allLessons.flatMap { it.steps }.flatMap { listOf(it.say, it.reply ?: "") + it.branches.map { b -> b.reply } }
        assertTrue(all.none { Regex("\\bwrong\\b", RegexOption.IGNORE_CASE).containsMatchIn(it) })
    }
}
