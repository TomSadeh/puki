package io.github.tomsadeh.puki.brain

/**
 * Which of a round's answers the child said out loud ("אדום!" instead of tapping the red
 * balloon). The brain only hears the words (POST /hear); the game decides here, the same way on
 * the PC and on the device.
 */
object SpokenAnswer {
    /**
     * The index in [options] of the answer said in [heard], or -1. Each option is the words
     * that count for it, e.g. ["3", "שלוש", "שלושה"]. Hebrew prefixes ("האדום", "והכחול") and
     * a feminine ending ("אדומה") count too. When several answers are said, the last one wins:
     * "one, two, three!" answers three, and "no, blue!" answers blue. A near miss ("אדו")
     * counts only when it's clearly closer to one answer than to the others.
     */
    fun pick(heard: String, options: List<List<String>>): Int {
        val words = Persona.words(heard)
        if (words.isEmpty() || options.isEmpty()) return -1
        val goals = options.map { forms -> forms.map { Persona.words(it).joinToString("") }.filter { it.isNotEmpty() } }
        // The said words, and each two in a row (for answers of two words, like "ice cream")
        for (j in words.indices.reversed()) {
            val here = buildList {
                add(words[j])
                if (j + 1 < words.size) add(words[j] + words[j + 1])
            }
            val exact = goals.indices.filter { i -> goals[i].any { g -> here.any { it == g } } }
            if (exact.size == 1) return exact[0]
            val said = goals.indices.filter { i -> goals[i].any { g -> here.any { sameWord(it, g) } } }
            if (said.size == 1) return said[0]
            if (said.size > 1) return -1  // the same word fits two answers: better not guess
        }
        return nearMiss(words, goals)
    }

    private const val PREFIXES = "הובלשמכ"

    /** [w] is [goal], perhaps with a prefix or two (ה, ו, ב...) and a feminine "ה" or a plural "s". */
    private fun sameWord(w: String, goal: String): Boolean {
        val forms = listOfNotNull(
            w,
            w.dropLast(1).takeIf { w.endsWith("ה") && !goal.endsWith("ה") },
            w.dropLast(1).takeIf { w.endsWith("s") && !goal.endsWith("s") }
        )
        return forms.any { f ->
            f == goal || (f.length - goal.length in 1..2 && f.endsWith(goal) && f.dropLast(goal.length).all { it in PREFIXES })
        }
    }

    private fun nearMiss(words: List<String>, goals: List<List<String>>): Int {
        val scores = goals.map { forms ->
            words.maxOfOrNull { w -> forms.maxOfOrNull { g -> if (g.length < 3) 0.0 else Persona.similarity(w, g) } ?: 0.0 } ?: 0.0
        }
        val best = scores.indices.maxByOrNull { scores[it] } ?: return -1
        val second = scores.filterIndexed { i, _ -> i != best }.maxOrNull() ?: 0.0
        return if (scores[best] >= 0.75 && scores[best] - second >= 0.15) best else -1
    }
}
