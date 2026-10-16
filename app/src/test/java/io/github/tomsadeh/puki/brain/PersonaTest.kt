package io.github.tomsadeh.puki.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The filter every line Puki says passes, and the word check for Say it with Puki. */
class PersonaTest {
    private val persona = Persona()

    @Test
    fun keepsAWarmShortLine() {
        assertEquals("הפרה רעבה!", persona.clean("הפרה רעבה!", "he"))  // "רע" inside a word is fine
        assertEquals("The cow is hungry!", persona.clean("The cow is hungry!", "en"))
    }

    @Test
    fun stripsTagsQuotesAndExtraLines() {
        assertEquals("שלום!", persona.clean("\"<b>שלום!</b>\"", "he"))
        assertEquals("Hi there!", persona.clean("Hi there!\nAnd a second line", "en"))
    }

    @Test
    fun rejectsHarshWords() {
        assertNull(persona.clean("זו טעות.", "he"))
        assertNull(persona.clean("והטעות קטנה.", "he"))  // with a prefix
        assertNull(persona.clean("לא נכון!", "he"))
        assertNull(persona.clean("That's wrong!", "en"))
        assertNull(persona.clean("Not right, try again.", "en"))
    }

    @Test
    fun rejectsLongLinesAndOtherScripts() {
        assertNull(persona.clean("מילה ".repeat(15).trim() + ".", "he"))
        assertNull(persona.clean("x".repeat(91), "en"))
        assertNull(persona.clean("Hello שלום", "en"))
        assertNull(persona.clean("", "he"))
    }

    @Test
    fun dropsComplimentsAboutTheChild() {
        assertEquals("הפרה אכלה.", persona.clean("את כל כך חכמה! הפרה אכלה.", "he"))
        assertEquals("The cow ate.", persona.clean("You're so smart! The cow ate.", "en"))
        // A compliment-like start that isn't one stays
        assertEquals("You are here!", persona.clean("You are here!", "en"))
    }

    @Test
    fun voiceSwapsMisreadWordsAndDropsNikud() {
        assertEquals("יש!", persona.forVoice("הידד!"))
        assertEquals("ויש", persona.forVoice("והידד"))
        assertEquals("שלום", persona.forVoice("שָׁלוֹם"))
        assertEquals("הידדים", persona.forVoice("הידדים"))  // whole words only
    }

    @Test
    fun childNameLosesNikud() {
        persona.setChild("  שָׁלוֹם ", "girl")
        assertEquals("שלום", persona.name)
        persona.setChild("", "boy")
        assertEquals("the little boy", persona.child("en"))
    }

    @Test
    fun wordMatchIsGenerous() {
        assertEquals("word", persona.wordMatch("הכלב", "כלב"))
        assertEquals("word", persona.wordMatch("the dog", "dog"))
        assertEquals("word", persona.wordMatch("כלבים", "כלב"))
        assertEquals("close", persona.wordMatch("פה", "פרה"))         // the first sound
        assertEquals("close", persona.wordMatch("הב הב", "כלב", listOf("הב")))  // its sound
        assertEquals("tried", persona.wordMatch("בננה", "כלב"))
        assertEquals("tried", persona.wordMatch("", "כלב"))
        assertEquals("word", persona.wordMatch("סוסים", "סוס"))
        assertEquals("word", persona.wordMatch("פרים", "פרים"))
    }

    @Test
    fun finalLettersMatchTheirPlainForm() {
        assertEquals("word", persona.wordMatch("ספרים", "ספרימ"))
    }

    @Test
    fun similarityMatchesPythonDifflib() {
        // difflib.SequenceMatcher(None, a, b).ratio()
        assertEquals(0.75, Persona.similarity("abcd", "abce"), 1e-9)
        assertEquals(0.0, Persona.similarity("abc", "xyz"), 1e-9)
        assertEquals(1.0, Persona.similarity("", ""), 1e-9)
        assertEquals(2.0 * 2 / 9, Persona.similarity("פרה", "פרפרים"), 1e-9)  // "פר", then nothing
    }
}
