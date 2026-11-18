package com.bolo.english.lesson

import com.bolo.english.scoring.ScoreResult

enum class AttemptOutcome { PASS, RETRY, HINDI_HELP, NOT_HEARD }

/** Decides what happens after the child speaks on a step. */
object AttemptLogic {
    const val FAILS_BEFORE_HELP = 2

    /** [failedAttempts] = failures on this step before this attempt; [result] is null when nothing was heard. */
    fun next(failedAttempts: Int, result: ScoreResult?, hindiHelpOn: Boolean): AttemptOutcome = when {
        result == null -> AttemptOutcome.NOT_HEARD
        result.passed -> AttemptOutcome.PASS
        failedAttempts + 1 >= FAILS_BEFORE_HELP && hindiHelpOn -> AttemptOutcome.HINDI_HELP
        else -> AttemptOutcome.RETRY
    }

    fun canSkip(failedAttempts: Int): Boolean = failedAttempts >= FAILS_BEFORE_HELP
}
