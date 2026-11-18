package com.bolo.english.progress

import com.bolo.english.lesson.LessonBook
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class Settings(val slowSpeech: Boolean = false, val hindiHelp: Boolean = true)

/** Everything the app remembers about the child. Stored only on the phone. */
@Serializable
data class Progress(
    val childName: String = "",
    val ageBand: String = "4-6",
    val lessonStars: Map<String, Int> = emptyMap(),
    val practiceDays: Set<String> = emptySet(),
    val practiceSeconds: Long = 0,
    val missedWords: Map<String, Int> = emptyMap(),
    val settings: Settings = Settings(),
) {
    val isOnboarded: Boolean get() = childName.isNotBlank()
    val totalStars: Int get() = lessonStars.values.sum()
}

object ProgressLogic {
    fun completeLesson(p: Progress, lessonId: String, stars: Int, today: LocalDate, seconds: Long): Progress =
        p.copy(
            lessonStars = p.lessonStars + (lessonId to maxOf(stars, p.lessonStars[lessonId] ?: 0)),
            practiceDays = p.practiceDays + today.toString(),
            practiceSeconds = p.practiceSeconds + seconds,
        )

    fun recordMisses(p: Progress, words: List<String>): Progress {
        val counts = p.missedWords.toMutableMap()
        words.map { it.lowercase() }.filter { it.isNotBlank() }.forEach { counts[it] = (counts[it] ?: 0) + 1 }
        return p.copy(missedWords = counts)
    }

    fun topMissed(p: Progress, n: Int = 10): List<Pair<String, Int>> =
        p.missedWords.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(n)
            .map { it.key to it.value }

    /** Consecutive practice days ending today, or yesterday if today has no practice yet. */
    fun streak(p: Progress, today: LocalDate): Int {
        val days = p.practiceDays.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
        var day = when {
            today in days -> today
            today.minusDays(1) in days -> today.minusDays(1)
            else -> return 0
        }
        var count = 0
        while (day in days) { count++; day = day.minusDays(1) }
        return count
    }

    /** The first lesson of each theme is open; each next lesson opens when the previous one is completed. */
    fun isUnlocked(book: LessonBook, p: Progress, lessonId: String): Boolean {
        for (theme in book.themes) {
            val index = theme.lessons.indexOfFirst { it.id == lessonId }
            if (index == 0) return true
            if (index > 0) return theme.lessons[index - 1].id in p.lessonStars
        }
        return false
    }
}
