package com.bolo.english.translate

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranslationServiceTest {
    private val book = Phrasebook(listOf(Phrase(listOf("मुझे पानी चाहिए"), "I want water.", "चाहिए = want")))
    private var onlineCalls = 0
    private var offlineCalls = 0
    private val online = TextTranslator { hi -> onlineCalls++; Translation(hi, "online: $hi", source = Source.GEMINI) }
    private val offline = TextTranslator { hi -> offlineCalls++; Translation(hi, "offline: $hi", source = Source.MLKIT) }

    @Test fun phrasebookFirst() = runBlocking {
        val t = TranslationService(book, online, offline).translate("मुझे पानी चाहिए।")!!
        assertEquals("I want water.", t.english)
        assertEquals("चाहिए = want", t.explanation)
        assertEquals(Source.PHRASEBOOK, t.source)
        assertEquals(0, onlineCalls + offlineCalls)
    }

    @Test fun onlineWhenNoPhrase() = runBlocking {
        val t = TranslationService(book, online, offline).translate("आसमान नीला है")!!
        assertEquals(Source.GEMINI, t.source)
        assertEquals(0, offlineCalls)
    }

    @Test fun offlineWhenOnlineReturnsNull() = runBlocking {
        val t = TranslationService(book, TextTranslator { null }, offline).translate("आसमान नीला है")!!
        assertEquals(Source.MLKIT, t.source)
    }

    @Test fun offlineWhenOnlineThrows() = runBlocking {
        val t = TranslationService(book, TextTranslator { error("no internet") }, offline).translate("आसमान नीला है")!!
        assertEquals(Source.MLKIT, t.source)
    }

    @Test fun offlineWhenNoOnline() = runBlocking {
        assertEquals(Source.MLKIT, TranslationService(book, null, offline).translate("आसमान नीला है")!!.source)
    }

    @Test fun nullWhenEverythingFails() = runBlocking {
        assertNull(TranslationService(book, null, TextTranslator { error("model missing") }).translate("आसमान नीला है"))
    }

    @Test fun blankInputIsNull() = runBlocking {
        assertNull(TranslationService(book, online, offline).translate("  "))
        assertEquals(0, onlineCalls + offlineCalls)
    }
}
