package com.olamkatan.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingLinkTest {
    @Test
    fun readsTheDashboardLink() {
        assertEquals(
            PairingLink("192.168.1.20:8790", "ABCDEFGH"),
            parsePairingLink("puki://pair?host=192.168.1.20:8790&code=ABCDEFGH")
        )
    }

    @Test
    fun codeCaseAndDashesDontMatter() {
        assertEquals(
            PairingLink("192.168.1.20:8790", "ABCDEFGH"),
            parsePairingLink(" puki://pair?code=abcd-efgh&host=192.168.1.20%3A8790 ")
        )
    }

    @Test
    fun ignoresOtherCodes() {
        listOf(
            null,
            "",
            "hello",
            "https://example.com/?host=1.2.3.4:8790&code=ABCDEFGH",
            "puki://other?host=1.2.3.4:8790&code=ABCDEFGH",
            "puki://pair?host=1.2.3.4:8790",
            "puki://pair?code=ABCDEFGH",
            "puki://pair?host=1.2.3.4&code=ABCDEFGH",
            "puki://pair?host=1.2.3.4:0&code=ABCDEFGH",
            "puki://pair?host=1.2.3.4:99999&code=ABCDEFGH",
            "puki://pair?host=evil.com/x:8790&code=ABCDEFGH",
            "puki://pair?host=1.2.3.4:8790&code=SHORT",
            "puki://pair?host=1.2.3.4:8790&code=ABCDEFGH%D7%90",
        ).forEach { assertNull(it, parsePairingLink(it)) }
    }
}
