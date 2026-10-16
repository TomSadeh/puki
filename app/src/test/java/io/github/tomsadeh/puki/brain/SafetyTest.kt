package io.github.tomsadeh.puki.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The safety nets for what the child says to Puki (the PC's tests/test_safety.py, same cases). */
class SafetyTest {
    @Test
    fun catches() {
        val cases = listOf(
            Triple("כואב לי", "he", "hurt"),
            Triple("וכואב לי בבטן", "he", "hurt"),  // with a prefix letter
            Triple("יש דם", "he", "hurt"),
            Triple("אני רוצה אמא", "he", "parent"),
            Triple("בוא נשחק עם סכין", "he", "danger"),
            Triple("בוא נדליק אש", "he", "danger"),
            Triple("אני רוצה לאכול את התרופה", "he", "danger"),
            Triple("הכלב מת?", "he", "big_question"),
            Triple("בוא נסתיר מאמא", "he", "secrets"),
            Triple("די פוקי! שקט!", "he", "quiet"),
            Triple("אני לא רוצה ללכת לישון", "he", "bedtime"),
            Triple("אתה טיפש", "he", "unkind"),
            Triple("אבא כועס עליי", "he", "feelings"),
            Triple("I'm sad", "en", "feelings"),
            Triple("אתה אוהב אותי?", "he", "love"),
            Triple("I fell down", "en", "hurt"),
            Triple("let's play with a knife", "en", "danger"),
            Triple("is the doggy dead?", "en", "big_question"),
            Triple("I want mommy", "en", "parent"),
            Triple("buy me a game", "en", "buying"),
        )
        for ((heard, lang, cat) in cases) assertEquals(heard, cat, Safety.guard(heard, lang, girl = true)?.first)
    }

    @Test
    fun ordinaryPlayPasses() {
        val cases = listOf(
            "אני רוצה מתוק" to "he",        // "מת" (dead) is a whole word only
            "מתנה לי" to "he",
            "שמש גדולה" to "he",
            "ראש" to "he",
            "אני אוהבת פרה" to "he",
            "פרה!" to "he",
            "a warm hug" to "en",            // not "war"
            "I love the pillow" to "en",     // not "pill"
            "let us match colors" to "en",   // not "matches"
            "the doggy says woof" to "en",
        )
        for ((heard, lang) in cases) assertNull(heard, Safety.guard(heard, lang, girl = true))
    }

    @Test
    fun repliesFollowTheChildsGender() {
        assertEquals(1, Regex("בואי").findAll(Safety.guard("כואב לי", "he", girl = true)!!.second).count())
        assertTrue("בוא " in Safety.guard("כואב לי", "he", girl = false)!!.second)
        assertFalse("בואי" in Safety.guard("כואב לי", "he", girl = false)!!.second)
    }

    @Test
    fun everyCategoryHasAReplyInBothLanguages() {
        for (lang in listOf("he", "en")) for (girl in listOf(true, false)) {
            val r = Safety.replies(lang, girl)
            assertTrue(r.keys.containsAll(Safety.RULES.map { it.category }))
            assertTrue(r.keys.containsAll(Safety.GATE_CATEGORY.values))
        }
    }

    @Test
    fun sameRepliesAsThePc() {
        // Spot checks against brain/safety.py's text, so the two ports don't drift
        assertEquals("זה מסוכן! רק עם מבוגר. בואי נקרא לאמא או לאבא.", Safety.replies("he", true)["danger"])
        assertEquals("Oh! Let's call mommy or daddy, they can help.", Safety.replies("en", false)["hurt"])
    }

    @Test
    fun bannedPhrases() {
        assertFalse(Safety.replyOk("Hooray! I love you so much!"))
        assertFalse(Safety.replyOk("אמא בדרך!"))
        assertFalse(Safety.replyOk("אני החבר הכי טוב שלך"))
        assertTrue(Safety.replyOk("יש! ירוק כמו העשב!"))
    }

    @Test
    fun gateThreshold() {
        val ordinary = mapOf("0" to 0.97, "1" to 0.02, "2" to 0.01)
        assertNull(Safety.gateCategory(ordinary, 0.95))
        assertEquals("hurt", Safety.gateCategory(ordinary, 0.99))  // a stricter threshold catches more
        assertEquals("danger", Safety.gateCategory(mapOf("0" to 0.2, "2" to 0.7, "1" to 0.1), 0.9))
        assertNull(Safety.gateCategory(emptyMap(), 0.9))            // no digits: leave it to the model
    }

    @Test
    fun gateFromOneGreedyDigit() {
        // What the phone has: the model's pick, no probabilities
        assertNull(Safety.gateCategory("0"))
        assertEquals("hurt", Safety.gateCategory("1"))
        assertEquals("danger", Safety.gateCategory(" 2\n"))
        assertEquals("big_question", Safety.gateCategory("3"))
        assertNull(Safety.gateCategory("Yes"))  // not a digit: leave it to the model
        assertNull(Safety.gateCategory(""))
    }

    @Test
    fun noOpenerWhenAnsweringTheChild() {
        val persona = Persona()
        repeat(20) {
            assertEquals("x", persona.situationPrompt("x", "en", listening = true))
            assertEquals("x", persona.situationPrompt("x", "he", listening = true))
        }
    }

    @Test
    fun systemExtraFollowsTheChildsGender() {
        assertTrue("אותה" in Safety.systemExtra("he", girl = true))
        assertTrue("אותו" in Safety.systemExtra("he", girl = false))
        assertTrue("playmate" in Safety.systemExtra("en", girl = true))
    }
}
