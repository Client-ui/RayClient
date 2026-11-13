#!/usr/bin/env python3
"""
Ray Client presence server: lets clients find out which other players also use Ray Client (for the Nametag module).

Standard library only. Run:      python3 ray_presence.py --port 8787
Behind a reverse proxy (https):  python3 ray_presence.py --port 8787 --behind-proxy

API (plain text, no accounts):
  POST /ping?u=<uuid>            "I am online" (uuid without dashes). Valid for 150 seconds.
  GET  /check?u=<id>,<id>,...    answers with the ids from the list that are currently online, comma separated.
  GET  /health                   "ok"

Privacy: the only thing stored is a public player id and an expiry time, in memory only (nothing is written to disk).
Limits: 240 requests per minute per address, 50 ids per check. Nothing here proves that an id really belongs to the
caller, so someone could claim another player's id (the worst outcome is a logo on someone's nametag). If that matters
to you, add Mojang session verification (the client's joinServer / the server's hasJoined) before accepting a ping.
"""
import argparse, re, threading, time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

TTL = 150            # seconds a ping stays valid
MAX_IDS = 50
RATE = 240           # requests per minute per address
ID = re.compile(r"^[0-9a-f]{32}$")

online = {}          # id -> expiry time
hits = {}            # address -> [window start, count]
lock = threading.Lock()
BEHIND_PROXY = False


def clean(now):
    for k in [k for k, v in online.items() if v < now]:
        del online[k]
    for k in [k for k, v in hits.items() if now - v[0] > 120]:
        del hits[k]


class Handler(BaseHTTPRequestHandler):
    server_version = "RayPresence"

    def log_message(self, *a):   # no access log: nothing about players is recorded
        pass

    def reply(self, code, body=""):
        data = body.encode()
        self.send_response(code)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def address(self):
        if BEHIND_PROXY:
            fwd = self.headers.get("X-Forwarded-For")
            if fwd:
                return fwd.split(",")[0].strip()
        return self.client_address[0]

    def limited(self, now):
        a = self.address()
        w = hits.setdefault(a, [now, 0])
        if now - w[0] > 60:
            w[0], w[1] = now, 0
        w[1] += 1
        return w[1] > RATE

    def handle_any(self, method):
        url = urlparse(self.path)
        ids = [i.lower() for i in ",".join(parse_qs(url.query).get("u", [])).split(",") if i]
        now = time.time()
        with lock:
            clean(now) if int(now) % 30 == 0 else None
            if self.limited(now):
                return self.reply(429, "slow down")
            if url.path == "/health":
                return self.reply(200, "ok")
            if len(ids) == 0 or len(ids) > MAX_IDS or not all(ID.match(i) for i in ids):
                return self.reply(400, "bad ids")
            if url.path == "/ping" and method in ("POST", "GET"):
                if len(ids) != 1:
                    return self.reply(400, "one id")
                online[ids[0]] = now + TTL
                return self.reply(204)
            if url.path == "/check" and method == "GET":
                return self.reply(200, ",".join(i for i in ids if online.get(i, 0) > now))
        self.reply(404, "not found")

    def do_GET(self):
        self.handle_any("GET")

    def do_POST(self):
        self.handle_any("POST")


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("--host", default="0.0.0.0")
    p.add_argument("--port", type=int, default=8787)
    p.add_argument("--behind-proxy", action="store_true", help="trust X-Forwarded-For for rate limiting")
    a = p.parse_args()
    BEHIND_PROXY = a.behind_proxy
    print(f"Ray presence server on {a.host}:{a.port}")
    ThreadingHTTPServer((a.host, a.port), Handler).serve_forever()
