package com.bolo.english.progress

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SavedSentenceTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun s(en: String, at: Long = 1, stars: Int = 0) = SavedSentence(hindi = "हिंदी $en", english = en, stars = stars, addedAt = at)

    @Test fun newestFirst() {
        var p = ProgressLogic.saveSentence(Progress(), s("I want water.", 1))
        p = ProgressLogic.saveSentence(p, s("I am hungry.", 2))
        assertEquals(listOf("I am hungry.", "I want water."), p.savedSentences.map { it.english })
    }

    @Test fun duplicateEnglishMovesToTopAndKeepsBestStars() {
        var p = ProgressLogic.saveSentence(Progress(), s("I want water.", 1, stars = 3))
        p = ProgressLogic.saveSentence(p, s("I am hungry.", 2))
        p = ProgressLogic.saveSentence(p, s("i want water", 3, stars = 1))
        assertEquals(2, p.savedSentences.size)
        assertEquals("i want water", p.savedSentences.first().english)
        assertEquals(3, p.savedSentences.first().stars)
    }

    @Test fun capAt200() {
        var p = Progress()
        repeat(205) { p = ProgressLogic.saveSentence(p, s("Sentence $it.", it.toLong())) }
        assertEquals(200, p.savedSentences.size)
        assertEquals("Sentence 204.", p.savedSentences.first().english)
    }

    @Test fun updateStarsKeepsBest() {
        var p = ProgressLogic.saveSentence(Progress(), s("I want water.", 1, stars = 2))
        p = ProgressLogic.updateSentenceStars(p, "I want water.", 1)
        assertEquals(2, p.savedSentences.single().stars)
        p = ProgressLogic.updateSentenceStars(p, "I want water.", 3)
        assertEquals(3, p.savedSentences.single().stars)
    }

    @Test fun repositoryRoundTripWithNewFields() {
        val repo = ProgressRepository(tmp.root.resolve("p.json"))
        val p = ProgressLogic.saveSentence(Progress(childName = "Riya", settings = Settings(geminiKey = "k", geminiModel = "gemini-x")), s("Hi.", 5, 2))
        repo.save(p)
        assertEquals(p, repo.load())
    }

    @Test fun version1FileStillLoads() {
        val f = tmp.root.resolve("p.json")
        f.writeText("""{"childName":"Riya","ageBand":"7-9","lessonStars":{"hello":3},"practiceDays":["2026-11-18"],"practiceSeconds":40,"missedWords":{},"settings":{"slowSpeech":false,"hindiHelp":true}}""")
        val p = ProgressRepository(f).load()
        assertEquals("Riya", p.childName)
        assertEquals(3, p.lessonStars["hello"])
        assertEquals("", p.settings.geminiKey)
        assertEquals(emptyList<SavedSentence>(), p.savedSentences)
    }
}
