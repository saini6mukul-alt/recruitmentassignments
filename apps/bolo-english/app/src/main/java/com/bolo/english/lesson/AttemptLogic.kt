package com.bolo.english.lesson

import com.bolo.english.Phase
import com.bolo.english.scoring.ScoreResult

enum class AttemptOutcome { PASS, RETRY, HINDI_HELP, NOT_HEARD }

/** Decides what happens after the child speaks on a step. */
object AttemptLogic {
    const val FAILS_BEFORE_HELP = 2
    const val NOT_HEARD_BEFORE_SKIP = 3

    /** [failedAttempts] = failures on this step before this attempt; [result] is null when nothing was heard. */
    fun next(failedAttempts: Int, result: ScoreResult?, hindiHelpOn: Boolean): AttemptOutcome = when {
        result == null -> AttemptOutcome.NOT_HEARD
        result.passed -> AttemptOutcome.PASS
        failedAttempts + 1 >= FAILS_BEFORE_HELP && hindiHelpOn -> AttemptOutcome.HINDI_HELP
        else -> AttemptOutcome.RETRY
    }

    /** Skip appears after 2 failed tries, or after 3 attempts where nothing could be heard (so a child is never stuck). */
    fun canSkip(failedAttempts: Int, notHeard: Int = 0): Boolean =
        failedAttempts >= FAILS_BEFORE_HELP || notHeard >= NOT_HEARD_BEFORE_SKIP

    /** Where the player goes after Tara finishes talking: a passed step shows Next, otherwise the mic. */
    fun phaseAfterTalk(result: ScoreResult?): Phase = if (result?.passed == true) Phase.FEEDBACK else Phase.WAITING
}
