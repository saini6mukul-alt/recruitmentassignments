package com.bolo.english.scoring

import org.junit.Assert.assertEquals
import org.junit.Test

class TextNormalizerTest {
    @Test fun lowercasesAndStripsPunctuation() =
        assertEquals(listOf("hello", "tara"), TextNormalizer.words("Hello, Tara!"))

    @Test fun expandsContractions() =
        assertEquals(listOf("it", "is", "a", "cat"), TextNormalizer.words("It's a cat"))

    @Test fun treatsItsWithoutApostropheAsItIs() =
        assertEquals(listOf("it", "is", "a", "cat"), TextNormalizer.words("its a cat"))

    @Test fun curlyApostropheContraction() =
        assertEquals(listOf("i", "am", "happy"), TextNormalizer.words("I’m happy"))

    @Test fun digitsBecomeWords() =
        assertEquals(listOf("i", "am", "seven"), TextNormalizer.words("I am 7"))

    @Test fun regionalSpellingsUnified() {
        assertEquals(TextNormalizer.words("my favourite colour"), TextNormalizer.words("my favorite color"))
        assertEquals(TextNormalizer.words("mummy"), TextNormalizer.words("mom"))
    }

    @Test fun articleAnBecomesA() =
        assertEquals(TextNormalizer.words("a apple"), TextNormalizer.words("an apple"))

    @Test fun emptyTextGivesNoWords() =
        assertEquals(emptyList<String>(), TextNormalizer.words("  ...  "))
}
