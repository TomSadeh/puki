package com.olamkatan.brain.voice

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** The BlueTTS text steps (the full output was checked against the Python, see puki-bench/voicetest). */
class VoiceTextTest {
    @Test
    fun collapsesRepeatedPunctuation() {
        assertEquals("יש!", VoiceText.prepareTextForSynthesis("יש!!!"))
        assertEquals("מה?", VoiceText.prepareTextForSynthesis("מה???"))
    }

    @Test
    fun dropsHyphensInsideHebrewWords() {
        assertEquals("טהדה!", VoiceText.prepareTextForSynthesis("טה-דה!"))
    }

    @Test
    fun squeezesSpaces() {
        assertEquals("שלום חבר", VoiceText.prepareTextForSynthesis("  שלום    חבר  "))
    }

    @Test
    fun stripsLanguageTags() {
        assertEquals("shalom haver", VoiceText.stripLangTags("<he>shalom</he>   <he>haver</he>"))
    }

    @Test
    fun chunksBySentence() {
        val chunks = VoiceText.chunkText("One two. Three four. Five six.", maxLen = 12)
        assertEquals(listOf("One two.", "Three four.", "Five six."), chunks)
        assertEquals(listOf("One two. Three four."), VoiceText.chunkText("One two. Three four.", maxLen = 300))
    }

    @Test
    fun encodeEndsWithPunctuationAndPadsUnknowns() {
        val ids = mapOf("a" to 1, "b" to 2, "." to 3, "!" to 4)
        assertArrayEquals(intArrayOf(1, 2, 3), VoiceText.encode("ab", ids, 0))
        assertArrayEquals(intArrayOf(1, 4), VoiceText.encode("a!", ids, 0))
        assertArrayEquals(intArrayOf(1, 0, 3), VoiceText.encode("az", ids, 0))
    }
}
