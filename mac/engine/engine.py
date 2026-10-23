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

With "cloud": {"key": ..., "model": ...} the chunk goes to OpenAI instead; if
that fails the local model does it, and the result carries "fallback": reason.
Every result says "via": "cloud" or "local". Cloud chunks run side by side, so
results can arrive out of order (the app puts them back in order by id).

  -> {"cmd": "warm"}   opens a connection to OpenAI ahead of the first chunk
"""

import http.client
import json
import os
import ssl
import sys
import threading
import time
import uuid
import warnings

warnings.filterwarnings("ignore")

out_lock = threading.Lock()


def send(**msg):
    with out_lock:
        sys.stdout.write(json.dumps(msg) + "\n")
        sys.stdout.flush()


def log(text):
    print(text, file=sys.stderr, flush=True)


class OpenAI:
    """Keeps a few HTTPS connections open, so each chunk skips the connect and TLS handshake."""

    HOST = "api.openai.com"

    def __init__(self):
        self.idle = []
        self.lock = threading.Lock()
        self.ssl = ssl.create_default_context()

    def _take(self):
        with self.lock:
            if self.idle:
                return self.idle.pop()
        return http.client.HTTPSConnection(self.HOST, timeout=20, context=self.ssl)

    def _give_back(self, conn):
        with self.lock:
            self.idle.append(conn)

    def warm(self):
        with self.lock:
            if self.idle:
                return
        conn = self._take()
        try:
            conn.connect()
            self._give_back(conn)
        except OSError:
            conn.close()

    def transcribe(self, path, cloud, language, prompt):
        """Sends one chunk to OpenAI's transcription API. Raises on any failure."""
        boundary = uuid.uuid4().hex
        parts = []

        def field(name, value):
            parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'.encode())

        field("model", cloud.get("model") or "gpt-4o-transcribe")
        field("response_format", "text")
        if language:
            field("language", language)
        if prompt:
            field("prompt", prompt)
        with open(path, "rb") as f:
            audio = f.read()
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="chunk.wav"\r\n'
                     f"Content-Type: audio/wav\r\n\r\n".encode() + audio + b"\r\n")
        parts.append(f"--{boundary}--\r\n".encode())
        body = b"".join(parts)
        headers = {
            "Authorization": "Bearer " + cloud["key"].strip(),
            "Content-Type": "multipart/form-data; boundary=" + boundary,
        }

        # A kept-open connection may have been closed by the server meanwhile: retry once on a fresh one.
        for attempt in range(2):
            conn = self._take() if attempt == 0 else http.client.HTTPSConnection(self.HOST, timeout=20, context=self.ssl)
            try:
                conn.request("POST", "/v1/audio/transcriptions", body=body, headers=headers)
                r = conn.getresponse()
                data = r.read().decode(errors="ignore")
            except (OSError, http.client.HTTPException):
                conn.close()
                if attempt == 1:
                    raise RuntimeError("No connection")
                continue
            self._give_back(conn)
            if r.status == 401:
                raise RuntimeError("The API key isn't valid")
            if r.status == 429 and "quota" in data:
                raise RuntimeError("No credit left on your OpenAI account")
            if r.status >= 300:
                raise RuntimeError(f"OpenAI error {r.status}")
            return data.strip()


def main():
    import numpy as np
    from faster_whisper import WhisperModel

    model = None
    model_name = None
    model_lock = threading.Lock()  # the local model handles one chunk at a time
    threads = min(8, os.cpu_count() or 4)
    state = {"session": None, "context": "", "cloud_paused_until": 0.0}
    state_lock = threading.Lock()
    openai = OpenAI()

    def transcribe(req):
        try:
            session = req.get("session")
            with state_lock:
                if session != state["session"]:
                    state["session"], state["context"] = session, ""
                context = state["context"]
                paused = time.time() < state["cloud_paused_until"]
            start = time.time()
            text, where, fallback = None, "local", None
            cloud = req.get("cloud")
            if cloud and cloud.get("key") and not paused:
                try:
                    text = openai.transcribe(req["path"], cloud, req.get("language"), context)
                    where = "cloud"
                except Exception as e:
                    fallback = str(e)
                    with state_lock:
                        state["cloud_paused_until"] = time.time() + 30  # use the local model for a while
            if text is None:
                if model is None:
                    send(event="error", id=req.get("id"), message="Model not loaded")
                    return
                with model_lock:
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
                with state_lock:
                    if state["session"] == session:
                        state["context"] = (state["context"] + " " + text)[-200:].strip()
            log(f"chunk session={session} via={where} took={time.time() - start:.2f}s"
                + (f" fallback={fallback}" if fallback else ""))
            result = dict(event="result", id=req.get("id"), text=text, via=where, seconds=round(time.time() - start, 2))
            if fallback:
                result["fallback"] = fallback
            send(**result)
        except Exception as e:  # keep the worker alive no matter what
            send(event="error", id=req.get("id"), message=str(e))

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
                    with model_lock:
                        model = WhisperModel(
                            name, device="cpu", compute_type="int8", cpu_threads=threads
                        )
                        model_name = name
                        # Warm up so the first real dictation isn't slower than the rest.
                        list(model.transcribe(np.zeros(16000, dtype=np.float32), vad_filter=False)[0])
                send(event="ready", model=model_name)

            elif cmd == "warm":
                threading.Thread(target=openai.warm, daemon=True).start()

            elif cmd == "transcribe":
                if req.get("cloud"):
                    threading.Thread(target=transcribe, args=(req,), daemon=True).start()
                else:
                    transcribe(req)

            elif cmd == "quit":
                break
        except Exception as e:  # keep the worker alive no matter what
            send(event="error", id=req.get("id"), message=str(e))


if __name__ == "__main__":
    main()
