"""The brain's dashboard: a small web page for the parent, on this PC only.

Shows whether the brain is awake, the pairing code (and a QR code for the app), and a
live log of what Puki was asked, heard and said. Served on 127.0.0.1 only (default
http://127.0.0.1:8793), never to the home network: the log holds the child's words.
It lives in memory and is gone when the doorbell stops.

Other websites open in the same browser can't use it: requests must name this PC as
127.0.0.1/localhost (stops DNS rebinding), and the buttons need a custom header that a
foreign page can't send without a CORS preflight, which is never granted.
"""

import collections
import json
import os
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import qr

HERE = os.path.dirname(os.path.abspath(__file__))


class Events:
    """The most recent things that happened, newest last."""

    def __init__(self, size=300):
        self.lock = threading.Lock()
        self.items = collections.deque(maxlen=size)
        self.next_id = 1

    def add(self, kind, **fields):
        with self.lock:
            self.items.append({"id": self.next_id, "t": time.time(), "kind": kind, **fields})
            self.next_id += 1

    def since(self, after):
        with self.lock:
            return [e for e in self.items if e["id"] > after]


def serve(app, port):
    """Starts the dashboard in a background thread. [app] provides status(), events,
    sleep(), new_code(), set_idle(minutes), set_model(choice) and pair_url()."""
    allowed_hosts = {f"127.0.0.1:{port}", f"localhost:{port}"}

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, fmt, *args):
            pass

        def _send(self, code, body, ctype):
            self.send_response(code)
            self.send_header("Content-Type", ctype)
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            self.send_header("X-Content-Type-Options", "nosniff")
            self.send_header("Content-Security-Policy",
                             "default-src 'self'; script-src 'self' 'unsafe-inline'; "
                             "style-src 'self' 'unsafe-inline'; img-src 'self'; frame-ancestors 'none'")
            self.end_headers()
            self.wfile.write(body)

        def _json(self, code, obj):
            self._send(code, json.dumps(obj, ensure_ascii=False).encode("utf-8"), "application/json; charset=utf-8")

        def _host_ok(self):
            if self.headers.get("Host", "") in allowed_hosts:
                return True
            self._json(403, {"error": "open the dashboard at http://127.0.0.1:%d" % port})
            return False

        def do_GET(self):
            if not self._host_ok():
                return
            path, _, query = self.path.partition("?")
            if path == "/":
                with open(os.path.join(HERE, "dashboard.html"), "rb") as f:
                    self._send(200, f.read(), "text/html; charset=utf-8")
            elif path == "/api/status":
                self._json(200, app.status())
            elif path == "/api/events":
                after = 0
                for part in query.split("&"):
                    if part.startswith("after="):
                        after = int(part[6:] or 0)
                self._json(200, app.events.since(after))
            elif path == "/api/qr.svg":
                self._send(200, qr.svg(app.pair_url()).encode(), "image/svg+xml")
            else:
                self._json(404, {"error": "not found"})

        def do_POST(self):
            if not self._host_ok():
                return
            if self.headers.get("X-Puki-Dashboard") != "1":
                self._json(403, {"error": "missing header"})
                return
            length = int(self.headers.get("Content-Length", 0) or 0)
            body = json.loads(self.rfile.read(min(length, 10000)) or b"{}") if length else {}
            if self.path == "/api/sleep":
                app.sleep()
            elif self.path == "/api/new-code":
                app.new_code()
            elif self.path == "/api/model":
                choice = str(body.get("choice", ""))
                if choice not in ("e4b", "12b"):
                    self._json(400, {"error": "choice must be e4b or 12b"})
                    return
                app.set_model(choice)
            elif self.path == "/api/idle":
                minutes = int(body.get("minutes", 0))
                if not 1 <= minutes <= 240:
                    self._json(400, {"error": "minutes must be 1-240"})
                    return
                app.set_idle(minutes)
            else:
                self._json(404, {"error": "not found"})
                return
            self._json(200, app.status())

    server = ThreadingHTTPServer(("127.0.0.1", port), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server
