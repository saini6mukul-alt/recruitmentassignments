package com.bolo.english.scoring

import com.bolo.english.lesson.Branch
import com.bolo.english.lesson.Step
import com.bolo.english.lesson.StepType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScorerTest {
    private fun step(answer: String, accepted: List<String> = emptyList(), branches: List<Branch> = emptyList(), reply: String? = null) =
        Step(id = "s", type = StepType.PICTURE, say = "What is this?", answer = answer, accepted = accepted,
            hindi = "यह क्या है?", branches = branches, reply = reply)

    @Test fun exactMatchGivesThreeStars() {
        val r = Scorer.score(listOf("It is an elephant"), step("It is an elephant."))
        assertEquals(3, r.stars)
        assertTrue(r.marks.all { it.heard })
        assertTrue(r.passed)
    }

    @Test fun oneMissingWordOfFiveGivesTwoStars() {
        val r = Scorer.score(listOf("the cat is sleeping"), step("The cat is sleeping now"))
        assertEquals(0.8, r.ratio, 0.001)
        assertEquals(2, r.stars)
        assertEquals(listOf(true, true, true, true, false), r.marks.map { it.heard })
        assertEquals("now", r.marks.last().word)
    }

    @Test fun fillerAndExtraWordsAreIgnored() =
        assertEquals(3, Scorer.score(listOf("umm it is a cat yes"), step("It is a cat")).stars)

    @Test fun digitAnswerMatchesWordAnswer() =
        assertEquals(3, Scorer.score(listOf("I am 7"), step("I am seven")).stars)

    @Test fun americanSpellingMatches() =
        assertEquals(3, Scorer.score(listOf("my favorite color is red"), step("My favourite colour is red")).stars)

    @Test fun bestAlternativeWins() {
        val r = Scorer.score(listOf("it is a hat", "it is a cat"), step("It is a cat"))
        assertEquals(3, r.stars)
    }

    @Test fun acceptedPatternWithSlotMatches() {
        val s = step("My favourite colour is blue", accepted = listOf("my favourite colour is {any}"))
        assertEquals(3, Scorer.score(listOf("my favourite colour is purple"), s).stars)
    }

    @Test fun slotNeedsAnExtraWord() {
        val s = step("My name is Tara", accepted = listOf("my name is {any}"))
        val r = Scorer.score(listOf("my name is"), s)
        assertEquals(0.75, r.ratio, 0.001)
        assertEquals(2, r.stars)
    }

    @Test fun smallMispronunciationTolerated() =
        assertEquals(3, Scorer.score(listOf("it is an elefant"), step("It is an elephant")).stars)

    @Test fun shortWordsMustMatchExactly() =
        assertEquals(2, Scorer.score(listOf("it is a hat"), step("It is a cat")).stars)

    @Test fun repeatedWordCountsOnce() {
        val r = Scorer.score(listOf("cat"), step("cat cat"))
        assertEquals(0.5, r.ratio, 0.001)
    }

    @Test fun nothingHeardGivesZero() {
        val r = Scorer.score(emptyList(), step("It is a cat"))
        assertEquals(0, r.stars)
        assertFalse(r.passed)
        assertTrue(r.marks.none { it.heard })
    }

    @Test fun starThresholds() {
        assertEquals(3, Scorer.starsFor(0.9))
        assertEquals(2, Scorer.starsFor(0.89))
        assertEquals(2, Scorer.starsFor(0.7))
        assertEquals(1, Scorer.starsFor(0.4))
        assertEquals(0, Scorer.starsFor(0.39))
    }

    @Test fun marksKeepOriginalWordsWithoutPunctuation() {
        val r = Scorer.score(listOf("hello tara"), step("Hello, Tara!"))
        assertEquals(listOf("Hello", "Tara"), r.marks.map { it.word })
    }

    @Test fun chooseReplyByKeyword() {
        val s = step("I want a mango", branches = listOf(
            Branch(listOf("mango"), "Yummy mango!"), Branch(listOf("banana", "bananas"), "Here is a banana!")),
            reply = "Here you go!")
        assertEquals("Here is a banana!", Scorer.chooseReply(listOf("i want bananas"), s))
        assertEquals("Yummy mango!", Scorer.chooseReply(listOf("Mango please"), s))
        assertEquals("Here you go!", Scorer.chooseReply(listOf("apple"), s))
    }

    @Test fun chooseReplyWithoutBranchesOrReplyIsNull() =
        assertNull(Scorer.chooseReply(listOf("apple"), step("apple")))

    @Test fun slotInModelAnswerIsMarkedAsBlank() {
        val s = step("My name is {any}")
        val r = Scorer.score(listOf("my name is Riya"), s)
        assertEquals(3, r.stars)
        assertEquals(listOf("My", "name", "is", "___"), r.marks.map { it.word })
        assertTrue(r.marks.all { it.heard })
        assertFalse(Scorer.score(listOf("my name is"), s).marks.last().heard)
    }

    @Test fun displayAndSpeechFormsOfSlotAnswer() {
        assertEquals("My name is ___", Scorer.display("My name is {any}"))
        assertEquals("My name is ...", Scorer.speakable("My name is {any}"))
        assertEquals("It is a cat.", Scorer.speakable("It is a cat."))
    }
}
