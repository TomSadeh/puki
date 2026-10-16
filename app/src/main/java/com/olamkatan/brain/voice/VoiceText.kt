package com.olamkatan.brain.voice

import java.text.Normalizer

/**
 * BlueTTS's text handling, ported from blue_onnx (Python) for what Puki says: short Hebrew
 * lines with punctuation, no digits (the brain's line filter only lets those through).
 * Each step keeps the Python name, so the two can be compared side by side; the parts that
 * only matter for numbers, dates, emails and English loanwords are left out.
 */
object VoiceText {
    private const val HEB = "֐-׿"

    // ---- text_norm.prepare_text_for_synthesis, the Hebrew-sentence part ----

    fun prepareTextForSynthesis(text: String): String {
        var t = normalizeCommonText(text)
        t = Regex("(?<=[$HEB])[\"״](?=[$HEB])").replace(t, "")           // strip_hebrew_abbreviation_quotes
        t = Regex("(?<=[גצז])'(?=[$HEB])").replace(t, "׳")               // normalize_phonetic_geresh
        t = expandDialogueQuotes(t)
        t = Regex("(?<=[$HEB])[-–—‑]+(?=[$HEB])").replace(t, "")              // strip_hebrew_inword_hyphens
        return stripSilentSeparatorTokens(t)
    }

    private fun normalizeCommonText(text: String): String {
        var t = Regex("(^|\\s)#{1,6}\\s*").replace(text, "$1")
        // strip_brackets
        t = Regex("\\s*[(\\[{]\\s*").replace(t, ", ")
        t = Regex("\\s*[)\\]}]\\s*").replace(t, ", ")
        // normalize_repeated_punctuation
        t = t.replace("…", ",")
        t = Regex("(?<!\\d)\\.{2,}(?!\\d)").replace(t, ",")
        t = Regex("!+").replace(t, "!")
        t = Regex("\\?+").replace(t, "?")
        t = Regex(",(?:\\s*,)+").replace(t, ",")
        t = Regex("\\s*[,;:]+\\s*(?=[.!?])").replace(t, "")
        return Regex("(?<=[.!?])\\s*[,;:]+\\s*").replace(t, " ")
    }

    private fun expandDialogueQuotes(text: String): String {
        var t = Regex("(?<=\\S)\\s*[\"“„”]\\s*$").replace(text, ".")
        t = Regex("\\s*:?\\s*[\"“„”]\\s*(?=\\S)").replace(t, ", ")
        return Regex("(?<=\\S)\\s*[\"“„”]").replace(t, ", ")
    }

    private fun stripSilentSeparatorTokens(text: String): String {
        var t = Regex("(?<=[$HEB])[-–—‑]+(?=[A-Za-z0-9])").replace(text, " ")
        t = Regex("(?<=[A-Za-z0-9])[-–—‑]+(?=[$HEB])").replace(t, " ")
        t = Regex("(?<![A-Za-z])\\s*[-–—‑]+\\s*(?![A-Za-z])").replace(t, " ")
        t = Regex("(?<!\\d)\\s*:+\\s*(?!\\d)").replace(t, " ")
        return Regex("\\s+").replace(t, " ").trim()
    }

    // ---- blue_onnx helpers ----

    private val LANG_TAG = Regex("</?[\\p{L}\\p{N}_]+>")

    fun stripLangTags(s: String): String = Regex("\\s+").replace(LANG_TAG.replace(s, ""), " ").trim()

    /** chunk_text: sentences packed into chunks of at most [maxLen] characters. */
    fun chunkText(text: String, maxLen: Int = 300): List<String> {
        val chunks = mutableListOf<String>()
        for (paragraph in Regex("\\n\\s*\\n+").split(text.trim()).map { it.trim() }.filter { it.isNotEmpty() }) {
            var current = ""
            for (sentence in Regex("(?<=[.!?])\\s+").split(paragraph)) {
                if (current.length + sentence.length + 1 <= maxLen) {
                    current += (if (current.isNotEmpty()) " " else "") + sentence
                } else {
                    if (current.isNotEmpty()) chunks += current.trim()
                    current = sentence
                }
            }
            if (current.isNotEmpty()) chunks += current.trim()
        }
        return chunks
    }

    // ---- UnicodeProcessor: phonemes to token ids ----

    private val REPLACEMENTS = listOf(
        "–" to "-", "‑" to "-", "—" to "-", "_" to " ", "“" to "\"", "”" to "\"",
        "‘" to "'", "’" to "'", "´" to "'", "`" to "'", "[" to " ", "]" to " ",
        "|" to " ", "/" to " ", "#" to " ", "→" to " ", "←" to " ",
    )

    /** UnicodeProcessor._preprocess_text, then _encode with [charToId] (unknowns become [padId]). */
    fun encode(phonemes: String, charToId: Map<String, Int>, padId: Int): IntArray {
        var t = Normalizer.normalize(stripLangTags(phonemes), Normalizer.Form.NFKD)
        for ((k, v) in REPLACEMENTS) t = t.replace(k, v)
        t = Regex("[♥☆♡©\\\\]").replace(t, "")
        t = t.replace("@", " at ").replace("e.g.,", "for example, ").replace("i.e.,", "that is, ")
        for (p in listOf(",", ".", "!", "?", ";", ":", "'")) t = t.replace(" $p", p)
        while ("\"\"" in t) t = t.replace("\"\"", "\"")
        while ("''" in t) t = t.replace("''", "'")
        while ("``" in t) t = t.replace("``", "`")
        t = Regex("\\s+").replace(t, " ").trim()
        if (!Regex("[.!?;:,'\"')\\]}…。」』】〉》›»]$").containsMatchIn(t)) t += "."
        // (_preprocess_text wraps it in <he>…</he> and _encode strips the tags again)
        val ids = IntArray(t.codePointCount(0, t.length))
        var i = 0
        var k = 0
        while (i < t.length) {
            val cp = t.codePointAt(i)
            ids[k++] = charToId[String(Character.toChars(cp))] ?: padId
            i += Character.charCount(cp)
        }
        return ids
    }
}
