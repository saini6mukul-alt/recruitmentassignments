package com.bolo.english.progress

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProgressRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun roundTrip() {
        val repo = ProgressRepository(tmp.root.resolve("progress.json"))
        val p = Progress(childName = "Riya", ageBand = "7-9", lessonStars = mapOf("a1" to 3),
            practiceDays = setOf("2026-03-01"), practiceSeconds = 42, missedWords = mapOf("zebra" to 2),
            settings = Settings(slowSpeech = true, hindiHelp = false))
        repo.save(p)
        assertEquals(p, repo.load())
    }

    @Test fun missingFileGivesDefaults() =
        assertEquals(Progress(), ProgressRepository(tmp.root.resolve("none.json")).load())

    @Test fun corruptFileGivesDefaults() {
        val f = tmp.root.resolve("progress.json")
        f.writeText("{not json")
        assertEquals(Progress(), ProgressRepository(f).load())
    }

    @Test fun unknownFieldsIgnored() {
        val f = tmp.root.resolve("progress.json")
        f.writeText("""{"childName":"Aru","futureField":1}""")
        assertEquals("Aru", ProgressRepository(f).load().childName)
    }
}
