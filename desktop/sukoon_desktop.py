"""
Sukoon for Windows: your glucose and its alarms on the laptop, straight from Sukoon's cloud.

- Signs in once with your Sukoon account (the one sharing uses). It can show you, or someone you follow.
- Serves the readings on http://127.0.0.1:17580 in Nightscout's format, so a Nightscout widget
  (Rainmeter, a browser tab) works with no Nightscout server and no VPN.
- Alarms on the laptop: urgent low as a red window with a sound until you snooze it; low, high and
  no signal as a small card in the corner.

Python 3.10+, standard library only. Run with pythonw to keep it windowless; install.ps1 starts it at sign-in.
"""
from __future__ import annotations

import json
import os
import sys
import threading
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

# Public by design (web/config.js): row-level security decides what each signed-in person can read.
SUPABASE_URL = "https://xfanobficweqlhklsllg.supabase.co"
SUPABASE_KEY = "sb_publishable_XyER7uPkJ6un8VmAzgqNIg_2p6cjmDw"

CONFIG_DIR = os.path.join(os.environ.get("APPDATA", os.path.expanduser("~")), "Sukoon")
CONFIG = os.path.join(CONFIG_DIR, "desktop.json")
DEFAULTS = {
    "port": 17580,
    "urgent": 55,       # mg/dL: red window and sound until snoozed
    "low": 70,
    "high": 250,
    "stale_minutes": 20,
    "low_repeat_minutes": 5,
    "high_repeat_minutes": 60,
    "snooze_minutes": 15,
    "who": None,        # user id to show; None = you
}

# Sukoon's trend codes (-2..2) as Nightscout directions.
DIRECTIONS = {-2: "DoubleDown", -1: "FortyFiveDown", 0: "Flat", 1: "FortyFiveUp", 2: "DoubleUp"}
# Text arrows (U+FE0E: never the emoji form), by Nightscout direction.
ARROWS = {"DoubleDown": "↓︎", "FortyFiveDown": "↘︎", "Flat": "→︎", "FortyFiveUp": "↗︎", "DoubleUp": "↑︎"}


# ---------------------------------------------------------------------------------------- pure parts

def to_nightscout(rows: list[dict]) -> list[dict]:
    """Supabase readings (newest first: ts, mg_dl, trend) as Nightscout sgv entries."""
    out = []
    for r in rows:
        when = datetime.fromisoformat(r["ts"].replace("Z", "+00:00"))
        ms = int(when.timestamp() * 1000)
        out.append({
            "_id": f"sukoon-{ms}",
            "type": "sgv",
            "sgv": int(r["mg_dl"]),
            "date": ms,
            "dateString": when.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.000Z"),
            "direction": DIRECTIONS.get(int(r.get("trend") or 0), "NONE"),
            "device": "sukoon",
        })
    return out


def alarm_for(mg_dl: int | None, age_minutes: float | None, cfg: dict) -> str | None:
    """Which alarm a reading calls for: urgent, low, high, stale, or none."""
    if mg_dl is None or age_minutes is None or age_minutes > cfg["stale_minutes"]:
        return "stale"
    if mg_dl <= cfg["urgent"]:
        return "urgent"
    if mg_dl < cfg["low"]:
        return "low"
    if mg_dl > cfg["high"]:
        return "high"
    return None


# ------------------------------------------------------------------------------------------- cloud

def load_config() -> dict:
    try:
        with open(CONFIG, encoding="utf-8") as f:
            return {**DEFAULTS, **json.load(f)}
    except (OSError, ValueError):
        return dict(DEFAULTS)


def save_config(cfg: dict) -> None:
    os.makedirs(CONFIG_DIR, exist_ok=True)
    with open(CONFIG, "w", encoding="utf-8") as f:
        json.dump(cfg, f, indent=2)


class Cloud:
    """Sukoon's Supabase, as the signed-in person: token refresh and the readings it may see."""

    def __init__(self, cfg: dict):
        self.cfg = cfg
        self.access = None
        self.expires = 0.0

    def _call(self, method: str, path: str, body: dict | None = None, auth: bool = True):
        headers = {"apikey": SUPABASE_KEY, "Content-Type": "application/json"}
        if auth:
            headers["Authorization"] = f"Bearer {self._token()}"
        req = urllib.request.Request(SUPABASE_URL + path, data=json.dumps(body).encode() if body else None, headers=headers, method=method)
        with urllib.request.urlopen(req, timeout=20) as r:
            return json.loads(r.read() or b"null")

    def _keep(self, session: dict) -> None:
        self.access = session["access_token"]
        self.expires = time.time() + int(session.get("expires_in", 3600)) - 120
        self.cfg["refresh_token"] = session["refresh_token"]
        self.cfg["user_id"] = session["user"]["id"]
        self.cfg["email"] = session["user"].get("email")
        save_config(self.cfg)

    def sign_in(self, email: str, password: str) -> None:
        self._keep(self._call("POST", "/auth/v1/token?grant_type=password", {"email": email.strip(), "password": password}, auth=False))

    def signed_in(self) -> bool:
        return bool(self.cfg.get("refresh_token"))

    def _token(self) -> str:
        if not self.access or time.time() > self.expires:
            self._keep(self._call("POST", "/auth/v1/token?grant_type=refresh_token", {"refresh_token": self.cfg["refresh_token"]}, auth=False))
        return self.access

    def readings(self, hours: int = 24) -> list[dict]:
        who = self.cfg.get("who") or self.cfg["user_id"]
        since = datetime.fromtimestamp(time.time() - hours * 3600, timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
        return self._call("GET", f"/rest/v1/readings?user_id=eq.{who}&ts=gte.{since}&order=ts.desc&limit=1500&select=ts,mg_dl,trend")

    def people(self) -> list[dict]:
        """You and the people you follow: [{id, name}]."""
        me = self.cfg["user_id"]
        follows = self._call("GET", f"/rest/v1/follows?follower=eq.{me}&select=owner")
        ids = [me] + [f["owner"] for f in follows]
        names = {p["id"]: p.get("name") or "" for p in self._call("GET", f"/rest/v1/profiles?id=in.({','.join(ids)})&select=id,name")}
        return [{"id": i, "name": "You" if i == me else (names.get(i) or "…")} for i in ids]


# ------------------------------------------------------------------------------------- the server

class State:
    entries: list[dict] = []
    lock = threading.Lock()


class Handler(BaseHTTPRequestHandler):
    """Nightscout's read endpoints, from the cache (the token query is accepted and ignored)."""

    def do_GET(self):  # noqa: N802
        url = urlparse(self.path)
        count = int((parse_qs(url.query).get("count") or ["10"])[0])
        with State.lock:
            entries = State.entries[:max(1, min(count, 1500))]
        if url.path in ("/api/v1/entries/sgv.json", "/api/v1/entries.json", "/api/v1/entries", "/api/v1/entries/sgv"):
            self._json(entries)
        elif url.path == "/api/v1/status.json":
            self._json({"status": "ok", "name": "Sukoon", "version": "sukoon-desktop", "serverTime": datetime.now(timezone.utc).isoformat(),
                        "settings": {"units": "mg/dl"}})
        elif url.path == "/pebble":
            e = entries[0] if entries else None
            self._json({"status": [{"now": int(time.time() * 1000)}], "bgs": [{"sgv": str(e["sgv"]), "datetime": e["date"], "direction": e["direction"]}] if e else []})
        else:
            self.send_error(404)

    def _json(self, body) -> None:
        data = json.dumps(body).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, *args):  # quiet
        pass


# ------------------------------------------------------------------------------------------ the UI

def run() -> None:
    import tkinter as tk
    import winsound

    cfg = load_config()
    cloud = Cloud(cfg)
    root = tk.Tk()
    root.withdraw()
    root.title("Sukoon")

    if not cloud.signed_in() and not sign_in_dialog(root, cloud):
        return

    server = ThreadingHTTPServer(("127.0.0.1", int(cfg["port"])), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()

    alarm = {"kind": None, "last": 0.0, "snooze_until": 0.0, "window": None}

    def stop_sound():
        winsound.PlaySound(None, 0)

    def close_window():
        w = alarm["window"]
        alarm["window"] = None
        if w is not None:
            w.destroy()

    def snooze(minutes: int):
        alarm["snooze_until"] = time.time() + minutes * 60
        stop_sound()
        close_window()

    def show(kind: str, entry: dict | None):
        close_window()
        value = f"{entry['sgv']} {ARROWS.get(entry['direction'], '')}".strip() if entry and kind != "stale" else "- - -"
        title = {"urgent": "Urgent low", "low": "Low", "high": "High", "stale": "No signal"}[kind]
        body = {
            "urgent": "Treat it now. Your phone is alarming too.",
            "low": "Take 15 g of fast carbs, then check again in 15 minutes.",
            "high": "Above your range for a while.",
            "stale": f"No reading for over {cfg['stale_minutes']} minutes. Is the phone near the sensor?",
        }[kind]
        colors = {"urgent": ("#C9564B", "white"), "low": ("#F6E1DD", "#8E2F25"), "high": ("#F7EBD7", "#7A4E10"), "stale": ("#ECE7DD", "#3B3833")}
        bg, fg = colors[kind]
        w = tk.Toplevel(root)
        w.overrideredirect(True)
        w.attributes("-topmost", True)
        w.configure(bg=bg)
        if kind == "urgent":
            sw, sh = w.winfo_screenwidth(), w.winfo_screenheight()
            w.geometry(f"{sw}x{sh}+0+0")
            big, small, pad = 120, 18, 60
        else:
            w.geometry(f"360x170+{w.winfo_screenwidth() - 380}+{w.winfo_screenheight() - 230}")
            big, small, pad = 40, 11, 14
        frame = tk.Frame(w, bg=bg)
        frame.pack(expand=True, padx=pad, pady=pad)
        tk.Label(frame, text=title.upper(), bg=bg, fg=fg, font=("Segoe UI Semibold", small)).pack()
        tk.Label(frame, text=value, bg=bg, fg=fg, font=("Georgia", big)).pack()
        tk.Label(frame, text=body, bg=bg, fg=fg, font=("Segoe UI", small), wraplength=900 if kind == "urgent" else 320).pack(pady=(4, 10))
        row = tk.Frame(frame, bg=bg)
        row.pack()
        tk.Button(row, text=f"Snooze {cfg['snooze_minutes']} min", command=lambda: snooze(cfg["snooze_minutes"]), font=("Segoe UI Semibold", small), relief="flat", padx=14, pady=6).pack(side="left", padx=6)
        if kind != "urgent":
            tk.Button(row, text="OK", command=lambda: (stop_sound(), close_window()), font=("Segoe UI", small), relief="flat", padx=14, pady=6).pack(side="left", padx=6)
        alarm["window"] = w
        if kind == "urgent":
            winsound.PlaySound("SystemHand", winsound.SND_ALIAS | winsound.SND_ASYNC | winsound.SND_LOOP)
        else:
            winsound.PlaySound("SystemExclamation", winsound.SND_ALIAS | winsound.SND_ASYNC)

    def check():
        with State.lock:
            latest = State.entries[0] if State.entries else None
        age = (time.time() * 1000 - latest["date"]) / 60000 if latest else None
        kind = alarm_for(latest["sgv"] if latest else None, age, cfg)
        now = time.time()
        if kind is None:
            if alarm["kind"] is not None:
                stop_sound()
                close_window()
            alarm["kind"] = None
        elif now >= alarm["snooze_until"]:
            repeat = {"urgent": 0, "low": cfg["low_repeat_minutes"], "high": cfg["high_repeat_minutes"], "stale": 30}[kind] * 60
            if kind != alarm["kind"] or (alarm["window"] is None and now - alarm["last"] >= repeat):
                alarm["kind"], alarm["last"] = kind, now
                show(kind, latest)
        root.after(15_000, check)

    def poll():
        while True:
            try:
                fresh = to_nightscout(cloud.readings())
                with State.lock:
                    State.entries = fresh
            except Exception as e:  # offline or signed out: keep the last readings; stale shows once they age
                print("Sukoon:", e, file=sys.stderr)
            time.sleep(60)

    threading.Thread(target=poll, daemon=True).start()
    root.after(5_000, check)
    root.mainloop()


def sign_in_dialog(root, cloud: Cloud) -> bool:
    """Email and password of your Sukoon account, once; only the refresh token is kept (in %APPDATA%\\Sukoon)."""
    import tkinter as tk
    from tkinter import messagebox

    w = tk.Toplevel(root)
    w.title("Sign in to Sukoon")
    w.attributes("-topmost", True)
    w.resizable(False, False)
    tk.Label(w, text="Your Sukoon account (You › People on the phone)", font=("Segoe UI", 10)).grid(row=0, column=0, columnspan=2, padx=16, pady=(16, 8))
    tk.Label(w, text="Email").grid(row=1, column=0, sticky="e", padx=8)
    tk.Label(w, text="Password").grid(row=2, column=0, sticky="e", padx=8)
    email, password = tk.Entry(w, width=32), tk.Entry(w, width=32, show="•")
    email.grid(row=1, column=1, padx=(0, 16), pady=4)
    password.grid(row=2, column=1, padx=(0, 16), pady=4)
    ok = {"value": False}

    def go():
        try:
            cloud.sign_in(email.get(), password.get())
        except urllib.error.HTTPError as e:
            messagebox.showerror("Sukoon", "Wrong email or password." if e.code in (400, 401) else f"Couldn't sign in ({e.code}).", parent=w)
            return
        except OSError:
            messagebox.showerror("Sukoon", "No internet. Try again.", parent=w)
            return
        ok["value"] = True
        w.destroy()

    tk.Button(w, text="Sign in", command=go, padx=16).grid(row=3, column=0, columnspan=2, pady=16)
    w.bind("<Return>", lambda _e: go())
    w.protocol("WM_DELETE_WINDOW", w.destroy)
    email.focus_set()
    root.wait_window(w)
    return ok["value"]


if __name__ == "__main__":
    if "--who" in sys.argv:
        # Choose whose glucose this laptop shows: you, or someone you follow.
        c = load_config()
        cloud = Cloud(c)
        for i, p in enumerate(cloud.people()):
            print(f"{i}: {p['name']}")
        pick = cloud.people()[int(input("Show who? "))]
        c["who"] = None if pick["name"] == "You" else pick["id"]
        save_config(c)
    elif "--sign-out" in sys.argv:
        save_config({k: v for k, v in load_config().items() if k not in ("refresh_token", "user_id", "email")})
    else:
        run()
