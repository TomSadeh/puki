# פוקי's brain (companion server)

The parrot in the games gets its words from a PC at home. Gemma 4 E4B writes a short
line about what just happened, and [BlueTTS](https://github.com/maxmelichov/BlueTTS)
speaks it in Hebrew or English. Holding the parrot records the child, and
[ivrit.ai](https://huggingface.co/ivrit-ai)'s Hebrew-tuned Whisper transcribes the
recording on the PC so the parrot can answer.

The tablet only talks to the PC over the home network. Nothing goes to the internet.
When the PC can't be reached (it's off, you're away, or a reply is too slow), the
parrot simply doesn't appear and the games use their recorded voice lines.

```
tablet ──Wi-Fi──▶ doorbell.py :8790 ──starts on demand──▶ llama-server :8792 (Gemma)
                  (always on, no GPU)                    brain.py     :8791 (Whisper + prompt + BlueTTS)
```

- **doorbell.py** runs from login. It uses no GPU and almost no memory, and only needs
  the Python standard library.
- When a game opens, the tablet calls `/wake`. The doorbell starts Gemma on the GPU if at
  least `min_free_vram_mb` is free, otherwise on the CPU (slower, about 2-4 s per line).
  Warming up takes about 30 seconds.
- After `idle_minutes` without requests, both heavy processes exit and the GPU memory is freed.

**Requirements:** a Windows PC (Linux works with small path changes) with Python 3.12 and,
ideally, an NVIDIA GPU with about 6 GB of free VRAM. Gemma and Whisper together use about
5.6 GB. Without a GPU everything runs on the CPU, just slower.

## Setup

Everything goes in `~/puki-brain` by default. You can change any path in the config.

1. **llama.cpp:** download a Windows CUDA build plus the matching `cudart` zip from
   [llama.cpp releases](https://github.com/ggml-org/llama.cpp/releases) and unzip both into
   `~/puki-brain/llama`.
2. **Gemma:** download `gemma-4-E4B-it-Q4_0.gguf` from
   [unsloth/gemma-4-E4B-it-GGUF](https://huggingface.co/unsloth/gemma-4-E4B-it-GGUF) into
   `~/puki-brain/models`. Also download `mmproj-F16.gguf` from the same repo and save it as
   `~/puki-brain/models/mmproj-gemma-4-E4B-F16.gguf`. That's Gemma's vision part, which lets
   Puki see drawings (about 1 GB of extra VRAM; leave it out and everything else still works).
   Gemma is subject to Google's [Gemma terms of use](https://ai.google.dev/gemma/terms).
3. **Whisper:** download all files of
   [ivrit-ai/whisper-large-v3-turbo-ct2](https://huggingface.co/ivrit-ai/whisper-large-v3-turbo-ct2)
   into `~/puki-brain/models/whisper-ivrit-turbo`.
4. **BlueTTS:** clone [BlueTTS](https://github.com/maxmelichov/BlueTTS) into
   `~/puki-brain/BlueTTS` and download its ONNX models as its README describes.
5. **Python environment** for brain.py:
   ```
   python -m venv ~/puki-brain/.venv
   ~/puki-brain/.venv/Scripts/pip install ~/puki-brain/BlueTTS -r brain/requirements.txt
   ```
6. **Your settings:** create `brain/config.json`. It's git-ignored, so the child's name stays
   private, and it overrides anything in `config.example.json`:
   ```json
   { "child_name": "<child's name>", "child_gender": "girl" }
   ```
   Write the name **without nikud**. BlueTTS mispronounces text that already has nikud.
7. **Firewall:** let the tablet through the Windows Firewall, home network only. Run this once
   in an **administrator** PowerShell:
   ```powershell
   New-NetFirewallRule -DisplayName "Puki companion" -Direction Inbound -Protocol TCP -LocalPort 8790 -Profile Private -Action Allow
   ```
   Your Wi-Fi must be set to *Private* in Windows network settings. Don't let Python itself
   through the firewall: if Windows asks when the doorbell first starts, choose **Cancel**,
   so the rule above (port 8790, home networks only) is the only way in, and the PC stays
   closed on public Wi-Fi.
8. **Start it automatically at login:** put a shortcut in `shell:startup` with this target,
   adjusting the path to where you cloned the repo:
   ```
   pythonw.exe "C:\path\to\puki\brain\doorbell.py"
   ```
9. **In the app:** open parent settings (long-press the gear), turn on **התוכי פוקי**, and
   enter the PC's address, for example `192.168.1.20:8790`, and the
   [pairing code](#pairing-code). A DHCP reservation in your router keeps that address from
   changing.

## Pairing code

The doorbell answers only requests that carry its **pairing code**, so another device on
the network (a guest's phone, a smart TV) can't make Puki talk or send it recordings.

- The code is made the first time the doorbell starts, printed in its console
  (`pairing code: K7M2-QX9P`), and kept in `brain/pairing_code.txt` (git-ignored). With
  `pythonw` there's no console, so open that file.
- Type it once in the app's parent settings, under the PC address. Case and the dash don't
  matter. Below the field, the app shows whether it's connected, the code is wrong, or the
  PC can't be found.
- Wrong codes are answered after a 1-second delay, and an address that sends 10 wrong codes
  within 10 minutes is shut out for 15 minutes.
- `python doorbell.py --new-code` makes a new code, for example if it leaked. Every tablet
  then needs the new code.
- The code travels unencrypted over the home Wi-Fi (plain HTTP). It keeps strangers'
  devices out; it isn't meant to protect against someone who already controls your network.

## Trying it

```
python brain/doorbell.py
curl -X POST -H "X-Puki-Code: K7M2-QX9P" localhost:8790/wake      # your code from pairing_code.txt
curl -H "X-Puki-Code: K7M2-QX9P" localhost:8790/health            # wait for "ready"
```

Every request needs the `X-Puki-Code` header; without it the doorbell answers 401.

| Endpoint | Takes | Returns |
|---|---|---|
| `POST /say` | `{"situation": "...", "lang": "he"}` | a short spoken reaction: OGG audio, with the line in `X-Text` |
| `POST /listen?lang=he[&context=...]` | a WAV recording of the child | the same, plus `X-Heard` with what Whisper heard |
| `POST /look?lang=he` | a PNG of a drawing | a spoken reaction to the drawing (Gemma vision) |
| `POST /story` | `{"played": "...", "icons": {"ic_animal_cow": "פרה", ...}, "lang": "he"}` | `{"sentences": [{"text", "icon"}]}`, a short bedtime story with one picture per sentence |
| `POST /speak` | `{"text": "...", "lang": "he", "speed": 0.9}` | the text spoken in the parrot's voice |

Every endpoint returns 204 (nothing) when no safe line came out, and the app falls back
to its recorded voices. The doorbell's console log shows every
situation, transcript and reply, which helps when tuning.

To test over USB without the firewall rule, run `adb reverse tcp:8790 tcp:8790` and set
the app's PC address to `127.0.0.1:8790`.

## Safety rails

- The prompt sets up a warm persona for a 2-year-old: short sentences, no "no" or "wrong",
  gentle encouragement after a mistake, no compliments tacked on, and the child's
  grammatical gender.
- A filter only lets through short lines in clean Hebrew or English with no banned words.
  If a reply fails the filter, the request returns nothing and the app falls back to its
  recorded lines.
- Small models repeat themselves, so each request suggests a different opening sound and
  lists the last few lines to avoid.
