package com.bolo.english.progress

import com.bolo.english.lesson.Lesson
import com.bolo.english.lesson.LessonBook
import com.bolo.english.lesson.Theme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProgressTest {
    private val today = LocalDate.of(2026, 3, 1)

    private fun days(vararg d: String) = Progress(practiceDays = d.toSet())

    @Test fun completeLessonKeepsBestStars() {
        var p = ProgressLogic.completeLesson(Progress(), "l1", 2, today, 60)
        p = ProgressLogic.completeLesson(p, "l1", 1, today, 30)
        assertEquals(2, p.lessonStars["l1"])
        p = ProgressLogic.completeLesson(p, "l1", 3, today, 30)
        assertEquals(3, p.lessonStars["l1"])
        assertEquals(120L, p.practiceSeconds)
        assertEquals(setOf("2026-03-01"), p.practiceDays)
        assertEquals(3, p.totalStars)
    }

    @Test fun streakCountsBackFromToday() =
        assertEquals(3, ProgressLogic.streak(days("2026-02-27", "2026-02-28", "2026-03-01"), today))

    @Test fun streakStillAliveIfLastPractiseWasYesterday() =
        assertEquals(2, ProgressLogic.streak(days("2026-02-27", "2026-02-28"), today))

    @Test fun streakBrokenByGap() =
        assertEquals(1, ProgressLogic.streak(days("2026-02-26", "2026-03-01"), today))

    @Test fun streakZeroWhenLastPractiseIsOld() =
        assertEquals(0, ProgressLogic.streak(days("2026-02-26"), today))

    @Test fun streakAcrossYearBoundary() =
        assertEquals(2, ProgressLogic.streak(days("2025-12-31", "2026-01-01"), LocalDate.of(2026, 1, 1)))

    @Test fun recordMissesAndTopMissed() {
        var p = ProgressLogic.recordMisses(Progress(), listOf("Elephant", "is"))
        p = ProgressLogic.recordMisses(p, listOf("elephant", "zebra"))
        assertEquals(listOf("elephant" to 2, "is" to 1, "zebra" to 1), ProgressLogic.topMissed(p))
        assertEquals(1, ProgressLogic.topMissed(p, 1).size)
    }

    private fun lesson(id: String) = Lesson(id, id, "📘", "⭐", emptyList())
    private val book = LessonBook(listOf(
        Theme("t1", "A", "🅰", listOf(lesson("a1"), lesson("a2"), lesson("a3"))),
        Theme("t2", "B", "🅱", listOf(lesson("b1"), lesson("b2"))),
    ))

    @Test fun everyLessonIsUnlockedFromTheStart() {
        listOf("a1", "a2", "a3", "b1", "b2").forEach { assertTrue(it, ProgressLogic.isUnlocked(book, Progress(), it)) }
    }

    @Test fun onboardingFlag() {
        assertFalse(Progress().isOnboarded)
        assertTrue(Progress(childName = "Riya").isOnboarded)
    }
}
