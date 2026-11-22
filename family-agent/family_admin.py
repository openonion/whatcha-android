"""Private HTTPS service for the child-side Whatcha app."""
import argparse
import hashlib
import hmac
import json
import os
import re
import sqlite3
import ssl
import time
from contextlib import closing
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlsplit
from uuid import UUID

from connectonion.network.host.session.storage import SessionStorage, session_owner
from connectonion.network.host.session.ui import session_to_chat_items
from nacl.exceptions import BadSignatureError
from nacl.signing import VerifyKey
from handoffs import Handoffs

from ticket import address_revoked, edit_tickets, issue_ticket, read_tickets, ticket_available


class ChildMessages:
    """Keep manual messages independent of the model's session writes."""

    def __init__(self, directory: Path):
        directory.mkdir(mode=0o700, parents=True, exist_ok=True)
        self.path = directory / "child-messages.sqlite3"
        with closing(self.connect()) as db, db:
            db.execute("""CREATE TABLE IF NOT EXISTS messages (
                sequence INTEGER PRIMARY KEY, id TEXT NOT NULL UNIQUE,
                parent TEXT NOT NULL, text TEXT NOT NULL, at INTEGER NOT NULL
            )""")
            db.execute("CREATE INDEX IF NOT EXISTS messages_by_parent ON messages(parent, sequence)")

    def connect(self):
        db = sqlite3.connect(self.path)
        db.row_factory = sqlite3.Row
        return db

    def send(self, parent: str, message_id: str, text: str) -> bool:
        with closing(self.connect()) as db, db:
            db.execute(
                "INSERT OR IGNORE INTO messages(id, parent, text, at) VALUES (?, ?, ?, ?)",
                (message_id, parent, text, time.time_ns() // 1_000_000),
            )
            record = db.execute("SELECT parent, text FROM messages WHERE id = ?", (message_id,)).fetchone()
            return (record["parent"], record["text"]) == (parent, text)

    def list(self, parent: str, after: int = 0) -> list[dict]:
        with closing(self.connect()) as db:
            return [dict(row) for row in db.execute(
                "SELECT id, sequence, text, at FROM messages WHERE parent = ? AND sequence > ? "
                "ORDER BY sequence LIMIT 100", (parent, after)
            )]


def paired_addresses(directory: Path) -> set[str]:
    return {record["address"] for record in read_tickets(directory).values()
            if record.get("address") and not record.get("reusable") and not record.get("revoked_at")}


def _chat_timestamps(session: dict) -> dict[tuple[str, str], int]:
    """Map chat item keys to Unix milliseconds from the corresponding host turn."""
    messages = session.get("messages", [])
    trace = session.get("trace", [])
    inputs = [event for event in trace if event.get("type") == "user_input"]
    user_indices = [index for index, msg in enumerate(messages)
                    if msg.get("role") == "user" and not msg.get("internal")]
    if len(inputs) == len(user_indices):
        input_by_index = dict(zip(user_indices, inputs))
    else:
        # Resumed legacy histories can have an untraced prefix. Recover only
        # the contiguous suffix whose text also agrees with the host trace.
        input_by_index = {}
        for index, event in zip(reversed(user_indices), reversed(inputs)):
            prompt = event.get("content")
            if not isinstance(prompt, str) or prompt != messages[index].get("content"):
                break
            input_by_index[index] = event
    results = {event["turn"]: event for event in trace
               if event.get("type") == "turn_result" and type(event.get("turn")) is int}

    timestamps = {}
    result = {}
    for index, msg in enumerate(messages):
        item_id = f"msg-{index}"
        if msg.get("role") == "user" and not msg.get("internal"):
            event = input_by_index.get(index, {})
            turn = event.get("turn")
            result = results.get(turn, {}) if type(turn) is int else {}
            sender = "user"
        elif msg.get("role") == "assistant" and msg.get("content"):
            event = result
            sender = "agent"
            if isinstance(msg.get("id"), str) and msg["id"]:
                item_id = msg["id"]
        else:
            continue
        ts = event.get("ts")
        if type(ts) in (int, float) and 0 <= ts * 1000 < 2**63:
            timestamps[sender, item_id] = int(ts * 1000)
    return timestamps


def family_overview(directory: Path, storage: SessionStorage) -> dict:
    now = time.time()
    members = []
    labels = {}
    for code, record in read_tickets(directory).items():
        if record.get("reusable") or record.get("revoked_at"):
            continue
        address = record.get("address")
        if address and address not in labels:
            labels[address] = record["label"]
            members.append({"id": address, "label": record["label"], "paired": True})
        elif not address and ticket_available(record, now):
            # Only a presentation ID is needed. Never return the invite secret.
            members.append({"id": hashlib.sha256(code.encode()).hexdigest(),
                            "label": record["label"], "paired": False})
    conversations = []
    for session in storage.list():
        owner = session_owner(session)
        label = labels.get(owner)
        if label is None:
            continue
        payload = session.session or {}
        timestamps = _chat_timestamps(payload)
        messages = []
        for item in session_to_chat_items(payload):
            if item["type"] in ("user", "agent") and isinstance(item.get("content"), str):
                message = {"id": item["id"], "sender": item["type"], "text": item["content"]}
                at = timestamps.get((item["type"], item["id"]))
                if at is not None:
                    message["at"] = at
                messages.append(message)
        if messages:
            conversations.append({"id": session.session_id, "parent": owner, "label": label, "messages": messages})
    return {"members": members, "conversations": conversations}


def handler_for(directory: Path, address: str, token: str, storage: SessionStorage, child_name: str,
                parent_origin: str | None = None):
    expected = f"Bearer {token}".encode()
    child_messages = ChildMessages(directory)
    handoffs = Handoffs(directory)

    class Handler(BaseHTTPRequestHandler):
        def respond(self, status: int, body: dict) -> None:
            data = json.dumps(body, ensure_ascii=False).encode()
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        def log_message(self, *_args) -> None:
            # Requests can concern private family data.
            return

        def authorized(self) -> bool:
            if hmac.compare_digest(self.headers.get("Authorization", "").encode(), expected):
                return True
            self.respond(401, {"error": "Unauthorized"})
            return False

        def do_GET(self) -> None:
            path = urlsplit(self.path)
            if path.path == "/family/child-messages":
                self.read_messages(path)
                return
            if not self.authorized():
                return
            if path.path == "/family/sent-messages":
                try:
                    params = parse_qs(path.query, strict_parsing=True)
                    parent, after = params["parent"][0], int(params["after"][0])
                    if not 0 <= after < 2**63:
                        raise ValueError
                except (ValueError, KeyError):
                    self.respond(400, {"error": "Invalid message cursor"})
                    return
                if parent not in paired_addresses(directory):
                    self.respond(404, {"error": "Unknown family member"})
                    return
                self.respond(200, {"messages": child_messages.list(parent, after)})
                return
            if self.path != "/family":
                self.respond(404, {"error": "Not found"})
                return
            overview = family_overview(directory, storage)
            paired = paired_addresses(directory)
            overview["handoffs"] = [item for item in handoffs.pending() if item["parent"] in paired]
            self.respond(200, overview)

        def read_messages(self, path) -> None:
            parent = self.headers.get("X-Whatcha-Parent", "")
            timestamp = self.headers.get("X-Whatcha-Time", "")
            signature = self.headers.get("X-Whatcha-Signature", "")
            try:
                after = int(parse_qs(path.query, strict_parsing=True)["after"][0])
                if (not re.fullmatch(r"0x[0-9a-f]{64}", parent)
                        or not re.fullmatch(r"[0-9a-f]{128}", signature)
                        or not re.fullmatch(r"[0-9]{1,12}", timestamp)
                        or abs(time.time() - int(timestamp)) > 60
                        or not 0 <= after < 2**63):
                    raise ValueError
                signed = f"whatcha-child-messages-v1\n{address}\n{parent}\n{timestamp}\n{after}"
                VerifyKey(bytes.fromhex(parent[2:])).verify(signed.encode(), bytes.fromhex(signature))
            except (ValueError, KeyError, BadSignatureError):
                self.respond(401, {"error": "Invalid parent proof"})
                return
            if parent not in paired_addresses(directory):
                self.respond(403, {"revoked": address_revoked(directory, parent)})
                return
            self.respond(200, {"messages": child_messages.list(parent, after)})

        def json_body(self, limit: int) -> dict:
            length = int(self.headers.get("Content-Length", "0"))
            if not 0 < length <= limit:
                raise ValueError
            body = json.loads(self.rfile.read(length))
            if not isinstance(body, dict):
                raise ValueError
            return body

        def do_POST(self) -> None:
            if not self.authorized():
                return
            if self.path == "/family/child-messages":
                self.send_message()
                return
            if self.path == "/family/revoke":
                self.revoke()
                return
            if self.path == "/family/notifications":
                self.notifications()
                return
            if self.path == "/family/resolve-handoff":
                try:
                    item_id = self.json_body(1024).get("id")
                    if type(item_id) is not int or not 0 < item_id < 2**63:
                        raise ValueError
                except ValueError:
                    self.respond(400, {"error": "Invalid handoff"})
                    return
                self.respond(200 if handoffs.resolve(item_id) else 404, {"id": item_id})
                return
            if self.path != "/family/invitations":
                self.respond(404, {"error": "Not found"})
                return
            try:
                label = self.json_body(1024).get("label")
                if not isinstance(label, str):
                    raise ValueError
                label = label.strip()
                if not 0 < len(label) <= 40 or not label.isprintable():
                    raise ValueError
            except (ValueError, UnicodeError):
                self.respond(400, {"error": "Invalid family label"})
                return
            invite = issue_ticket(directory, address, label)
            if parent_origin:
                invite = f"家庭消息：{parent_origin}\n{invite}"
            self.respond(201, {"invitation": f"孩子名字：{child_name}\n{invite}"})

        def notifications(self) -> None:
            try:
                body = self.json_body(1024)
                token, enabled = body.get("token"), body.get("enabled")
                if not isinstance(token, str) or not re.fullmatch(r"(?:[0-9a-f]{2}){16,256}", token):
                    raise ValueError
                if type(enabled) is not bool:
                    raise ValueError
            except ValueError:
                self.respond(400, {"error": "Invalid device registration"})
                return
            handoffs.device(token, enabled)
            self.respond(200, {"enabled": enabled})

        def revoke(self) -> None:
            try:
                identifier = self.json_body(1024).get("id")
                if not isinstance(identifier, str):
                    raise ValueError
            except ValueError:
                self.respond(400, {"error": "Invalid member"})
                return
            found = False
            with edit_tickets(directory) as tickets:
                for code, record in tickets.items():
                    if record.get("reusable"):
                        continue
                    member_id = record.get("address") or hashlib.sha256(code.encode()).hexdigest()
                    if member_id == identifier:
                        record["revoked_at"] = time.time()
                        found = True
            self.respond(200 if found else 404, {"id": identifier})

        def send_message(self) -> None:
            try:
                body = self.json_body(24576)
                parent, message_id, text = (body.get(field) for field in ("parent", "id", "text"))
                if not all(isinstance(value, str) for value in (parent, message_id, text)):
                    raise ValueError
                if str(UUID(message_id)) != message_id or not 0 < len(text.strip()) <= 2000:
                    raise ValueError
                text = text.strip()
                text.encode()
            except (ValueError, UnicodeError):
                self.respond(400, {"error": "Invalid message"})
                return
            if parent not in paired_addresses(directory):
                self.respond(404, {"error": "Unknown family member"})
                return
            if not child_messages.send(parent, message_id, text):
                self.respond(409, {"error": "Message ID already used"})
                return
            self.respond(200, {"id": message_id})

    return Handler


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--address", required=True, help="Public address of the family agent")
    parser.add_argument("--child-name", required=True, help="Name shared with invited family members")
    parser.add_argument("--cert", required=True)
    parser.add_argument("--key", required=True)
    parser.add_argument("--bind", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8443)
    parser.add_argument("--parent-origin", help="Public HTTPS origin included in new invitations")
    args = parser.parse_args()
    if not re.fullmatch(r"0x[0-9a-fA-F]{64}", args.address):
        parser.error("Provide the family agent's public 0x address")
    if args.parent_origin:
        origin = urlsplit(args.parent_origin)
        if (origin.scheme != "https" or not origin.hostname or origin.username is not None
                or origin.path not in ("", "/") or origin.query or origin.fragment):
            parser.error("Provide a public HTTPS origin without a path or credentials")
    token = os.environ.get("WHATCHA_FAMILY_ADMIN_TOKEN", "")
    if len(token) < 32 or not token.isascii() or not token.isalnum():
        parser.error("Set WHATCHA_FAMILY_ADMIN_TOKEN to at least 32 random alphanumeric characters")
    child_name = args.child_name.strip()
    if not 0 < len(child_name) <= 40 or not child_name.isprintable():
        parser.error("Child name must contain 1 to 40 printable characters")
    directory = Path(__file__).resolve().parent / ".co"
    storage = SessionStorage(directory / "session_results.jsonl")
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.load_cert_chain(args.cert, args.key)
    handler = handler_for(directory, args.address.lower(), token, storage, child_name, args.parent_origin)
    with ThreadingHTTPServer((args.bind, args.port), handler) as server:
        server.socket = context.wrap_socket(server.socket, server_side=True)
        server.serve_forever()


if __name__ == "__main__":
    main()
