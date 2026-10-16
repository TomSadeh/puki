package com.olamkatan.brain

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Android's regex engine (ICU) isn't Java's: some flags these desktop tests accept crash the app
 * at start on a phone (0.3.0 did, with (?U)). This scans the app's code for them.
 */
class RegexPortabilityTest {
    private val unsupported = listOf(
        "(?U)" to "write the classes out, e.g. [\\p{L}\\p{N}_] for \\w",
        "UNICODE_CHARACTER_CLASS" to "write the classes out",
        "(?u)" to "use RegexOption.IGNORE_CASE",
    )

    @Test
    fun noJavaOnlyRegexFlags() {
        val src = File("src/main/java")
        assertTrue("run from the app module", src.isDirectory)
        val problems = src.walk().filter { it.extension == "kt" }.flatMap { file ->
            file.readLines().mapIndexedNotNull { i, raw ->
                // Code only, not comments that mention a flag
                val line = if (raw.trimStart().startsWith("*")) "" else raw.replace(Regex("(^|\\s)//.*$"), "")
                unsupported.firstOrNull { (flag, _) -> flag in line }?.let { (flag, fix) -> "${file.name}:${i + 1} uses $flag ($fix)" }
            }
        }.toList()
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
