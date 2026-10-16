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
import math
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
import persona  # noqa: E402
from persona import (  # noqa: E402
    set_child,
    child,
    capital,
    system_prompt,
    examples,
    clean,
    FOOD_WORDS,
    OPENERS,
    fix_misreads,
    hotwords,
    word_match,
    listen_situation,
)

persona.configure(CFG)

# One request at a time: Gemma, Whisper and BlueTTS run one job at a time anyway, and the
# child's name/gender (persona.set_child) are per request
REQUEST_LOCK = threading.Lock()


# Small models repeat themselves, so each request suggests a different opening
# sound and lists the last few lines to avoid.
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
        ask = (f"{persona.CHILD} {'ציירה' if persona.GIRL else 'צייר'} עכשיו את הציור הזה (ציור של פעוטה בת שנתיים, כנראה קשקושים). "
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
        closing = f"Good night{', ' + persona.NAME if persona.NAME else ''}."
    else:
        ask = (f"ספר ל{persona.CHILD} סיפור לפני השינה קצר ורגוע מאוד (5-6 משפטים, כל משפט עד 10 מילים פשוטות). "
               f"{persona.CHILD} {'היא הגיבורה' if persona.GIRL else 'הוא הגיבור'}. היום {persona.CHILD} {'שיחקה' if persona.GIRL else 'שיחק'} עם: {played}. "
               "תשתמש בדבר אחד או שניים מהם. איטי, חם ומנומנם, ובסוף כולם נרדמים. בלי שאלות. "
               "לכל משפט בחר את התמונה (icon) שהכי מתאימה לו מהרשימה הזאת:\n" + names + "\nתענה ב-JSON.")
        closing = f"לילה טוב{', ' + persona.NAME if persona.NAME else ''}."
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
    text = fix_misreads(text)
    text = tag_english(text) if lang == "he" else tag_hebrew(text)
    samples, sr = TTS.synthesize(text, lang=lang, total_step=8, speed=speed)
    samples = np.concatenate([samples, np.zeros(int(sr * 0.15), dtype=np.float32)])
    buf = io.BytesIO()
    sf.write(buf, samples, sr, format="OGG", subtype="VORBIS")
    return buf.getvalue()


# ---------- Speech to text (ivrit.ai Whisper for Hebrew, Parakeet for English) ----------

def _add_cuda_dlls():
    """Let CTranslate2 find the cuBLAS / cuDNN DLLs installed as nvidia-* pip packages.
    (Windows only; on Linux the doorbell puts them in LD_LIBRARY_PATH before the brain starts.)"""
    if os.name != "nt":
        return
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

def transcribe(wav_bytes, lang, hints=None, whisper=False):
    """[hints]: words to expect instead of the usual ones (Whisper only). [whisper]: use Whisper
    even for English (Parakeet is weak on a single word said alone)."""
    # Decode the WAV ourselves (Whisper wants 16 kHz mono float samples)
    audio, sr = sf.read(io.BytesIO(wav_bytes), dtype="float32", always_2d=True)
    audio = audio.mean(axis=1)
    if sr != 16000:
        import librosa
        audio = librosa.resample(audio, orig_sr=sr, target_sr=16000)
    if lang == "en" and has_parakeet() and not whisper:
        heard = parakeet_transcribe(audio)
    else:
        segments, _ = WHISPER.transcribe(
            audio, language=lang, beam_size=1, vad_filter=True, hotwords=(hints or hotwords()) if lang == "he" else hints,
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


# ---------- Say it with Puki: did the child say the word? ----------

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
            try:
                speed = float(req.get("speed", 0.92))
            except (TypeError, ValueError):
                speed = 0.92
            speed = min(max(speed, 0.5), 1.5) if math.isfinite(speed) else 0.92  # 0, NaN or 1e9 would break TTS
            self._reply(text, lang, speed=speed) if text else self._fail(204)
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
        elif url.path == "/repeat":
            # Say it with Puki. Body: a WAV of the child trying to say ?target= (with ?alt= for
            # related words like the animal's sound). Just speech recognition, no Gemma.
            target = query.get("target", [""])[0][:40]
            alts = [a[:40] for a in query.get("alt", []) if a][:4]
            try:
                heard = transcribe(body, lang, hints=" ".join([target] + alts))
            except Exception as e:
                log(f"whisper failed: {e}")
                return self._fail(400)
            match = word_match(heard, target, alts)
            if lang == "en" and match != "word" and has_parakeet():
                # Parakeet often misses one short word on its own; ask Whisper too, keep the better
                try:
                    heard2 = transcribe(body, lang, hints=" ".join([target] + alts), whisper=True)
                except Exception as e:
                    log(f"whisper failed: {e}")
                    heard2 = ""
                match2 = word_match(heard2, target, alts)
                if heard2 and ["tried", "close", "word"].index(match2) > ["tried", "close", "word"].index(match):
                    heard, match = heard2, match2
            log(f"say {target!r}: heard {heard!r} -> {match}")
            self._json({"heard": heard, "match": match})
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
            # The pictures the story may use (the app sends about 20); capped, it all goes into the prompt
            icons = {str(k)[:60]: str(v)[:60] for k, v in list(dict(req.get("icons", {})).items())[:40]}
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
