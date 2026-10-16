"""Makes the music game's instrument sounds (drum, xylophone, tambourine, maracas).

Synthesised from scratch with the standard library only, so they're ours to ship.
Writes 16-bit mono WAVs to app/src/main/res/raw/snd_<instrument>.wav.

    python scripts/generate_instruments.py
"""
import math
import random
import struct
import wave
from pathlib import Path

RATE = 44100
OUT = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res" / "raw"


def write(name, samples):
    peak = max(abs(s) for s in samples) or 1.0
    scale = 0.85 / peak  # leave a little headroom
    # A few ms fade at both ends, so nothing clicks
    fade = int(RATE * 0.003)
    for i in range(fade):
        samples[i] *= i / fade
        samples[-1 - i] *= i / fade
    with wave.open(str(OUT / f"snd_{name}.wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(s * scale * 32767)) for s in samples))
    print(f"snd_{name}.wav  {len(samples) / RATE:.2f}s")


def drum():
    """A round, friendly toy drum: a low thump whose pitch drops, and a soft skin slap."""
    rnd = random.Random(1)
    n = int(RATE * 0.55)
    out, phase = [], 0.0
    for i in range(n):
        t = i / RATE
        freq = 70 + 110 * math.exp(-t * 28)  # pitch falls as the skin settles
        phase += 2 * math.pi * freq / RATE
        body = math.sin(phase) * math.exp(-t * 7)
        slap = (rnd.random() * 2 - 1) * math.exp(-t * 60) * 0.35
        out.append(body + slap)
    return out


def xylophone():
    """One wooden bar (C5). The game plays it faster or slower for the other notes."""
    n = int(RATE * 0.9)
    f = 523.25
    # A struck bar's overtones aren't whole multiples, and they die away quicker
    partials = [(1.0, 1.0, 4.5), (3.93, 0.35, 14), (9.2, 0.12, 30)]
    out = []
    for i in range(n):
        t = i / RATE
        s = sum(a * math.sin(2 * math.pi * f * m * t) * math.exp(-t * d) for m, a, d in partials)
        s += math.sin(2 * math.pi * 2600 * t) * math.exp(-t * 400) * 0.3  # the mallet's tick
        out.append(s)
    return out


def tambourine():
    """A shake and a tap: jingles ringing over a light skin hit."""
    rnd = random.Random(2)
    n = int(RATE * 0.7)
    jingles = [(rnd.uniform(3200, 7800), rnd.uniform(0, 2 * math.pi), rnd.uniform(9, 16)) for _ in range(14)]
    hits = [0.0, 0.11, 0.2]  # the jingles clash a few times as it shakes
    out = []
    for i in range(n):
        t = i / RATE
        s = 0.0
        for h, start in enumerate(hits):
            dt = t - start
            if dt < 0:
                continue
            level = (1.0, 0.6, 0.4)[h]
            ring = sum(math.sin(2 * math.pi * f * dt + p) * math.exp(-dt * d) for f, p, d in jingles) / len(jingles)
            hiss = (rnd.random() * 2 - 1) * math.exp(-dt * 45)
            s += level * (ring * 0.8 + hiss * 0.45)
        thump = math.sin(2 * math.pi * 190 * t) * math.exp(-t * 25) * 0.5
        out.append(s + thump)
    return out


def maracas():
    """Two quick shakes: bright bursts of rattling seeds."""
    rnd = random.Random(3)
    n = int(RATE * 0.42)
    out, prev = [], 0.0
    for i in range(n):
        t = i / RATE
        env = 0.0
        for start, level in ((0.0, 1.0), (0.17, 0.85)):
            dt = t - start
            if dt >= 0:
                env += level * (1 - math.exp(-dt * 400)) * math.exp(-dt * 22)
        x = rnd.random() * 2 - 1
        hp = x - prev  # crude high-pass: keeps it a crisp "ch", not a hiss
        prev = x
        grain = 1.0 if rnd.random() < 0.35 else 0.4  # the seeds hit unevenly
        out.append(hp * env * grain)
    return out


if __name__ == "__main__":
    write("drum", drum())
    write("xylophone", xylophone())
    write("tambourine", tambourine())
    write("maracas", maracas())
