"""
In-process Parakeet TDT 0.6B v2 STT via ONNX Runtime — CPU only, no PyTorch, no NeMo.
Puki uses it to hear English (Hebrew stays with ivrit.ai's Whisper). Copied from the
author's Auto-GM project (src/services/stt/parakeet_onnx_engine.py).

Runs the int8 export from HF `istupakov/parakeet-tdt-0.6b-v2-onnx` as three hand-rolled
sessions: the nemo128 log-mel preprocessor, the FastConformer encoder, and the fused
decoder+joint, whose TDT greedy loop runs in numpy.
Decoding semantics (state-update-on-emit, duration advance, max-tokens-per-frame guard,
vocab/detokenize rules) are a faithful port of `onnx_asr.models.nemo.NemoConformerTdt` +
`_AsrWithTransducerDecoding` — the package itself is NOT installed because it deps plain
`onnxruntime`, and the repo pins `onnxruntime-gpu` (the two wheels clobber each other's
install; only one may be present).

Sessions are pinned to CPUExecutionProvider: the GPU belongs to Gemma, and Parakeet's
transducer architecture clears realtime on CPU by a wide margin.
"""

import json
import logging
import re
import threading
import time
from pathlib import Path

import numpy as np

logger = logging.getLogger(__name__)

# --- Export spec (file layout of the istupakov HF repo) ---
MODEL_TYPE = "nemo-conformer-tdt"
PREPROCESSOR_FILE = "nemo128.onnx"
ENCODER_FILE = "encoder-model.int8.onnx"
DECODER_JOINT_FILE = "decoder_joint-model.int8.onnx"
VOCAB_FILE = "vocab.txt"
CONFIG_FILE = "config.json"

# --- Model spec (NeMo FastConformer-TDT) ---
SAMPLE_RATE = 16000
BLANK_TOKEN = "<blk>"
# Decoding guard from onnx-asr's contract: a config.json without the key means 10.
DEFAULT_MAX_TOKENS_PER_STEP = 10

# onnx-asr's detokenizer: vocab maps "▁"->" " at parse time, then this regex strips the
# leading space and any space not at a word boundary from the joined token string.
_DECODE_SPACE_PATTERN = re.compile(r"\A\s|\s\B|(\s)\b")


def parse_vocab(text: str) -> dict[int, str]:
    """Parse vocab.txt ("token id" lines); ▁ becomes a literal space (SentencePiece)."""
    return {
        int(idx): token.replace("▁", " ")
        for token, idx in (line.split(" ") for line in text.splitlines() if line)
    }


class ParakeetOnnxEngine:
    """Parakeet TDT transcription. Sessions lazy-loaded once; safe for repeated calls."""

    def __init__(self, model_dir: str, intra_op_threads: int = 0):
        """`model_dir` holds the istupakov export files. `intra_op_threads` 0 = ORT default."""
        self.model_dir = Path(model_dir).expanduser()
        self.intra_op_threads = intra_op_threads
        self._loaded = False
        self._load_lock = threading.Lock()

    @property
    def loaded(self) -> bool:
        return self._loaded

    # ------------------------------------------------------------------
    def _ensure_loaded(self) -> None:
        if self._loaded:
            return
        with self._load_lock:
            if self._loaded:
                return
            self._load()

    def _load(self) -> None:
        t_load = time.perf_counter()
        import onnxruntime as ort

        config = json.loads((self.model_dir / CONFIG_FILE).read_text(encoding="utf-8"))
        if config["model_type"] != MODEL_TYPE:
            raise ValueError(
                f"{self.model_dir / CONFIG_FILE}: model_type {config['model_type']!r}, "
                f"expected {MODEL_TYPE!r}"
            )
        if config["features_size"] != 128:
            raise ValueError(
                f"{self.model_dir / CONFIG_FILE}: features_size {config['features_size']}, "
                f"but the preprocessor graph is {PREPROCESSOR_FILE} (128 mel bins)"
            )
        self._max_tokens_per_step = int(
            config.get("max_tokens_per_step", DEFAULT_MAX_TOKENS_PER_STEP)
        )

        self._vocab = parse_vocab((self.model_dir / VOCAB_FILE).read_text(encoding="utf-8"))
        self._vocab_size = len(self._vocab)
        blank = [i for i, tok in self._vocab.items() if tok == BLANK_TOKEN]
        if len(blank) != 1:
            raise ValueError(f"{VOCAB_FILE}: expected exactly one {BLANK_TOKEN}, found {len(blank)}")
        self._blank_idx = blank[0]

        so = ort.SessionOptions()
        so.log_severity_level = 3
        so.intra_op_num_threads = self.intra_op_threads

        def mk(filename):
            t0 = time.perf_counter()
            path = self.model_dir / filename
            s = ort.InferenceSession(str(path), sess_options=so,
                                     providers=["CPUExecutionProvider"])
            logger.info(f"Parakeet ONNX: built {filename} in "
                        f"{time.perf_counter() - t0:.2f}s on {s.get_providers()[0]}")
            return s

        self._mel = mk(PREPROCESSOR_FILE)
        self._enc = mk(ENCODER_FILE)
        self._joint = mk(DECODER_JOINT_FILE)

        # LSTM state shapes come from the graph itself ([layers, batch, hidden]).
        shapes = {x.name: x.shape for x in self._joint.get_inputs()}
        self._state_shapes = (
            (shapes["input_states_1"][0], 1, shapes["input_states_1"][2]),
            (shapes["input_states_2"][0], 1, shapes["input_states_2"][2]),
        )

        self._loaded = True
        logger.info(f"Parakeet ONNX: engine ready in {time.perf_counter() - t_load:.2f}s")

    # ------------------------------------------------------------------
    def transcribe(self, samples: np.ndarray, sample_rate: int) -> str:
        """Transcribe a mono float32 waveform; resamples to 16 kHz at this boundary."""
        self._ensure_loaded()
        if samples.ndim != 1:
            raise ValueError(f"expected mono 1-D waveform, got shape {samples.shape}")
        if sample_rate != SAMPLE_RATE:
            from scipy.signal import resample_poly
            from math import gcd
            g = gcd(SAMPLE_RATE, sample_rate)
            samples = resample_poly(samples, SAMPLE_RATE // g, sample_rate // g)
        samples = np.ascontiguousarray(samples, dtype=np.float32)

        t0 = time.perf_counter()
        features, features_lens = self._mel.run(
            ["features", "features_lens"],
            {"waveforms": samples[None, :],
             "waveforms_lens": np.array([len(samples)], np.int64)},
        )
        t_mel = time.perf_counter()
        encoder_out, encoded_lens = self._enc.run(
            ["outputs", "encoded_lengths"],
            {"audio_signal": features, "length": features_lens},
        )
        t_enc = time.perf_counter()
        tokens = self._tdt_greedy(encoder_out[0].T, int(encoded_lens[0]))
        t_dec = time.perf_counter()

        logger.info(
            f"Parakeet ONNX: {len(samples) / SAMPLE_RATE:.2f}s audio -> "
            f"mel {t_mel - t0:.3f}s, encoder {t_enc - t_mel:.3f}s, "
            f"tdt loop {t_dec - t_enc:.3f}s ({len(tokens)} tokens)"
        )
        return self._detokenize(tokens)

    # ------------------------------------------------------------------
    def _tdt_greedy(self, encodings: np.ndarray, enc_len: int) -> list[int]:
        """TDT greedy decode over [T, dim] encoder frames (port of onnx-asr's loop).

        Per frame: the fused decoder+joint emits vocab+duration logits. A non-blank
        argmax token is emitted and the LSTM state advances; the duration head's argmax
        moves the time cursor. Blank (or `max_tokens_per_step` emissions on one frame)
        forces the cursor forward, so the loop always terminates.
        """
        state1 = np.zeros(self._state_shapes[0], np.float32)
        state2 = np.zeros(self._state_shapes[1], np.float32)
        tokens: list[int] = []
        t = 0
        emitted = 0
        while t < enc_len:
            outputs, new_s1, new_s2 = self._joint.run(
                ["outputs", "output_states_1", "output_states_2"],
                {
                    "encoder_outputs": encodings[t][None, :, None],
                    "targets": np.array([[tokens[-1] if tokens else self._blank_idx]], np.int32),
                    "target_length": np.array([1], np.int32),
                    "input_states_1": state1,
                    "input_states_2": state2,
                },
            )
            logits = np.squeeze(outputs)
            token = int(logits[: self._vocab_size].argmax())
            step = int(logits[self._vocab_size:].argmax())

            if token != self._blank_idx:
                state1, state2 = new_s1, new_s2
                tokens.append(token)
                emitted += 1

            if step > 0:
                t += step
                emitted = 0
            elif token == self._blank_idx or emitted == self._max_tokens_per_step:
                t += 1
                emitted = 0
        return tokens

    def _detokenize(self, ids: list[int]) -> str:
        joined = "".join(self._vocab[i] for i in ids)
        return re.sub(_DECODE_SPACE_PATTERN, lambda m: " " if m.group(1) else "", joined)

    # ------------------------------------------------------------------
    def warmup(self) -> None:
        """Build the sessions and pay first-run allocations off the critical path."""
        t0 = time.perf_counter()
        self._ensure_loaded()
        self.transcribe(np.zeros(SAMPLE_RATE // 2, np.float32), SAMPLE_RATE)
        logger.info(f"Parakeet ONNX: warmup {time.perf_counter() - t0:.2f}s")
