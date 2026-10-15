#!/usr/bin/env python3
"""The companion's brain: turns "what just happened in the game" into a short,
spoken, toddler-safe line. Started and stopped by doorbell.py; listens on
127.0.0.1 only.

POST /say {"situation": "...", "correct": true|false|null, "lang": "he"|"en"}
  -> 200 audio/ogg with header X-Text (URL-encoded line), or 204 if no safe line.

Needs the BlueTTS virtualenv (blue_onnx, numpy, soundfile) and a running
llama-server with Gemma (the doorbell starts both).
"""

import base64
import io
import json
import os
import random
import re
import sys
import threading
import urllib.parse
import urllib.request
from collections import deque
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import numpy as np
import soundfile as sf

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from doorbell import CFG, LLAMA_URL, log  # noqa: E402

NAME = re.sub(r"[\u0591-\u05C7]", "", CFG.get("child_name") or "")
GIRL = CFG.get("child_gender", "girl") == "girl"
COMPANION = re.sub(r"[\u0591-\u05C7]", "", CFG.get("companion_name", "פוקי"))
COMPANION_EN = CFG.get("companion_name_en", "Puki")
HEBREW_LETTER = re.compile(r"[\u05D0-\u05EA]")
CHILD = NAME or ("הילדה" if GIRL else "הילד")
_CONFIG_CHILD = (NAME, GIRL)
# One request at a time: Gemma, Whisper and BlueTTS run one job at a time anyway, and the
# child's name/gender below are per request
REQUEST_LOCK = threading.Lock()


def set_child(name, gender):
    """Use the name/gender the app sent (set in the app's settings), else the config's."""
    global NAME, GIRL, CHILD
    NAME = re.sub(r"[\u0591-\u05C7]", "", (name or "").strip())[:30] or _CONFIG_CHILD[0]
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


# Small models repeat themselves, so each request suggests a different opening
# sound and lists the last few lines to avoid.
FOOD_WORDS = ("האכיל", "אוכל", "fed ", "food")
OPENERS = {
    "he": {"food": ["יאמי", "מממ", "אמממ"], "other": ["הופ", "יש", "אוהו", "יופי", "טה-דה", "הידד", ""]},
    "en": {"food": ["Yum", "Mmm", "Munch munch"], "other": ["Hooray", "Yay", "Ooh", "Ta-da", "Woohoo", ""]},
}
recent = deque(maxlen=6)


def ask_gemma(situation, lang):
    kind = "food" if any(w in situation for w in FOOD_WORDS) else "other"
    opener = random.choice(OPENERS[lang][kind])
    hint = situation
    if lang == "he":
        if opener:
            hint += f"\n(תתחיל את התשובה ב-'{opener}!')"
        if recent:
            hint += "\n(אל תחזור על מה שאמרת קודם: " + " / ".join(recent) + ")"
    else:
        if opener:
            hint += f"\n(Start your reply with '{opener}!')"
        if recent:
            hint += "\n(Don't repeat what you said before: " + " / ".join(recent) + ")"
    messages = [{"role": "system", "content": system_prompt(lang)}]
    for user, reply in examples(lang):
        messages += [{"role": "user", "content": user}, {"role": "assistant", "content": reply}]
    messages.append({"role": "user", "content": hint})
    return chat(messages)


def chat(messages, max_tokens=48, temperature=0.9, response_format=None, timeout=45):
    payload = {
        "messages": messages,
        "max_tokens": max_tokens,
        "temperature": temperature,
        "top_p": 0.95,
        "chat_template_kwargs": {"enable_thinking": False},
    }
    if response_format:
        payload["response_format"] = response_format
    req = urllib.request.Request(f"{LLAMA_URL}/v1/chat/completions", data=json.dumps(payload).encode(),
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.load(r)["choices"][0]["message"]["content"]


# ---------- Looking at the child's drawing (Gemma vision) ----------

def look_line(png_bytes, lang):
    """A warm one-liner about a toddler's drawing, or None."""
    if lang == "en":
        ask = (f"{capital(child(lang))} just drew this picture (a 2-year-old's drawing, probably scribbles). "
               "In one or two short English sentences, say what you see: colors, lines, shapes, maybe a playful "
               "guess at what it is. Be delighted. Never say it doesn't look like something.")
    else:
        ask = (f"{CHILD} {'ציירה' if GIRL else 'צייר'} עכשיו את הציור הזה (ציור של פעוטה בת שנתיים, כנראה קשקושים). "
               "תגיב במשפט אחד או שניים קצרים: תגיד מה אתה רואה (צבעים, קווים, צורות, אולי ניחוש שובב מה זה) "
               "בהתלהבות. לעולם אל תגיד שזה לא דומה למשהו.")
    image = "data:image/png;base64," + base64.b64encode(png_bytes).decode()
    messages = [
        {"role": "system", "content": system_prompt(lang)},
        {"role": "user", "content": [{"type": "text", "text": ask}, {"type": "image_url", "image_url": {"url": image}}]},
    ]
    for _ in range(3):
        raw = chat(messages, max_tokens=60, temperature=0.8, timeout=60)
        text = clean(raw, lang)
        if text:
            return text
        log(f"rejected look: {raw!r}")
    return None


# ---------- Bedtime story ----------

STORY_SCHEMA = {
    "type": "object",
    "properties": {"sentences": {"type": "array", "minItems": 4, "maxItems": 7, "items": {
        "type": "object",
        "properties": {"text": {"type": "string"}, "icon": {"type": "string"}},
        "required": ["text", "icon"],
    }}},
    "required": ["sentences"],
}


def story(played, icons, lang):
    """A short calm bedtime story: [{"text", "icon"}], each icon one of [icons] (name -> label)."""
    names = "\n".join(f"- {k}: {v}" for k, v in icons.items())
    if lang == "en":
        c = child(lang)
        ask = (f"Tell {c} a very short, calm bedtime story in simple English (5-6 sentences, each at most "
               f"10 simple words). {capital(c)} is the hero. Today {c} played with: {played}. Use one or two "
               "of those things. Slow, warm and sleepy, ending with everyone falling asleep. No questions. "
               "For each sentence, choose the picture (icon) that fits it best from this list:\n" + names +
               "\nAnswer as JSON.")
        closing = f"Good night{', ' + NAME if NAME else ''}."
    else:
        ask = (f"ספר ל{CHILD} סיפור לפני השינה קצר ורגוע מאוד (5-6 משפטים, כל משפט עד 10 מילים פשוטות). "
               f"{CHILD} {'היא הגיבורה' if GIRL else 'הוא הגיבור'}. היום {CHILD} {'שיחקה' if GIRL else 'שיחק'} עם: {played}. "
               "תשתמש בדבר אחד או שניים מהם. איטי, חם ומנומנם, ובסוף כולם נרדמים. בלי שאלות. "
               "לכל משפט בחר את התמונה (icon) שהכי מתאימה לו מהרשימה הזאת:\n" + names + "\nתענה ב-JSON.")
        closing = f"לילה טוב{', ' + NAME if NAME else ''}."
    schema = json.loads(json.dumps(STORY_SCHEMA))
    schema["properties"]["sentences"]["items"]["properties"]["icon"]["enum"] = list(icons)
    messages = [{"role": "system", "content": system_prompt(lang)}, {"role": "user", "content": ask}]
    for _ in range(2):
        raw = chat(messages, max_tokens=400, temperature=0.8, timeout=90,
                   response_format={"type": "json_schema", "json_schema": {"schema": schema}})
        try:
            items = json.loads(raw)["sentences"]
        except (ValueError, KeyError, TypeError):
            log(f"story not JSON: {raw[:120]!r}")
            continue
        out = []
        for it in items:
            text = clean(str(it.get("text", "")), lang)
            if text and it.get("icon") in icons:
                out.append({"text": text, "icon": it["icon"]})
        if len(out) >= 3:
            out.append({"text": closing, "icon": "ic_bedtime_moon" if "ic_bedtime_moon" in icons else out[-1]["icon"]})
            return out
        log(f"story rejected: {raw[:160]!r}")
    return None


def line_for(situation, lang):
    for _ in range(3):
        raw = ask_gemma(situation, lang)
        text = clean(raw, lang)
        if text:
            recent.append(text)
            return text
        log(f"rejected: {raw!r}")
    return None


log("loading BlueTTS")
from blue_onnx import BlueTTS  # noqa: E402

_bt = CFG["bluetts_dir"]
TTS = BlueTTS(
    onnx_dir=os.path.join(_bt, "onnx_models"),
    style_json=os.path.join(_bt, "voices", f"{CFG['voice']}.json"),
    config_path=os.path.join(_bt, "config", "tts.json"),
)


# A run of English words, e.g. "dog" or "good night" inside a Hebrew line
ENGLISH_RUN = re.compile(r"[A-Za-z]+(?:[\s'’,-]+[A-Za-z]+)*")


def tag_english(text):
    """BlueTTS reads English inside a Hebrew line as gibberish unless it's wrapped in <en>…</en>."""
    if "<en>" in text:
        return text
    return ENGLISH_RUN.sub(lambda m: f"<en>{m.group(0)}</en>", text)


# A run of Hebrew words, e.g. the child's name typed in Hebrew inside an English line
HEBREW_RUN = re.compile(r"[\u05D0-\u05EA]+(?:[\s'\-]+[\u05D0-\u05EA]+)*")


def tag_hebrew(text):
    """The same the other way round: Hebrew inside an English line goes in <he>…</he>."""
    if "<he>" in text:
        return text
    return HEBREW_RUN.sub(lambda m: f"<he>{m.group(0)}</he>", text)


def speak(text, lang, speed=0.92):
    # BlueTTS mispronounces text that already has nikud (e.g. a name with nikud came out garbled)
    text = re.sub(r"[\u0591-\u05C7]", "", text)
    text = tag_english(text) if lang == "he" else tag_hebrew(text)
    samples, sr = TTS.synthesize(text, lang=lang, total_step=8, speed=speed)
    samples = np.concatenate([samples, np.zeros(int(sr * 0.15), dtype=np.float32)])
    buf = io.BytesIO()
    sf.write(buf, samples, sr, format="OGG", subtype="VORBIS")
    return buf.getvalue()


# ---------- Speech to text (ivrit.ai Whisper for Hebrew, Parakeet for English) ----------

def _add_cuda_dlls():
    """Let CTranslate2 find the cuBLAS / cuDNN DLLs installed as nvidia-* pip packages."""
    base = os.path.join(sys.prefix, "Lib", "site-packages", "nvidia")
    if not os.path.isdir(base):
        return
    for pkg in os.listdir(base):
        bin_dir = os.path.join(base, pkg, "bin")
        if os.path.isdir(bin_dir):
            os.add_dll_directory(bin_dir)
            os.environ["PATH"] = bin_dir + os.pathsep + os.environ["PATH"]


def _load_whisper():
    from faster_whisper import WhisperModel
    # GPU when there's room next to Gemma (and the training runs), CPU otherwise
    from doorbell import free_vram_mb
    if free_vram_mb() >= CFG["whisper_min_free_vram_mb"]:
        try:
            _add_cuda_dlls()
            model = WhisperModel(CFG["whisper_model"], device="cuda", compute_type="float16")
            log("whisper on GPU")
            return model
        except Exception as e:
            log(f"whisper GPU failed ({e}), using CPU")
    log("whisper on CPU")
    return WhisperModel(CFG["whisper_model"], device="cpu", compute_type="int8")


log("loading Whisper")
WHISPER = _load_whisper()

# English: NVIDIA's Parakeet (int8 ONNX) on the CPU, so it needs no VRAM next to Gemma.
# Loaded in the background on the first English request.
from parakeet import ParakeetOnnxEngine  # noqa: E402

PARAKEET = ParakeetOnnxEngine(CFG.get("parakeet_model") or "")


def has_parakeet():
    return os.path.isfile(os.path.join(PARAKEET.model_dir, "encoder-model.int8.onnx"))


def preload_parakeet():
    if PARAKEET.loaded:
        return
    if not has_parakeet():
        log(f"no Parakeet at {CFG.get('parakeet_model')!r} (parakeet_model): English uses the Hebrew Whisper")
        return
    threading.Thread(target=PARAKEET.warmup, daemon=True).start()


# Whisper "hears" these in silence or noise; treat them as nothing heard
HALLUCINATIONS = ("כתוביות", "תודה רבה", "תודה שצפיתם", "subtitles", "thank you for watching",
                  "thanks for watching", "please subscribe", "amara.org")
# ...and, in English, these when they're all it heard
HALLUCINATIONS_ALONE = ("you", "thank you", "thanks", "bye")

# Words Whisper should expect: the parrot's and child's names and things from the games
_plain = lambda s: re.sub(r"[֑-ׇ]", "", s)  # noqa: E731 (strip nikud)
def hotwords():
    return " ".join(filter(None, [
        _plain(COMPANION), _plain(NAME), "היי", "שלום", "פרה", "כלב", "חתול", "סוס", "פיל", "אריה",
        "ברווז", "תרנגולת", "קוף", "אדום", "כחול", "צהוב", "ירוק", "עיגול", "משולש", "ריבוע",
    ]))


def transcribe(wav_bytes, lang):
    # Decode the WAV ourselves (Whisper wants 16 kHz mono float samples)
    audio, sr = sf.read(io.BytesIO(wav_bytes), dtype="float32", always_2d=True)
    audio = audio.mean(axis=1)
    if sr != 16000:
        import librosa
        audio = librosa.resample(audio, orig_sr=sr, target_sr=16000)
    if lang == "en" and has_parakeet():
        heard = parakeet_transcribe(audio)
    else:
        segments, _ = WHISPER.transcribe(
            audio, language=lang, beam_size=1, vad_filter=True, hotwords=hotwords() if lang == "he" else None,
            condition_on_previous_text=False,
        )
        heard = " ".join(seg.text.strip() for seg in segments).strip()
    if any(h in heard.lower() for h in HALLUCINATIONS):
        return ""
    if lang == "en" and heard.lower().strip(" .!?,") in HALLUCINATIONS_ALONE:
        return ""
    return heard


def parakeet_transcribe(audio):
    """Parakeet has no voice detection of its own, so cut the recording to where someone talks."""
    from faster_whisper.vad import get_speech_timestamps
    speech = get_speech_timestamps(audio)
    if not speech:
        return ""
    pad = 16000 // 5
    return PARAKEET.transcribe(audio[max(0, speech[0]["start"] - pad):speech[-1]["end"] + pad], 16000).strip()


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


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        pass

    def do_GET(self):
        self.send_response(200 if self.path == "/health" else 404)
        self.end_headers()

    def _reply(self, text, lang, heard=None, speed=0.92):
        if not text:
            self.send_response(204)
            self.end_headers()
            return
        audio = speak(text, lang, speed)
        self.send_response(200)
        self.send_header("Content-Type", "audio/ogg")
        self.send_header("X-Text", urllib.parse.quote(text))
        if heard is not None:
            self.send_header("X-Heard", urllib.parse.quote(heard))
        self.send_header("Content-Length", str(len(audio)))
        self.end_headers()
        self.wfile.write(audio)

    def _line(self, situation, lang):
        try:
            return line_for(situation, lang)
        except Exception as e:
            log(f"gemma failed: {e}")
            return None

    def _json(self, obj):
        body = json.dumps(obj, ensure_ascii=False).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _fail(self, code):
        self.send_response(code)
        self.end_headers()

    def do_POST(self):
        # The app sends the child's name and gender with every request (?name=...&gender=girl)
        query = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)
        with REQUEST_LOCK:
            set_child(query.get("name", [""])[0], query.get("gender", [""])[0])
            self._post()

    def _post(self):
        url = urllib.parse.urlparse(self.path)
        query = urllib.parse.parse_qs(url.query)
        body = self.rfile.read(int(self.headers.get("Content-Length", 0)))
        try:
            if url.path in ("/say", "/speak", "/story"):
                req = json.loads(body or b"{}")
                lang = "en" if req.get("lang") == "en" else "he"
            else:
                lang = "en" if query.get("lang") == ["en"] else "he"
        except ValueError:
            return self._fail(400)
        if lang == "en" and url.path != "/speak":  # /speak en also comes from Hebrew tablets (name lines)
            preload_parakeet()

        if url.path == "/say":
            situation = str(req.get("situation", ""))[:300]
            text = self._line(situation, lang)
            log(f"{situation} -> {text}")
            self._reply(text, lang)
        elif url.path == "/speak":
            # Fixed text from the app (a question, a story sentence): just say it
            text = str(req.get("text", ""))[:200].strip()
            self._reply(text, lang, speed=float(req.get("speed", 0.92))) if text else self._fail(204)
        elif url.path == "/listen":
            # Body: a short WAV recording of the child; optional context=what Puki just asked
            try:
                heard = transcribe(body, lang)
            except Exception as e:
                log(f"whisper failed: {e}")
                return self._fail(400)
            situation = listen_situation(heard, lang)
            context = query.get("context", [""])[0][:200]
            if context:
                situation = context + " " + situation
            text = self._line(situation, lang)
            log(f"heard {heard!r} -> {text}")
            self._reply(text, lang, heard)
        elif url.path == "/look":
            # Body: PNG of the child's drawing
            try:
                text = look_line(body, lang)
            except Exception as e:
                log(f"look failed: {e}")
                text = None
            log(f"looked at a drawing -> {text}")
            self._reply(text, lang)
        elif url.path == "/story":
            icons = {str(k): str(v) for k, v in dict(req.get("icons", {})).items()}
            played = str(req.get("played", ""))[:300] or ("משחקים" if lang == "he" else "games")
            try:
                sentences = story(played, icons, lang) if icons else None
            except Exception as e:
                log(f"story failed: {e}")
                sentences = None
            log(f"story about {played!r} -> {sentences}")
            self._json({"sentences": sentences}) if sentences else self._fail(204)
        else:
            self._fail(404)


if __name__ == "__main__":
    speak("שלום", "he")  # loads the Hebrew pronunciation model before the first real request
    log(f"brain listening on 127.0.0.1:{CFG['brain_port']}")
    ThreadingHTTPServer(("127.0.0.1", CFG["brain_port"]), Handler).serve_forever()
