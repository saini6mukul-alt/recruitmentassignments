package com.bolo.english.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Checks the real phrasebook shipped in the app. */
class PhrasebookContentTest {
    private val book = Phrasebook.parse(File("src/main/assets/phrasebook.json").readText())

    @Test fun hasEnoughPhrases() = assertTrue("${book.phrases.size}", book.phrases.size >= 120)

    @Test fun noBlankEnglish() = assertEquals(emptyList<Phrase>(), book.phrases.filter { it.en.isBlank() || it.hi.isEmpty() })

    @Test fun noDuplicateHindiVariants() {
        val all = book.phrases.flatMap { p -> p.hi.map { HindiNormalizer.normalize(it) } }
        assertEquals(emptyList<String>(), all.groupBy { it }.filterValues { it.size > 1 }.keys.toList())
    }

    @Test fun everyVariantMatchesItsOwnPhrase() {
        val bad = book.phrases.flatMap { p -> p.hi.filter { book.match(it)?.en != p.en } }
        assertEquals(emptyList<String>(), bad)
    }
}
