package com.bolo.english.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhrasebookTest {
    private val book = Phrasebook(listOf(
        Phrase(listOf("मुझे पानी चाहिए"), "I want water.", "चाहिए = want"),
        Phrase(listOf("मुझे पानी नहीं चाहिए"), "I do not want water.", "नहीं चाहिए = do not want"),
        Phrase(listOf("मैं स्कूल जा रहा हूँ", "मैं स्कूल जा रही हूँ"), "I am going to school."),
        Phrase(listOf("मुझे भूख लगी है"), "I am hungry."),
    ))

    @Test fun normalizerRemovesPunctuationAndSpacing() =
        assertEquals("मुझे पानी चाहिए", HindiNormalizer.normalize("  मुझे   पानी चाहिए।  "))

    @Test fun normalizerUnifiesNuktaAndChandrabindu() {
        assertEquals(HindiNormalizer.normalize("ज़रूर"), HindiNormalizer.normalize("जरूर"))
        assertEquals(HindiNormalizer.normalize("हूँ"), HindiNormalizer.normalize("हूं"))
        assertEquals(HindiNormalizer.normalize("फ़ल"), HindiNormalizer.normalize("फ़ल"))
    }

    @Test fun normalizerUnifiesYeSpelling() =
        assertEquals(HindiNormalizer.normalize("चाहिए"), HindiNormalizer.normalize("चाहिये"))

    @Test fun exactMatch() = assertEquals("I want water.", book.match("मुझे पानी चाहिए")?.en)

    @Test fun matchesWithPunctuationAndSpellingVariants() =
        assertEquals("I want water.", book.match("मुझे पानी चाहिये?")?.en)

    @Test fun matchesAnyVariant() = assertEquals("I am going to school.", book.match("मैं स्कूल जा रही हूं")?.en)

    @Test fun fillerWordStillMatches() = assertEquals("I am hungry.", book.match("अरे मुझे भूख लगी है")?.en)

    @Test fun negationNeverMatchesPositivePhrase() =
        assertEquals("I do not want water.", book.match("मुझे पानी नहीं चाहिए")?.en)

    @Test fun extraMeaningfulWordDoesNotMatch() = assertNull(book.match("मम्मी मुझे पानी चाहिए"))

    @Test fun unrelatedSentenceDoesNotMatch() = assertNull(book.match("आसमान नीला है"))

    @Test fun blankDoesNotMatch() = assertNull(book.match("   "))

    @Test fun parsesJson() {
        val b = Phrasebook.parse("""{"phrases":[{"hi":["नमस्ते"],"en":"Hello.","tip":"नमस्ते = hello"}]}""")
        assertEquals("Hello.", b.match("नमस्ते")?.en)
        assertEquals("नमस्ते = hello", b.match("नमस्ते")?.tip)
    }

    @Test fun genderFormIsNeverConfused() {
        val b = Phrasebook(listOf(Phrase(listOf("मैं लड़का हूँ"), "I am a boy.")))
        assertNull(b.match("मैं लड़की हूँ"))
    }

    @Test fun meaningfulWordAchhaIsNotIgnored() {
        val b = Phrasebook(listOf(Phrase(listOf("बहुत अच्छा"), "Very good.")))
        assertEquals("Very good.", b.match("बहुत अच्छा")?.en)
    }
}
