#!/usr/bin/env python3
"""Issue one family ticket. Run beside agent.py with co's own Python (see RUNBOOK)."""
import argparse
import base64
from contextlib import contextmanager
import fcntl
import json
from pathlib import Path
import secrets
import time
from urllib.request import urlopen

from nacl.signing import SigningKey

TICKET_SECONDS = 24 * 60 * 60


def read_tickets(co_dir):
    path = co_dir / "tickets.json"
    return json.loads(path.read_text()) if path.exists() else {}


@contextmanager
def edit_tickets(co_dir):
    """Serialize issuer/host edits; readers see a whole file after replacement."""
    co_dir.mkdir(mode=0o700, parents=True, exist_ok=True)
    with (co_dir / "tickets.lock").open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        records = read_tickets(co_dir)
        yield records
        pending = co_dir / "tickets.json.pending"
        pending.touch(mode=0o600, exist_ok=True)
        pending.write_text(json.dumps(records, ensure_ascii=False, indent=2) + "\n")
        pending.replace(co_dir / "tickets.json")


def ticket_available(record, now):
    return record["used_at"] is None and 0 <= now - record["created_at"] < TICKET_SECONDS


def issue_ticket(co_dir, address, label):
    agent = bytes.fromhex(address.removeprefix("0x"))
    if len(agent) != 32:
        raise ValueError("agent address must contain 32 bytes")
    token = secrets.token_bytes(12)
    code = base64.urlsafe_b64encode(token).decode().rstrip("=")
    with edit_tickets(co_dir) as records:
        records[code] = {"label": label, "created_at": time.time(), "used_at": None}
    payload = base64.urlsafe_b64encode(agent + token).decode().rstrip("=")
    return f"「在干嘛」家人口令 #WC1.{payload}#\n长按这条消息，点「复制」，再打开「在干嘛」"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("label")
    parser.add_argument("--port", type=int, default=8000)
    args = parser.parse_args()
    co_dir = Path(__file__).resolve().parent / ".co"
    key_path = co_dir / "keys" / "agent.key"
    if not key_path.exists():
        key_path = Path.home() / ".co" / "keys" / "agent.key"
    if not key_path.exists():
        parser.error("No agent key found in this project or ~/.co. Start the host with its existing identity first.")
    key = SigningKey(key_path.read_bytes())
    with urlopen(f"http://localhost:{args.port}/info") as response:
        address = json.load(response)["address"]
    if address != "0x" + bytes(key.verify_key).hex():
        parser.error("This port serves a different agent. Start this project's host on its own port and use the same --port here.")
    print(issue_ticket(co_dir, address, args.label))


if __name__ == "__main__":
    main()
