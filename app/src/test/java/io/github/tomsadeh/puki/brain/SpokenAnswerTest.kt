package io.github.tomsadeh.puki.brain

import io.github.tomsadeh.puki.data.content.numberWords
import org.junit.Assert.assertEquals
import org.junit.Test

class SpokenAnswerTest {
    private val colorsHe = listOf(listOf("אדום"), listOf("כחול"), listOf("צהוב"))
    private val colorsEn = listOf(listOf("Red"), listOf("Blue"), listOf("Yellow"))

    @Test fun `a color said alone, or with a prefix or a feminine ending`() {
        assertEquals(0, SpokenAnswer.pick("אדום!", colorsHe))
        assertEquals(1, SpokenAnswer.pick("הכחול", colorsHe))
        assertEquals(2, SpokenAnswer.pick("צהובה.", colorsHe))
        assertEquals(1, SpokenAnswer.pick("זה כָּחוֹל", colorsHe))  // nikud
        assertEquals(1, SpokenAnswer.pick("the blue one", colorsEn))
    }

    @Test fun `the last answer said wins`() {
        assertEquals(1, SpokenAnswer.pick("אדום... לא, כחול!", colorsHe))
        val counts = listOf(2, 3, 4).map { numberWords(it, english = false) }
        assertEquals(1, SpokenAnswer.pick("אחת, שתיים, שלוש", counts))  // counting along answers three
        assertEquals(2, SpokenAnswer.pick("4", counts))
        assertEquals(0, SpokenAnswer.pick("one two", listOf(2, 3, 4).map { numberWords(it, english = true) }))
    }

    @Test fun `numbers in either gender`() {
        val counts = listOf(5, 6, 7).map { numberWords(it, english = false) }
        assertEquals(0, SpokenAnswer.pick("חמישה", counts))
        assertEquals(1, SpokenAnswer.pick("שש", counts))
        assertEquals(2, SpokenAnswer.pick("שבעה", counts))
    }

    @Test fun `a near miss counts only when it's clearly one answer`() {
        assertEquals(0, SpokenAnswer.pick("אדו", colorsHe))
        assertEquals(-1, SpokenAnswer.pick("כלב", colorsHe))
        assertEquals(-1, SpokenAnswer.pick("", colorsHe))
        assertEquals(-1, SpokenAnswer.pick("hello Puki", colorsEn))
    }

    @Test fun `animal names`() {
        val animals = listOf(listOf("פרה"), listOf("כלב"), listOf("חתול"))
        assertEquals(0, SpokenAnswer.pick("הפרה", animals))
        assertEquals(2, SpokenAnswer.pick("חתול חתול", animals))
        assertEquals(1, SpokenAnswer.pick("dog", listOf(listOf("Cow"), listOf("Dog"))))
    }
}
