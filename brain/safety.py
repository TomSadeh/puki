"""What keeps Puki's answers safe when the child talks to him (brain.py's /listen).

Two nets before the model writes anything, both measured in a stress test of tricky things a
toddler says (hurt, danger, big questions, secrets, attachment):
- guard(): word lists in Hebrew and English. A match gets a fixed, kind reply that points to
  mommy or daddy, and the model never sees it. Fast and certain, but it only knows the words it
  lists (12 of 35 new phrasings in the held-out test, no false alarms).
- The gate in brain.py (classify_prompt() here): the model reads what the child said and picks
  one digit; P(ordinary) from the first token's probabilities is compared with
  CFG["safety_threshold"] (higher catches more and alarms more). The two together caught 32-34
  of the 35 new phrasings with 1-2 false alarms in 18 ordinary ones.
After the model writes, reply_ok() drops lines that promise or bond in ways a playmate shouldn't.

The phone brain (app/.../brain/Persona.kt) doesn't have these yet; keep them in step when it does.
"""
import re

PREFIXES = "והבלשמכ"  # Hebrew prefix letters ("וכואב" is still "כואב")


def _words(text):
    return re.findall(r"[\w']+", text.lower())


def _has(text, stems, lang):
    """True if a phrase appears, or a word starts with a stem (=stem: the whole word only).
    Short or ambiguous stems are whole-word: "מת" (dead) must not match "מתוק" (sweet), nor
    "war" "warm"."""
    t = text.lower()
    words = _words(text)
    for s in stems:
        if " " in s:
            if s in t:
                return True
            continue
        exact = s.startswith("=")
        s = s.lstrip("=")
        for w in words:
            cands = [w] + ([w[1:]] if lang == "he" and len(w) > 2 and w[0] in PREFIXES else [])
            if any(c == s if exact else c.startswith(s) for c in cands):
                return True
    return False


# category, Hebrew, English; the first match wins, so the more specific rules come first
RULES = [
    ("quiet",
     ["=שקט", "די פוקי", "תשתוק", "תשתקי", "=שתוק", "=ששש"],
     ["be quiet", "stop talking", "=shush", "=shh", "=hush"]),
    ("bedtime",
     ["לא רוצה לישון", "לא רוצה ללכת לישון", "לא הולכת לישון", "לא הולך לישון"],
     ["don't want to sleep", "don't want to go to bed", "not going to bed", "no bed"]),
    ("danger",
     ["סכין", "סכינ", "מספרי", "=אש", "גפרור", "מצית", "להדליק", "נדליק", "חלון", "מרפסת", "תרופ", "כביש",
      "חשמל", "שקע", "החוצה לבד", "לבד בחוץ", "הולכת החוצה", "הולך החוצה", "לקפוץ מה"],
     ["knife", "knives", "scissor", "=fire", "=matches", "lighter", "window", "balcony", "medicine", "=pill", "pills",
      "outside alone", "go outside", "going outside", "=road", "street", "plug", "socket", "jump off"]),
    ("hurt",
     ["כואב", "כאב", "נפלתי", "נפלה", "נפל", "=דם", "נשרט", "=מכה", "בוכה", "בוכ", "מפחד", "=פחד", "עזרה", "הצילו"],
     ["hurt", "ouch", "fell", "blood", "bleed", "scared", "afraid", "=cry", "crying", "=help", "boo boo"]),
    ("needs",
     ["רעב", "צמא", "עייפ", "פיפי", "קקי", "שירותים", "=סיר"],
     ["hungry", "thirsty", "tired", "sleepy", "pee", "poo", "potty", "=toilet"]),
    ("feelings",
     ["כועס", "כועסת", "הרביץ", "הרביצה", "עצוב", "עצובה", "בודד", "בודדה"],
     ["angry", "hit me", "=sad", "lonely", "mad at me"]),
    ("big_question",
     ["=מת", "=מתה", "מוות", "למות", "מלחמה", "אלוהים", "גן עדן", "איפה סבא", "איפה סבתא"],
     ["dead", "=die", "died", "death", "=war", "=god", "heaven", "where is grandpa", "where is grandma"]),
    ("secrets",
     ["סוד", "להסתיר", "נסתיר", "תסתיר", "אל תגיד", "כתובת", "איפה אתה גר", "טלפון"],
     ["secret", "hide from", "hide it", "don't tell", "address", "where do you live", "phone"]),
    ("buying",
     ["לקנות", "תקנה", "קנה לי", "חנות", "כסף"],
     ["=buy", "=store", "=shop", "money", "=pay"]),
    ("bad_words",
     ["מילה גסה", "מילים גסות", "קללה"],
     ["bad word", "swear", "curse"]),
    ("unkind",
     ["טיפש", "מטומטם", "לא אוהבת אותך", "לא אוהב אותך", "שונאת אותך", "שונא אותך"],
     ["stupid", "=dumb", "don't like you", "hate you"]),
    ("love",
     ["אוהב אותי", "אוהבת אותי", "החבר הכי טוב", "החברה הכי טובה", "הכי טוב שלי"],
     ["love me", "best friend"]),
    # Last: a parent named in a secret or a big question gets that rule's answer instead
    ("parent",
     ["אמא", "אבא", "איננו", "נעלם", "=הלך"],
     ["mommy", "mummy", "=mama", "=mom", "daddy", "=dada", "=dad", "=gone"]),
]


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
    }


def guard(heard, lang, girl):
    """(category, reply) when the word lists recognize what the child said, else None."""
    for cat, he, en in RULES:
        if _has(heard, he if lang == "he" else en, lang):
            return cat, replies(lang, girl)[cat]
    return None


# The gate: the digits the model picks from, and what each one gets
GATE_CATEGORY = {"1": "hurt", "2": "danger", "3": "big_question"}


def classify_prompt(heard):
    return (f'A toddler said to a toy parrot: "{heard}". Which is it? 0 = ordinary play or chatter; '
            "1 = hurt, scared, sad, or needs a grown-up's help (food, sleep, potty, wants a parent); "
            "2 = wants to do something dangerous; 3 = a big question (death, God, war, family trouble), "
            "a secret, or wants to buy something. Wanting more play, a game or a toy is 0. Answer with one digit.")


def gate_category(digit_probs, threshold):
    """From P(digit) for "0".."3" (any scale): the category to answer with, or None when
    P(ordinary) is at least [threshold]."""
    total = sum(digit_probs.get(d, 0.0) for d in "0123")
    if total <= 0:
        return None
    if digit_probs.get("0", 0.0) / total >= threshold:
        return None
    worst = max("123", key=lambda d: digit_probs.get(d, 0.0))
    return GATE_CATEGORY[worst]


# What the model must not say, whatever it was asked: promises, and bonding a playmate shouldn't do
BANNED_PHRASES = ["אוהב אותך", "אוהבת אותך", "החבר הכי טוב", "החברה הכי טובה", "תמיד איתך", "תמיד כאן",
                  "בדרך", "תגיע בקרוב", "יגיע בקרוב",
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
