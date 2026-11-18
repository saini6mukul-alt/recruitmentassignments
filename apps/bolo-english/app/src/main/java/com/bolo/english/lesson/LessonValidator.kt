package com.bolo.english.lesson

/** Checks lesson content for mistakes. An empty result means the book is valid. */
object LessonValidator {
    const val MIN_STEPS = 5
    const val MAX_STEPS = 8
    private val slot = Regex("\\{([^}]*)\\}")

    fun validate(book: LessonBook): List<String> {
        val errors = mutableListOf<String>()
        duplicates(book.themes.map { it.id }).forEach { errors += "duplicate theme id: $it" }
        duplicates(book.allLessons.map { it.id }).forEach { errors += "duplicate lesson id: $it" }

        for (lesson in book.allLessons) {
            val where = "lesson ${lesson.id}"
            if (lesson.steps.size !in MIN_STEPS..MAX_STEPS) {
                errors += "$where has ${lesson.steps.size} steps (expected $MIN_STEPS-$MAX_STEPS)"
            }
            if (lesson.title.isBlank()) errors += "$where: blank title"
            duplicates(lesson.steps.map { it.id }).forEach { errors += "$where: duplicate step id: $it" }
            for (step in lesson.steps) {
                val at = "$where step ${step.id}"
                if (step.say.isBlank()) errors += "$at: blank say"
                if (step.answer.isBlank()) errors += "$at: blank answer"
                if (step.hindi.isBlank()) errors += "$at: blank hindi"
                for (pattern in listOf(step.answer) + step.accepted) {
                    slot.findAll(pattern).map { it.value }.filter { it != "{any}" }
                        .forEach { errors += "$at: unknown slot $it in \"$pattern\"" }
                }
                step.branches.forEach { b ->
                    if (b.keywords.isEmpty() || b.reply.isBlank()) errors += "$at: incomplete branch"
                }
            }
        }
        return errors
    }

    private fun duplicates(ids: List<String>) = ids.groupBy { it }.filterValues { it.size > 1 }.keys
}
