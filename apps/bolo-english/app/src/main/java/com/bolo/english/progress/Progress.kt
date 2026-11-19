package com.bolo.english.progress

import com.bolo.english.lesson.LessonBook
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class Settings(
    val slowSpeech: Boolean = false,
    val hindiHelp: Boolean = true,
    /** Optional free Gemini API key for better explanations and "Tara से बातें". */
    val geminiKey: String = "",
    /** Gemini model chosen automatically for this key (cached). */
    val geminiModel: String = "",
)

/** A sentence the child translated from Hindi, kept for practice. */
@Serializable
data class SavedSentence(val hindi: String, val english: String, val stars: Int = 0, val addedAt: Long = 0)

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
    val savedSentences: List<SavedSentence> = emptyList(),
) {
    val isOnboarded: Boolean get() = childName.isNotBlank()
    val totalStars: Int get() = lessonStars.values.sum()
}

object ProgressLogic {
    const val MAX_SAVED = 200

    private fun key(english: String) = english.lowercase().filter { it.isLetterOrDigit() }

    /** Adds [s] at the top; an existing sentence with the same English is replaced, keeping its best stars. */
    fun saveSentence(p: Progress, s: SavedSentence): Progress {
        val old = p.savedSentences.firstOrNull { key(it.english) == key(s.english) }
        val merged = s.copy(stars = maxOf(s.stars, old?.stars ?: 0))
        val rest = p.savedSentences.filter { key(it.english) != key(s.english) }
        return p.copy(savedSentences = (listOf(merged) + rest).take(MAX_SAVED))
    }

    fun updateSentenceStars(p: Progress, english: String, stars: Int): Progress =
        p.copy(savedSentences = p.savedSentences.map {
            if (key(it.english) == key(english)) it.copy(stars = maxOf(it.stars, stars)) else it
        })

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

    /** Every lesson is open from the start (owner's choice: nothing locked). */
    fun isUnlocked(book: LessonBook, @Suppress("UNUSED_PARAMETER") p: Progress, lessonId: String): Boolean =
        book.allLessons.any { it.id == lessonId }

}
