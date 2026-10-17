package io.github.tomsadeh.puki.ui.components

import io.github.tomsadeh.puki.data.models.Language
import org.junit.Assert.assertEquals
import org.junit.Test

class ParentCheckTest {
    @Test fun `numbers in words, both languages`() {
        assertEquals("seventeen", numberInWords(17, Language.ENGLISH))
        assertEquals("אחת-עשרה", numberInWords(11, Language.HEBREW))
        assertEquals("תשע-עשרה", numberInWords(19, Language.HEBREW))
        (11..19).forEach { numberInWords(it, Language.HEBREW); numberInWords(it, Language.ENGLISH) }
    }
}
