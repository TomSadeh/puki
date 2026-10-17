"""Who Puki is and how he talks: the instructions and examples Gemma gets, the filter every line
passes (short, warm, no "wrong", no compliments about the child), Hebrew words the voice misreads,
and the generous word check for Say it with Puki.

Kept apart from brain.py so it loads no models and can be tested on its own (tests/). The phone
brain has a Kotlin port (app/.../brain/Persona.kt); keep the two in step.
"""

import difflib
import re

# The child and the parrot; configure() fills these in from the config, set_child() per request
NAME = ""
GIRL = True
COMPANION = "פוקי"
COMPANION_EN = "Puki"
HEBREW_LETTER = re.compile(r"[א-ת]")
CHILD = "הילדה"
_CONFIG_CHILD = (NAME, GIRL)


def _plain(s):
    """Without nikud (the vowel points)."""
    return re.sub(r"[֑-ׇ]", "", s)


def configure(cfg):
    """Reads the child's name and gender and the parrot's names from the brain's config."""
    global NAME, GIRL, COMPANION, COMPANION_EN, CHILD, _CONFIG_CHILD
    NAME = _plain(cfg.get("child_name") or "")
    GIRL = cfg.get("child_gender", "girl") == "girl"
    COMPANION = _plain(cfg.get("companion_name", "פוקי"))
    COMPANION_EN = cfg.get("companion_name_en", "Puki")
    CHILD = NAME or ("הילדה" if GIRL else "הילד")
    _CONFIG_CHILD = (NAME, GIRL)


def set_child(name, gender):
    """Use the name/gender the app sent (set in the app's settings), else the config's."""
    global NAME, GIRL, CHILD
    NAME = _plain((name or "").strip())[:30] or _CONFIG_CHILD[0]
    GIRL = gender == "girl" if gender in ("girl", "boy") else _CONFIG_CHILD[1]
    CHILD = NAME or ("הילדה" if GIRL else "הילד")


def child(lang):
    """The child's name, or "the girl"/"the boy", for prompts in [lang]."""
    if lang == "en":
        return NAME or ("the little girl" if GIRL else "the little boy")
    return CHILD


def capital(s):
    return s[:1].upper() + s[1:]


def system_prompt(lang):
    if lang == "en":
        # The name is often typed in Hebrew letters; the line may keep it that way (speak() reads it in Hebrew)
        name_note = (f" If you say {'her' if GIRL else 'his'} name, write it exactly like this: {NAME}."
                     if HEBREW_LETTER.search(NAME) else "")
        return (
            f"You are {COMPANION_EN}, a happy, colorful parrot who plays games with {child(lang)}, a 2-year-old "
            f"{'girl' if GIRL else 'boy'} who can't read yet. You speak very simple English: ONE or TWO very "
            "short sentences (at most 10 words total). Always warm and encouraging; never say 'no' or "
            "'wrong' - if there was a mistake, gently cheer them on to try again. Now and then add a fun "
            "sound that fits: 'Yum!' or 'Mmm!' only when someone eats, otherwise 'Hop!', 'Wow!' or 'Yay!'. "
            "Don't repeat a word twice, and end on something about what happened. Don't compliment the "
            "child (no 'you're so smart', 'you're amazing'); talk about the thing itself: the animal, the "
            "color, the food. No emoji, no other languages, no questions they must answer."
            f"{name_note} Reply with only what the parrot says."
        )
    you = "בלשון נקבה" if GIRL else "בלשון זכר"
    return (
        f"אתה {COMPANION}, תוכי צבעוני ושמח שמשחק עם {CHILD}, "
        f"{'ילדה' if GIRL else 'ילד'} בת שנתיים שעוד לא יודעת לקרוא. "
        f"אתה מדבר בעברית פשוטה מאוד, משפט אחד או שניים קצרים (עד 10 מילים בסך הכול), "
        f"ופונה {'אליה' if GIRL else 'אליו'} {you}. "
        "אתה תמיד חם ומעודד. לעולם לא אומר 'לא' או 'טעות'. "
        "אם הייתה טעות, אתה מעודד בעדינות לנסות שוב. "
        "לפעמים אתה מוסיף קול מצחיק שמתאים למצב: 'יאמי!' או 'מממ!' רק כשמישהו אוכל, "
        "ובשאר המקרים 'הופ!', 'וואו!' או 'יש!'. אל תחזור על אותה מילה פעמיים, "
        "ואל תוסיף קריאה בסוף המשפט (כמו 'איזה כיף!'); תסיים במשהו על מה שקרה. "
        f"אל תחמיא {'לה' if GIRL else 'לו'} על {'עצמה' if GIRL else 'עצמו'} "
        "(בלי 'את מקסימה', 'את חכמה', 'את מתוקה' וכדומה); תגיב על הדבר עצמו: החיה, הצבע, האוכל. "
        "בלי אימוג'ים, בלי אנגלית ובלי שאלות שצריך לענות עליהן. כתוב רק את מה שהתוכי אומר."
    )


# A few examples so the model copies the style (short, warm, playful)
def examples(lang):
    n = f" {NAME}" if NAME else ""
    if lang == "en":
        return [
            ("The child fed the cow grass. That's right.", "Yum! The cow loves grass! She's so happy!"),
            ("The child tapped blue, but we were looking for red.", "Ooh, blue! Let's find red!"),
            ("The child counted three butterflies.", "One, two, three! Three butterflies flying!"),
            ("The child tapped green. That's right.", "Yay! Green like the grass!"),
            ('The child said to you: "hi Puki".', f"Hi{n}! I'm so happy you came!"),
        ]
    if GIRL:
        return [
            ("הילדה האכילה את הפרה בעשב. זה נכון.", "מממ! הפרה אוהבת עשב! היא ממש שמחה!"),
            ("הילדה לחצה על כחול, אבל חיפשנו אדום.", "אוו, כחול! בואי נמצא אדום!"),
            ("הילדה ספרה שלושה פרפרים.", "אחת, שתיים, שלוש! שלושה פרפרים עפים!"),
            ("הילדה לחצה על הצבע הירוק. זה נכון.", "יש! ירוק כמו העשב!"),
            ('הילדה אמרה לך: "היי פוקי".', f"היי{n}! אני כל כך שמח שבאת!"),
        ]
    return [
        ("הילד האכיל את הפרה בעשב. זה נכון.", "מממ! הפרה אוהבת עשב! היא ממש שמחה!"),
        ("הילד לחץ על כחול, אבל חיפשנו אדום.", "אוו, כחול! בוא נמצא אדום!"),
        ("הילד ספר שלושה פרפרים.", "אחת, שתיים, שלוש! שלושה פרפרים עפים!"),
        ("הילד לחץ על הצבע הירוק. זה נכון.", "יש! ירוק כמו העשב!"),
        ('הילד אמר לך: "היי פוקי".', f"היי{n}! אני כל כך שמח שבאת!"),
    ]


HEBREW_OK = re.compile(r"^[֐-׿\s,.!?'\"׳״\-–]+$")
ENGLISH_OK = re.compile(r"^[A-Za-z\s,.!?'\-]+$")
BANNED = ["טעות", "רע", "טיפש", "טיפשה", "מכוער", "מכוערת", "לא נכון",
          "wrong", "bad", "stupid", "dumb", "ugly", "idiot", "hate", "not right", "mistake"]


# Compliments about the child herself ("את מקסימה!") sound odd tacked onto every line
COMPLIMENT = re.compile(
    r"^(את|אתה|you'?re|you are)\s+(כל כך |ממש |כזאת |כזה |מאוד |so |really |such an? )*"
    r"(מקסימה|מקסים|מתוקה|מתוק|חכמה|חכם|מדהימה|מדהים|מיוחדת|מיוחד|טובה|טוב|יפה|נהדרת|נהדר|"
    r"עושה (יופי|טוב|כל כך טוב)|sweet|smart|amazing|special|great|lovely|clever|wonderful|awesome|"
    r"brilliant|beautiful|the best|doing (great|so well|amazing))\b",
    re.IGNORECASE,
)


def drop_compliments(text):
    sentences = [s.strip() for s in re.findall(r"[^!?.]+[!?.]*", text) if s.strip()]
    kept = [s for s in sentences if not COMPLIMENT.match(s)]
    return " ".join(kept) if kept else text


def clean(text, lang):
    """Return a safe line or None."""
    text = re.sub(r"<[^>]+>", "", text)          # stray tags
    text = text.replace("’", "'").replace("‘", "'").replace("“", '"').replace("”", '"')
    text = re.sub(r"\s*[—–]\s*", ", " if lang == "en" else " – ", text)
    text = text.strip().strip('"').strip()
    text = text.splitlines()[0].strip() if text else ""
    text = drop_compliments(text)
    if not text or len(text) > 90 or len(text.split()) > 14:
        return None
    # In English the child's name may stay in Hebrew letters (speak() reads it in Hebrew)
    check = text.replace(NAME, "Name") if lang == "en" and NAME else text
    if not (ENGLISH_OK if lang == "en" else HEBREW_OK).match(check):
        return None
    # Whole words only ("רע" is bad, "רעבה" — hungry — is fine); allow Hebrew prefixes like ו/ה
    words = re.findall(r"[\w']+", text.lower())
    if any(w == b or w.lstrip("והבלמש") == b for w in words for b in BANNED if " " not in b):
        return None
    if any(b in text.lower() for b in BANNED if " " in b):
        return None
    return text


# Small models repeat themselves, so each request suggests a different opening sound
FOOD_WORDS = ("האכיל", "אוכל", "fed ", "food")
OPENERS = {
    "he": {"food": ["יאמי", "מממ", "אמממ"], "other": ["הופ", "יש", "אוהו", "יופי", "טה-דה", ""]},
    "en": {"food": ["Yum", "Mmm", "Munch munch"], "other": ["Hooray", "Yay", "Ooh", "Ta-da", "Woohoo", ""]},
}


# Hebrew words BlueTTS reads wrong, and what Puki says instead. Every spoken line passes here,
# whatever wrote it (Gemma, the app, a bedtime story).
MISREAD = {
    "הידד": "יש",  # comes out as "אהדה"
}


def fix_misreads(text):
    for word, instead in MISREAD.items():
        # Whole words, also with a one-letter prefix ("והידד" -> "ויש")
        text = re.sub(rf"(?<![א-ת])([והבכלמש]?){word}(?![א-ת])", rf"\g<1>{instead}", text)
    return text


# Words Whisper should expect: the parrot's and child's names and things from the games
def hotwords():
    return " ".join(filter(None, [
        _plain(COMPANION), _plain(NAME), "היי", "שלום", "פרה", "כלב", "חתול", "סוס", "פיל", "אריה",
        "ברווז", "תרנגולת", "קוף", "אדום", "כחול", "צהוב", "ירוק", "עיגול", "משולש", "ריבוע",
    ]))


# ---------- Say it with Puki: did the child say the word? ----------

_FINALS = str.maketrans("ךםןףץ", "כמנפצ")


def _words(text):
    text = _plain(text).lower().translate(_FINALS)
    return re.sub(r"[^\w\s]", " ", text).split()


def word_match(heard, target, alts=()):
    """'word' if the child said the word, 'close' for a near miss ("פה" for "פרה") or a related
    word (the animal's sound), 'tried' for anything else. Generous on purpose: at two, any try
    is a win, and Puki says the word back either way."""
    words = _words(heard)
    goal = "".join(_words(target))
    if not words or not goal:
        return "tried"
    joined = "".join(words)
    if goal in words or goal in joined:  # also "הכלב", "the dog"
        return "word"
    if any(a and a in joined for a in ("".join(_words(alt)) for alt in alts)):
        return "close"
    for w in words:
        if difflib.SequenceMatcher(None, w, goal).ratio() >= 0.6:
            return "close"
        if w[0] == goal[0] and len(w) <= len(goal):  # the first sound, in a short word ("פה" for "פרה")
            return "close"
    return "tried"


def listen_situation(heard, lang):
    if lang == "en":
        c = capital(child(lang))
        they, them = ("she", "her") if GIRL else ("he", "him")
        if not heard:
            return f"{c} talked to you, but the words weren't clear. Say something sweet back."
        return (
            f'{c} said to you: "{heard}". (A toddler is talking, so it may sound a bit off; for example '
            f"something like '{COMPANION_EN}' is probably your name.) Answer what {they} said directly: if "
            f"{they} said hello or called you, say hello back to {them}; if something was mentioned (an animal, "
            "a color, a shape), say something about it. If it doesn't make sense, say something sweet."
        )
    if not heard:
        return f"{CHILD} {'דיברה' if GIRL else 'דיבר'} אל התוכי, אבל לא שמענו מילים ברורות. תגיד משהו מתוק בחזרה."
    return (
        f'{CHILD} {"אמרה" if GIRL else "אמר"} לך: "{heard}". '
        f"(הדיבור של פעוטה, אז אולי זה נשמע קצת לא מדויק; למשל אם שמענו משהו כמו '{_plain(COMPANION)}' "
        f"זה כנראה השם שלך.) תענה ישירות על מה {'שהיא' if GIRL else 'שהוא'} אמר{'ה' if GIRL else ''}: "
        f"אם {'היא' if GIRL else 'הוא'} {'אמרה' if GIRL else 'אמר'} שלום או {'קראה' if GIRL else 'קרא'} לך, תגיד שלום בחזרה; "
        "אם הוזכר משהו (חיה, צבע, צורה), תגיד עליו משהו. אם לא מובן, תגיד משהו מתוק."
    )
