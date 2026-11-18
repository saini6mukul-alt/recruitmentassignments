package com.bolo.english.lesson

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class StepType {
    @SerialName("repeat") REPEAT,
    @SerialName("picture") PICTURE,
    @SerialName("question") QUESTION,
    @SerialName("roleplay") ROLEPLAY,
}

/** Tara replies with [reply] when any of [keywords] is heard. */
@Serializable
data class Branch(val keywords: List<String>, val reply: String)

/**
 * One activity in a lesson.
 * [say] is what Tara says, [answer] is the model sentence (also an accepted answer),
 * [accepted] are extra accepted patterns where `{any}` is a free slot.
 */
@Serializable
data class Step(
    val id: String,
    val type: StepType,
    val say: String,
    val picture: String? = null,
    val answer: String,
    val accepted: List<String> = emptyList(),
    val hindi: String,
    val branches: List<Branch> = emptyList(),
    val reply: String? = null,
)

@Serializable
data class Lesson(
    val id: String,
    val title: String,
    val emoji: String,
    val sticker: String,
    val steps: List<Step>,
)

@Serializable
data class Theme(val id: String, val title: String, val emoji: String, val lessons: List<Lesson>)

@Serializable
data class LessonBook(val themes: List<Theme>) {
    val allLessons: List<Lesson> get() = themes.flatMap { it.lessons }
}

object LessonParser {
    private val json = Json { ignoreUnknownKeys = true }
    fun parse(text: String): LessonBook = json.decodeFromString(LessonBook.serializer(), text)
}
