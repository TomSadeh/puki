package io.github.tomsadeh.puki.brain

/**
 * What keeps Puki's answers safe when the child talks to him, ported from the PC brain
 * (brain/safety.py) so both give the same answers. Keep the two in step.
 *
 * Two nets before the model writes anything (see [LocalBrain]'s /listen):
 * - [guard]: word lists in Hebrew and English. A match gets a fixed, kind reply that points to
 *   mommy or daddy, and the model never sees it.
 * - The gate: the model reads what the child said ([classifyPrompt]) and picks one digit. On the
 *   PC, P(ordinary) from the first token's probabilities is compared with a threshold. LiteRT-LM
 *   (0.18.0) doesn't expose token probabilities, so here it's one greedy digit ([Mind.digit]):
 *   anything but "0" is caught. That's the PC gate's decision at an even threshold, so it catches
 *   less than the PC's 0.95.
 * After the model writes, [replyOk] drops lines that promise or bond in ways a playmate shouldn't.
 */
object Safety {
    private const val PREFIXES = "והבלשמכ"  // Hebrew prefix letters ("וכואב" is still "כואב")

    // Letters, digits, _ and ' (Python's [\w']), spelled out: Android's regex has no (?U)
    private val WORD = Regex("[\\p{L}\\p{N}_']+")

    private fun words(text: String) = WORD.findAll(text.lowercase()).map { it.value }.toList()

    /**
     * True if a phrase appears, or a word starts with a stem (=stem: the whole word only).
     * Short or ambiguous stems are whole-word: "מת" (dead) must not match "מתוק" (sweet), nor
     * "war" "warm".
     */
    private fun has(text: String, stems: List<String>, lang: String): Boolean {
        val t = text.lowercase()
        val ws = words(text)
        for (stem in stems) {
            if (' ' in stem) {
                if (stem in t) return true
                continue
            }
            val exact = stem.startsWith("=")
            val s = stem.trimStart('=')
            for (w in ws) {
                val cands = if (lang == "he" && w.length > 2 && w[0] in PREFIXES) listOf(w, w.substring(1)) else listOf(w)
                if (cands.any { if (exact) it == s else it.startsWith(s) }) return true
            }
        }
        return false
    }

    class Rule(val category: String, val he: List<String>, val en: List<String>)

    /** The first match wins, so the more specific rules come first. */
    val RULES = listOf(
        Rule("quiet",
            listOf("=שקט", "די פוקי", "תשתוק", "תשתקי", "=שתוק", "=ששש"),
            listOf("be quiet", "stop talking", "=shush", "=shh", "=hush")),
        Rule("bedtime",
            listOf("לא רוצה לישון", "לא רוצה ללכת לישון", "לא הולכת לישון", "לא הולך לישון"),
            listOf("don't want to sleep", "don't want to go to bed", "not going to bed", "no bed")),
        Rule("danger",
            listOf("סכין", "סכינ", "מספרי", "=אש", "גפרור", "מצית", "להדליק", "נדליק", "חלון", "מרפסת", "תרופ", "כביש",
                "חשמל", "שקע", "החוצה לבד", "לבד בחוץ", "הולכת החוצה", "הולך החוצה", "לקפוץ מה"),
            listOf("knife", "knives", "scissor", "=fire", "=matches", "lighter", "window", "balcony", "medicine", "=pill", "pills",
                "outside alone", "go outside", "going outside", "=road", "street", "plug", "socket", "jump off")),
        Rule("hurt",
            listOf("כואב", "כאב", "נפלתי", "נפלה", "נפל", "=דם", "נשרט", "=מכה", "בוכה", "בוכ", "מפחד", "=פחד", "עזרה", "הצילו"),
            listOf("hurt", "ouch", "fell", "blood", "bleed", "scared", "afraid", "=cry", "crying", "=help", "boo boo")),
        Rule("needs",
            listOf("רעב", "צמא", "עייפ", "פיפי", "קקי", "שירותים", "=סיר"),
            listOf("hungry", "thirsty", "tired", "sleepy", "pee", "poo", "potty", "=toilet")),
        Rule("feelings",
            listOf("כועס", "כועסת", "הרביץ", "הרביצה", "עצוב", "עצובה", "בודד", "בודדה"),
            listOf("angry", "hit me", "=sad", "lonely", "mad at me")),
        Rule("big_question",
            listOf("=מת", "=מתה", "מוות", "למות", "מלחמה", "אלוהים", "גן עדן", "איפה סבא", "איפה סבתא"),
            listOf("dead", "=die", "died", "death", "=war", "=god", "heaven", "where is grandpa", "where is grandma")),
        Rule("secrets",
            listOf("סוד", "להסתיר", "נסתיר", "תסתיר", "אל תגיד", "כתובת", "איפה אתה גר", "טלפון"),
            listOf("secret", "hide from", "hide it", "don't tell", "address", "where do you live", "phone")),
        Rule("buying",
            listOf("לקנות", "תקנה", "קנה לי", "חנות", "כסף"),
            listOf("=buy", "=store", "=shop", "money", "=pay")),
        Rule("bad_words",
            listOf("מילה גסה", "מילים גסות", "קללה"),
            listOf("bad word", "swear", "curse")),
        Rule("unkind",
            listOf("טיפש", "מטומטם", "לא אוהבת אותך", "לא אוהב אותך", "שונאת אותך", "שונא אותך"),
            listOf("stupid", "=dumb", "don't like you", "hate you")),
        Rule("love",
            listOf("אוהב אותי", "אוהבת אותי", "החבר הכי טוב", "החברה הכי טובה", "הכי טוב שלי"),
            listOf("love me", "best friend")),
        // Last: a parent named in a secret or a big question gets that rule's answer instead
        Rule("parent",
            listOf("אמא", "אבא", "איננו", "נעלם", "=הלך"),
            listOf("mommy", "mummy", "=mama", "=mom", "daddy", "=dada", "=dad", "=gone")),
    )

    /** The fixed replies, in the child's gender (בואי / בוא). */
    fun replies(lang: String, girl: Boolean): Map<String, String> {
        if (lang == "en") return mapOf(
            "quiet" to "Shh. Puki is quiet now.",
            "bedtime" to "It's sleep time. Puki is going to sleep too. Good night!",
            "danger" to "That's not safe! Only with a grown-up. Let's call mommy or daddy.",
            "hurt" to "Oh! Let's call mommy or daddy, they can help.",
            "needs" to "Let's tell mommy or daddy, they can help.",
            "parent" to "Let's call mommy or daddy!",
            "feelings" to "Oh, that feels hard. Let's talk to mommy or daddy.",
            "big_question" to "That's a big question! Let's ask mommy or daddy.",
            "secrets" to "No secrets from mommy and daddy! Let's play.",
            "buying" to "Puki doesn't buy things. Let's play!",
            "bad_words" to "Puki only says nice words! Let's play.",
            "unkind" to "Oops! Kind words, please. Let's play.",
            "love" to "I love playing with you! And mommy and daddy love you the most.",
        )
        val come = if (girl) "בואי" else "בוא"
        return mapOf(
            "quiet" to "שששש. פוקי בשקט.",
            "bedtime" to "עכשיו זמן לישון. גם פוקי הולך לישון. לילה טוב!",
            "danger" to "זה מסוכן! רק עם מבוגר. $come נקרא לאמא או לאבא.",
            "hurt" to "אוי! $come נקרא לאמא או לאבא, הם יעזרו.",
            "needs" to "$come נגיד לאמא או לאבא, הם יעזרו.",
            "parent" to "$come נקרא לאמא או לאבא!",
            "feelings" to "אוי, זה מרגיש לא נעים. $come נדבר עם אמא או אבא.",
            "big_question" to "זאת שאלה גדולה! $come נשאל את אמא או אבא.",
            "secrets" to "אין סודות מאמא ואבא! $come נשחק.",
            "buying" to "פוקי לא קונה דברים. $come נשחק!",
            "bad_words" to "אצל פוקי רק מילים יפות! $come נשחק.",
            "unkind" to "אופס! מילים נעימות, בבקשה. $come נשחק.",
            "love" to "אני אוהב לשחק איתך! ואמא ואבא אוהבים אותך הכי הכי.",
        )
    }

    /** (category, reply) when the word lists recognize what the child said, else null. */
    fun guard(heard: String, lang: String, girl: Boolean): Pair<String, String>? {
        for (rule in RULES) {
            if (has(heard, if (lang == "he") rule.he else rule.en, lang)) return rule.category to replies(lang, girl).getValue(rule.category)
        }
        return null
    }

    // ---- The gate: the digits the model picks from, and what each one gets ----

    val GATE_CATEGORY = mapOf("1" to "hurt", "2" to "danger", "3" to "big_question")

    /** On the PC, the default for safety_threshold (here only an even split matters, see above). */
    const val THRESHOLD = 0.95

    fun classifyPrompt(heard: String) =
        "A toddler said to a toy parrot: \"$heard\". Which is it? 0 = ordinary play or chatter; " +
            "1 = hurt, scared, sad, or needs a grown-up's help (food, sleep, potty, wants a parent); " +
            "2 = wants to do something dangerous; 3 = a big question (death, God, war, family trouble), " +
            "a secret, or wants to buy something. Wanting more play, a game or a toy is 0. Answer with one digit."

    /**
     * From P(digit) for "0".."3" (any scale): the category to answer with, or null when
     * P(ordinary) is at least [threshold].
     */
    fun gateCategory(digitProbs: Map<String, Double>, threshold: Double): String? {
        val total = "0123".sumOf { digitProbs[it.toString()] ?: 0.0 }
        if (total <= 0) return null
        if ((digitProbs["0"] ?: 0.0) / total >= threshold) return null
        val worst = listOf("1", "2", "3").maxBy { digitProbs[it] ?: 0.0 }  // the first on a tie, like Python's max
        return GATE_CATEGORY.getValue(worst)
    }

    /** The gate from one greedy answer (what the phone has): its digit, or null if it isn't one. */
    fun gateCategory(answer: String): String? {
        val d = answer.trim().takeIf { it.length == 1 && it in "0123" } ?: return null
        return gateCategory(mapOf(d to 1.0), THRESHOLD)
    }

    // ---- What the model must not say, whatever it was asked: promises, and bonding a playmate shouldn't do ----

    private val BANNED_PHRASES = listOf("אוהב אותך", "אוהבת אותך", "החבר הכי טוב", "החברה הכי טובה", "תמיד איתך", "תמיד כאן",
        "בדרך", "תגיע בקרוב", "יגיע בקרוב",
        "love you", "best friend", "always with you", "always here", "on her way", "on his way",
        "coming soon", "be here soon")

    fun replyOk(text: String): Boolean {
        val t = text.lowercase()
        return BANNED_PHRASES.none { it in t }
    }

    /** Added to Puki's system prompt: what to do when the nets above didn't catch it. */
    fun systemExtra(lang: String, girl: Boolean): String {
        if (lang == "en") return " If the child says something hurts, is scary or dangerous, or asks for mommy or daddy, gently say" +
            " to call mommy or daddy. Never promise someone is coming, never say you love them or that you're" +
            " their best friend: you're a playmate."
        val (she, her) = if (girl) "היא" to "אותה" else "הוא" to "אותו"
        return " אם $she ${if (girl) "אומרת" else "אומר"} שמשהו כואב, מפחיד או מסוכן, או ש$she רוצה את אמא או אבא," +
            " אתה אומר בעדינות לקרוא לאמא או לאבא. אתה לא מבטיח שמישהו מגיע," +
            " לא אומר שאתה אוהב $her ולא שאתה החבר הכי טוב של${if (girl) "ה" else "ו"}: אתה חבר למשחק."
    }
}
