package com.bolo.english.scoring

import com.bolo.english.lesson.Step

data class WordMark(val word: String, val heard: Boolean)

data class ScoreResult(val stars: Int, val ratio: Double, val marks: List<WordMark>) {
    val passed: Boolean get() = stars >= 1
}

/** Compares what the child said with a step's expected answers. */
object Scorer {
    private const val SLOT = "{any}"
    private val fillers = setOf("um", "umm", "uh", "uhh", "hmm", "ah", "er", "erm", "mm")

    fun starsFor(ratio: Double): Int = when {
        ratio >= 0.9 -> 3
        ratio >= 0.7 -> 2
        ratio >= 0.4 -> 1
        else -> 0
    }

    /** [heard] holds the recogniser's alternatives, best guess first. */
    fun score(heard: List<String>, step: Step): ScoreResult {
        val alternatives = heard.map { TextNormalizer.words(it) }.filter { it.isNotEmpty() }
        val patterns = (listOf(step.answer) + step.accepted).map { patternUnits(it) }

        var bestRatio = 0.0
        var bestAlternative: List<String> = emptyList()
        for (alt in alternatives) for (pattern in patterns) {
            val r = ratio(alt, pattern)
            if (r > bestRatio) { bestRatio = r; bestAlternative = alt }
        }
        return ScoreResult(starsFor(bestRatio), bestRatio, marks(step.answer, bestAlternative))
    }

    /** How a model answer is shown on screen: slots become a blank. */
    fun display(answer: String): String = answer.replace(SLOT, "___")

    /** How Tara says a model answer aloud: slots become a pause. */
    fun speakable(answer: String): String = answer.replace(SLOT, "...")

    /** Tara's next line for a role-play step: first branch with a heard keyword, else the default reply. */
    fun chooseReply(heard: List<String>, step: Step): String? {
        val words = heard.flatMap { TextNormalizer.words(it) }.toSet()
        return step.branches.firstOrNull { b -> b.keywords.any { k -> TextNormalizer.words(k).any { it in words } } }
            ?.reply ?: step.reply
    }

    private fun patternUnits(pattern: String): List<String> =
        pattern.split(Regex("\\s+")).flatMap { if (it == SLOT) listOf(SLOT) else TextNormalizer.words(it) }

    private fun ratio(heard: List<String>, units: List<String>): Double {
        if (units.isEmpty()) return 0.0
        val remaining = heard.filter { it !in fillers }.toMutableList()
        val patternWords = units.filter { it != SLOT }.toSet()
        var matched = 0
        for (unit in units.filter { it != SLOT }) {
            val i = remaining.indexOfFirst { similar(it, unit) }
            if (i >= 0) { matched++; remaining.removeAt(i) }
        }
        val slots = units.count { it == SLOT }
        matched += minOf(slots, remaining.count { it !in patternWords })
        return matched.toDouble() / units.size
    }

    private fun marks(answer: String, heard: List<String>): List<WordMark> {
        val remaining = heard.filter { it !in fillers }.toMutableList()
        val tokens = answer.split(Regex("\\s+"))
        val answerWords = tokens.filter { it != SLOT }.flatMap { TextNormalizer.words(it) }.toSet()
        return tokens.mapNotNull { token ->
            if (token == SLOT) {
                val i = remaining.indexOfFirst { it !in answerWords }
                if (i >= 0) remaining.removeAt(i)
                return@mapNotNull WordMark("___", i >= 0)
            }
            val display = token.trim { !it.isLetterOrDigit() && it != '\'' }
            if (display.isEmpty()) return@mapNotNull null
            val parts = TextNormalizer.words(token)
            val ok = parts.isNotEmpty() && parts.all { part ->
                val i = remaining.indexOfFirst { similar(it, part) }
                if (i >= 0) { remaining.removeAt(i); true } else false
            }
            WordMark(display, ok)
        }
    }

    /** Exact for short words; 1 edit allowed from 4 letters, 2 edits from 7 letters. */
    private fun similar(a: String, b: String): Boolean {
        if (a == b) return true
        val shorter = minOf(a.length, b.length)
        val allowed = when {
            shorter >= 7 -> 2
            shorter >= 4 -> 1
            else -> 0
        }
        return allowed > 0 && editDistance(a, b) <= allowed
    }

    private fun editDistance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            prev = cur
        }
        return prev[b.length]
    }
}
