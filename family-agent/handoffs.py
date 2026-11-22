"""Keep family requests durable and send optional, content-free iPhone alerts."""
import json
import os
import sqlite3
import subprocess
import time
from contextlib import closing
from pathlib import Path


class Handoffs:
    def __init__(self, directory: Path):
        directory.mkdir(mode=0o700, parents=True, exist_ok=True)
        self.path = directory / "handoffs.sqlite3"
        with closing(self.connect()) as db, db:
            db.execute("""CREATE TABLE IF NOT EXISTS handoffs (
                id INTEGER PRIMARY KEY, parent TEXT NOT NULL, text TEXT NOT NULL, resolved INTEGER DEFAULT 0
            )""")
            db.execute("CREATE TABLE IF NOT EXISTS devices (token TEXT PRIMARY KEY)")

    def connect(self):
        db = sqlite3.connect(self.path)
        db.row_factory = sqlite3.Row
        return db

    def add(self, parent: str, text: str) -> int:
        with closing(self.connect()) as db, db:
            # Retries of the exact same handoff share one inbox item briefly.
            db.execute("BEGIN IMMEDIATE")
            recent = db.execute(
                "SELECT id FROM handoffs WHERE parent = ? AND text = ? AND id > ? ORDER BY id DESC LIMIT 1",
                (parent, text, time.time_ns() - 60_000_000_000),
            ).fetchone()
            if recent:
                return recent["id"]
            handoff_id = time.time_ns()
            db.execute("INSERT INTO handoffs(id, parent, text) VALUES (?, ?, ?)", (handoff_id, parent, text))
            return handoff_id

    def pending(self) -> list[dict]:
        with closing(self.connect()) as db:
            return [dict(row) for row in db.execute(
                "SELECT id, parent, text FROM handoffs WHERE resolved = 0 ORDER BY id DESC LIMIT 100"
            )]

    def resolve(self, item_id: int) -> bool:
        with closing(self.connect()) as db, db:
            return db.execute("UPDATE handoffs SET resolved = 1 WHERE id = ?", (item_id,)).rowcount > 0

    def device(self, token: str, enabled: bool) -> None:
        with closing(self.connect()) as db, db:
            if enabled:
                db.execute("INSERT OR IGNORE INTO devices(token) VALUES (?)", (token,))
            else:
                db.execute("DELETE FROM devices WHERE token = ?", (token,))

    def alert_devices(self) -> None:
        certificate = os.environ.get("WHATCHA_APNS_CERT")
        key = os.environ.get("WHATCHA_APNS_KEY")
        if not certificate or not key:
            return
        host = "api.sandbox.push.apple.com" if os.environ.get("WHATCHA_APNS_SANDBOX") == "1" else "api.push.apple.com"
        with closing(self.connect()) as db:
            tokens = [row["token"] for row in db.execute("SELECT token FROM devices LIMIT 10")]
        payload = json.dumps({"aps": {"alert": {"title": "在干嘛", "body": "有一条家人的留言等你查看。"},
                                      "sound": "default"}, "whatcha": "handoffs"})
        for token in tokens:
            try:
                response = subprocess.run([
                    "curl", "--http2", "--silent", "--max-time", "5", "--cert", certificate, "--key", key,
                    "-H", "apns-topic: ai.openonion.whatcha", "-H", "apns-push-type: alert",
                    "-H", "apns-collapse-id: family-handoffs", "--data-binary", payload,
                    "--write-out", "\n%{http_code}", f"https://{host}/3/device/{token}",
                ], capture_output=True, text=True, check=False, timeout=6)
                if response.stdout.endswith("\n410"):
                    self.device(token, False)
            except (OSError, subprocess.TimeoutExpired):
                continue
