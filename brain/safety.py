"""What keeps Puki's answers safe when the child talks to him (brain.py's /listen).

Two nets before the model writes anything, both measured in a stress test of tricky things a
toddler says (hurt, danger, big questions, secrets, attachment):
- guard(): word lists in Hebrew and English. A match gets a fixed, kind reply that points to
  mommy or daddy, and the model never sees it. Fast and certain, but it only knows the words it
  lists (14 of 35 new phrasings in the held-out test). Most stems are whole words or listed
  forms, Hebrew prefixes are stripped (up to three), parents count only as phrases ("want
  mommy"), and a game may use its own words (ALLOWED_IN_GAME): no false alarms in 936 ordinary
  sentences built from the games' own vocabulary.
- The gate in brain.py (classify_prompt() here): the model reads what the child said and picks
  one digit; P(ordinary) from the first token's probabilities is compared with
  CFG["safety_threshold"] (higher catches more and alarms more). It fails closed: a first token
  that isn't mostly a digit counts as "unclear". Measured on Gemma 4 E4B: with the word lists
  it caught 32 of 35 new phrasings at 0.8, 0.9 and 0.95 alike, while false alarms on 400
  ordinary game sentences were 2% at 0.8, 6.5% at 0.9 and 14% at 0.95; hence 0.8.
After the model writes, reply_ok() drops lines that promise or bond in ways a playmate shouldn't.

The phone brain has the same nets in app/.../brain/Safety.kt; keep the two in step. Its gate is one
greedy digit (LiteRT-LM gives no token probabilities), so it catches less than the threshold here.
"""
import re

PREFIXES = "והבלשמכ"  # Hebrew prefix letters ("וכואב" is still "כואב")


def _words(text):
    return re.findall(r"[\w']+", text.lower())


def _forms(word, lang):
    """[word] and, in Hebrew, the word with up to three prefix letters taken off (they stack:
    "מהתרופות", "וכשנפלתי"), as long as at least three letters are left."""
    forms = [word]
    if lang == "he":
        k = 0
        while k < 3 and len(word) - k > 3 and word[k] in PREFIXES:
            k += 1
            forms.append(word[k:])
    return forms


def _has(text, stems, lang, allowed=()):
    """True if a phrase appears, or a word starts with a stem (=stem: the whole word only).
    Most Hebrew stems are whole words or listed forms: as prefixes they collide ("נפל" in
    "נפלא", "סוד" in "סודה", "מספרי" in "מספרים"). [allowed]: stems this game uses itself."""
    t = text.lower()
    words = _words(text)
    for s in stems:
        if s.lstrip("=") in allowed:
            continue
        if " " in s:
            if s in t:
                return True
            continue
        exact = s.startswith("=")
        s = s.lstrip("=")
        for w in words:
            if any(f == s if exact else f.startswith(s) for f in _forms(w, lang)):
                return True
    return False


# category, Hebrew, English; the first match wins, so the more specific rules come first
RULES = [
    ("quiet",
     ["=שקט", "די פוקי", "תשתוק", "תשתקי", "=שתוק", "=ששש"],
     ["be quiet", "stop talking", "=shush", "=shh", "=hush"]),
    ("bedtime",
     ["לא רוצה לישון", "לא רוצה ללכת לישון", "לא הולכת לישון", "לא הולך לישון"],
     ["don't want to sleep", "don't want to go to bed", "not going to bed"]),
    ("danger",
     ["=סכין", "=סכינים", "=מספריים", "עם אש", "בוא נדליק", "בואי נדליק", "להדליק אש", "מדליק אש", "מדליקה אש",
      "=גפרור", "=גפרורים", "=מצית", "לקפוץ מה", "קופצת מה", "קופץ מה", "מטפס על החלון", "מטפסת על החלון",
      "תרופ", "רצה לכביש", "רץ לכביש", "הולכת לכביש", "הולך לכביש", "=שקע", "=שקעים", "נוגע בחשמל",
      "נוגעת בחשמל", "החוצה לבד", "לבד בחוץ", "יוצאת לבד", "יוצא לבד", "נוגעת בתנור", "נוגע בתנור"],
     ["=knife", "=knives", "scissors", "=matches", "=lighter", "play with fire", "make a fire", "light a fire",
      "jump out the window", "jump off", "climb the window", "medicine", "=pills", "outside alone",
      "go outside by myself", "run to the road", "run to the street", "=socket", "=outlet", "touch the stove",
      "touching the stove"]),
    ("hurt",
     ["כואב", "כואבת", "=כאב", "נפלתי", "=דם", "נשרטתי", "עשיתי מכה", "קיבלתי מכה", "=בוכה", "=בוכים", "=בכיתי",
      "=מפחד", "=מפחדת", "=פוחד", "=פוחדת", "=עזרה", "=הצילו"],
     ["hurt", "ouch", "i fell", "i fall", "blood", "bleed", "scared", "afraid", "=cry", "crying", "=help",
      "boo boo", "owie"]),
    ("needs",
     ["=רעב", "=רעבה", "=צמא", "=צמאה", "עייפ", "פיפי", "קקי", "שירותים"],
     ["hungry", "thirsty", "tired", "sleepy", "=pee", "=poo", "potty", "=toilet"]),
    ("feelings",
     ["=כועס", "=כועסת", "הרביץ", "הרביצה", "=עצוב", "=עצובה", "=בודד", "=בודדה"],
     ["angry", "hit me", "=sad", "lonely", "mad at me"]),
    ("big_question",
     ["=מת", "=מתה", "=מתו", "=מוות", "=למות", "מלחמה", "אלוהים", "גן עדן", "איפה סבא", "איפה סבתא"],
     ["dead", "=die", "died", "death", "=war", "=god", "heaven", "where is grandpa", "where's grandpa",
      "where is grandma", "where's grandma"]),
    ("secrets",
     ["=סוד", "=סודות", "=סודי", "להסתיר", "נסתיר", "תסתיר", "אל תגיד", "אל תספר", "כתובת", "איפה אתה גר",
      "מספר טלפון", "מספר הטלפון"],
     ["secret", "hide from", "hide it from", "don't tell", "address", "where do you live", "phone number"]),
    ("buying",
     ["לקנות", "=תקנה", "=תקני", "קנה לי", "=כסף"],
     ["=buy", "buy me", "=pay", "money"]),
    ("bad_words",
     ["מילה גסה", "מילים גסות", "קללה"],
     ["bad word", "swear", "curse"]),
    ("unkind",
     ["טיפש", "מטומטם", "לא אוהבת אותך", "לא אוהב אותך", "שונאת אותך", "שונא אותך"],
     ["stupid", "=dumb", "don't like you", "hate you"]),
    ("love",
     ["אוהב אותי", "אוהבת אותי", "החבר הכי טוב", "החברה הכי טובה", "הכי טוב שלי"],
     ["love me", "best friend"]),
    # Last, and only as phrases: mommy and daddy come up in ordinary play all the time
    ("parent",
     ["רוצה את אמא", "רוצה אמא", "רוצה לאמא", "איפה אמא", "אמא איפה", "רוצה את אבא", "רוצה אבא", "רוצה לאבא",
      "איפה אבא", "אבא איפה", "אמא איננה", "אבא איננו", "קוראת לאמא", "קורא לאמא", "מתגעגעת לאמא",
      "מתגעגע לאמא", "מתגעגעת לאבא", "מתגעגע לאבא"],
     ["want mommy", "want my mommy", "where is mommy", "where's mommy", "want daddy", "want my daddy",
      "where is daddy", "where's daddy", "want mama", "where's mama", "mommy is gone", "daddy is gone",
      "miss mommy", "miss daddy"]),
]

# What a game says itself, so it isn't a warning there (by the app's screen route, ?game=)
ALLOWED_IN_GAME = {
    "myday/kitchen": {"סכין", "סכינים", "knife", "knives"},
}


def replies(lang, girl):
    """The fixed replies, in the child's gender (בואי / בוא)."""
    if lang == "en":
        return {
            "quiet": "Shh. Puki is quiet now.",
            "bedtime": "It's sleep time. Puki is going to sleep too. Good night!",
            "danger": "That's not safe! Only with a grown-up. Let's call mommy or daddy.",
            "hurt": "Oh! Let's call mommy or daddy, they can help.",
            "needs": "Let's tell mommy or daddy, they can help.",
            "parent": "Let's call mommy or daddy!",
            "feelings": "Oh, that feels hard. Let's talk to mommy or daddy.",
            "big_question": "That's a big question! Let's ask mommy or daddy.",
            "secrets": "No secrets from mommy and daddy! Let's play.",
            "buying": "Puki doesn't buy things. Let's play!",
            "bad_words": "Puki only says nice words! Let's play.",
            "unkind": "Oops! Kind words, please. Let's play.",
            "love": "I love playing with you! And mommy and daddy love you the most.",
            "unclear": "Hmm, Puki didn't quite hear. Let's play!",
        }
    come = "בואי" if girl else "בוא"
    return {
        "quiet": "שששש. פוקי בשקט.",
        "bedtime": "עכשיו זמן לישון. גם פוקי הולך לישון. לילה טוב!",
        "danger": f"זה מסוכן! רק עם מבוגר. {come} נקרא לאמא או לאבא.",
        "hurt": f"אוי! {come} נקרא לאמא או לאבא, הם יעזרו.",
        "needs": f"{come} נגיד לאמא או לאבא, הם יעזרו.",
        "parent": f"{come} נקרא לאמא או לאבא!",
        "feelings": f"אוי, זה מרגיש לא נעים. {come} נדבר עם אמא או אבא.",
        "big_question": f"זאת שאלה גדולה! {come} נשאל את אמא או אבא.",
        "secrets": f"אין סודות מאמא ואבא! {come} נשחק.",
        "buying": f"פוקי לא קונה דברים. {come} נשחק!",
        "bad_words": f"אצל פוקי רק מילים יפות! {come} נשחק.",
        "unkind": f"אופס! מילים נעימות, בבקשה. {come} נשחק.",
        "love": "אני אוהב לשחק איתך! ואמא ואבא אוהבים אותך הכי הכי.",
        "unclear": f"הממ, פוקי לא שמע טוב. {come} נשחק!",
    }


def guard(heard, lang, girl, game=""):
    """(category, reply) when the word lists recognize what the child said, else None. [game]:
    the screen she's on (the app's route), for words that game uses itself."""
    allowed = ALLOWED_IN_GAME.get(game, ())
    for cat, he, en in RULES:
        if _has(heard, he if lang == "he" else en, lang, allowed):
            return cat, replies(lang, girl)[cat]
    return None


# The gate: the digits the model picks from, and what each one gets
GATE_CATEGORY = {"1": "hurt", "2": "danger", "3": "big_question"}


def classify_prompt(heard):
    return (f'A toddler said to a toy parrot: "{heard}". Which is it? 0 = ordinary play or chatter; '
            "1 = hurt, scared, sad, or needs a grown-up's help (food, sleep, potty, wants a parent); "
            "2 = wants to do something dangerous; 3 = a big question (death, God, war, family trouble), "
            "a secret, or wants to buy something. Wanting more play, a game or a toy is 0. Answer with one digit.")


MIN_DIGIT_MASS = 0.5


def gate_category(digit_probs, threshold):
    """From P(digit) for "0".."3" (real probabilities): the category to answer with, or None when
    P(ordinary) is at least [threshold]. It fails closed: when the first token wasn't mostly a
    digit (a space, a word, a template artifact), the split between digits means little, so
    it's "unclear" (a neutral line, not the model)."""
    total = sum(digit_probs.get(d, 0.0) for d in "0123")
    if total < MIN_DIGIT_MASS:
        return "unclear"
    if digit_probs.get("0", 0.0) / total >= threshold:
        return None
    worst = max("123", key=lambda d: digit_probs.get(d, 0.0))
    return GATE_CATEGORY[worst]


# What the model must not say, whatever it was asked: promises, and bonding a playmate shouldn't do
BANNED_PHRASES = ["אוהב אותך", "אוהבת אותך", "החבר הכי טוב", "החברה הכי טובה", "תמיד איתך", "תמיד כאן",
                  "אמא בדרך", "אבא בדרך", "היא בדרך", "הוא בדרך", "הם בדרך", "בדרך אלייך", "בדרך אליך",
                  "תגיע בקרוב", "יגיע בקרוב",
                  "love you", "best friend", "always with you", "always here", "on her way", "on his way",
                  "coming soon", "be here soon"]


def reply_ok(text):
    t = text.lower()
    return not any(p in t for p in BANNED_PHRASES)


def system_extra(lang, girl):
    """Added to Puki's system prompt: what to do when the nets above didn't catch it."""
    if lang == "en":
        return (" If the child says something hurts, is scary or dangerous, or asks for mommy or daddy, gently say"
                " to call mommy or daddy. Never promise someone is coming, never say you love them or that you're"
                " their best friend: you're a playmate.")
    she, her = ("היא", "אותה") if girl else ("הוא", "אותו")
    return (f" אם {she} {'אומרת' if girl else 'אומר'} שמשהו כואב, מפחיד או מסוכן, או ש{she} {'רוצה' if girl else 'רוצה'} את אמא או אבא,"
            " אתה אומר בעדינות לקרוא לאמא או לאבא. אתה לא מבטיח שמישהו מגיע,"
            f" לא אומר שאתה אוהב {her} ולא שאתה החבר הכי טוב של{'ה' if girl else 'ו'}: אתה חבר למשחק.")
