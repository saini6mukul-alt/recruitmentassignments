package com.bolo.english.scoring

/** Turns spoken or written text into comparable lowercase words. */
object TextNormalizer {
    private val contractions = mapOf(
        "it's" to "it is", "its" to "it is", "i'm" to "i am", "what's" to "what is", "that's" to "that is",
        "he's" to "he is", "she's" to "she is", "there's" to "there is", "let's" to "let us",
        "don't" to "do not", "doesn't" to "does not", "can't" to "can not", "cannot" to "can not",
        "isn't" to "is not", "aren't" to "are not", "i'll" to "i will", "you're" to "you are",
        "we're" to "we are", "they're" to "they are", "i've" to "i have", "i'd" to "i would",
        "where's" to "where is", "who's" to "who is", "how's" to "how is",
    )
    private val numbers = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen",
        "nineteen", "twenty",
    )
    private val variants = mapOf(
        "color" to "colour", "colors" to "colours", "favorite" to "favourite",
        "mom" to "mummy", "mommy" to "mummy", "mum" to "mummy", "mama" to "mummy",
        "dad" to "daddy", "papa" to "daddy", "gray" to "grey", "okay" to "ok", "an" to "a",
    )

    fun words(text: String): List<String> {
        val cleaned = text.lowercase()
            .replace('’', '\'').replace('‘', '\'')
            .replace(Regex("[^a-z0-9' ]"), " ")
        return cleaned.split(' ')
            .map { it.trim('\'') }
            .filter { it.isNotEmpty() }
            .flatMap { token -> (contractions[token] ?: token).split(' ') }
            .map { token -> token.toIntOrNull()?.let { numbers.getOrNull(it) } ?: token }
            .map { variants[it] ?: it }
            .filter { it.isNotEmpty() && it != "'" }
    }
}
