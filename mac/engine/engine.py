"""Murmur transcription engine.

A tiny long-running worker around faster-whisper. The Murmur app talks to it
over stdin/stdout, one JSON object per line:

  -> {"cmd": "load", "model": "small"}
  <- {"event": "loading", "model": "small"}
  <- {"event": "ready", "model": "small"}

  -> {"cmd": "transcribe", "id": 3, "path": "/tmp/x.wav", "language": "en", "session": 7}
  <- {"event": "result", "id": 3, "text": "Hello there.", "seconds": 0.8}

A recording arrives as several chunks with the same "session"; the text of
earlier chunks is passed to Whisper as context for the next one.
"""

import json
import os
import sys
import time
import warnings

warnings.filterwarnings("ignore")


def send(**msg):
    sys.stdout.write(json.dumps(msg) + "\n")
    sys.stdout.flush()


def main():
    import numpy as np
    from faster_whisper import WhisperModel

    model = None
    model_name = None
    threads = min(8, os.cpu_count() or 4)
    context_session = None
    context = ""

    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        try:
            req = json.loads(line)
        except json.JSONDecodeError:
            continue

        cmd = req.get("cmd")
        try:
            if cmd == "load":
                name = req.get("model") or "small"
                if name != model_name:
                    send(event="loading", model=name)
                    model = WhisperModel(
                        name, device="cpu", compute_type="int8", cpu_threads=threads
                    )
                    model_name = name
                    # Warm up so the first real dictation isn't slower than the rest.
                    list(model.transcribe(np.zeros(16000, dtype=np.float32), vad_filter=False)[0])
                send(event="ready", model=model_name)

            elif cmd == "transcribe":
                if model is None:
                    send(event="error", id=req.get("id"), message="Model not loaded")
                    continue
                session = req.get("session")
                if session != context_session:
                    context_session, context = session, ""
                start = time.time()
                segments, info = model.transcribe(
                    req["path"],
                    language=req.get("language") or None,
                    beam_size=5,
                    vad_filter=True,
                    condition_on_previous_text=False,
                    initial_prompt=context or None,
                )
                text = " ".join(s.text.strip() for s in segments).strip()
                if text:
                    context = (context + " " + text)[-200:].strip()
                print(
                    f"chunk session={session} audio={info.duration:.1f}s "
                    f"took={time.time() - start:.2f}s",
                    file=sys.stderr,
                    flush=True,
                )
                send(
                    event="result",
                    id=req.get("id"),
                    text=text,
                    seconds=round(time.time() - start, 2),
                )

            elif cmd == "quit":
                break
        except Exception as e:  # keep the worker alive no matter what
            send(event="error", id=req.get("id"), message=str(e))


if __name__ == "__main__":
    main()
