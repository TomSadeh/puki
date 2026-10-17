package io.github.tomsadeh.puki.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which version counts as newer decides whether the game offers an update. */
class UpdaterTest {
    @Test
    fun newerVersions() {
        assertTrue(Updater.isNewer("0.7.0", "0.6.0"))
        assertTrue(Updater.isNewer("0.10.0", "0.9.9"))  // numbers, not text
        assertTrue(Updater.isNewer("1.0", "0.99.99"))
        assertTrue(Updater.isNewer("0.6.1", "0.6"))
    }

    @Test
    fun notNewer() {
        assertFalse(Updater.isNewer("0.6.0", "0.6.0"))
        assertFalse(Updater.isNewer("0.6", "0.6.0"))
        assertFalse(Updater.isNewer("0.5.9", "0.6.0"))
        assertFalse(Updater.isNewer("junk", "0.6.0"))
    }
}
