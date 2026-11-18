package com.bolo.english.lesson

import com.bolo.english.scoring.ScoreResult
import org.junit.Assert.assertEquals
import org.junit.Test

class AttemptLogicTest {
    private val pass = ScoreResult(2, 0.8, emptyList())
    private val fail = ScoreResult(0, 0.2, emptyList())

    @Test fun passedAttempt() = assertEquals(AttemptOutcome.PASS, AttemptLogic.next(0, pass, true))
    @Test fun firstFailRetries() = assertEquals(AttemptOutcome.RETRY, AttemptLogic.next(0, fail, true))
    @Test fun secondFailGivesHindiHelp() = assertEquals(AttemptOutcome.HINDI_HELP, AttemptLogic.next(1, fail, true))
    @Test fun laterFailsKeepHindiHelp() = assertEquals(AttemptOutcome.HINDI_HELP, AttemptLogic.next(4, fail, true))
    @Test fun secondFailRetriesWhenHindiOff() = assertEquals(AttemptOutcome.RETRY, AttemptLogic.next(1, fail, false))
    @Test fun nothingHeardIsNotAFailure() = assertEquals(AttemptOutcome.NOT_HEARD, AttemptLogic.next(1, null, true))
    @Test fun skipShownAfterTwoFails() {
        assertEquals(false, AttemptLogic.canSkip(1))
        assertEquals(true, AttemptLogic.canSkip(2))
    }
}
