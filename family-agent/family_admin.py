"""Private HTTPS service for the child-side Whatcha app."""
import argparse
import hashlib
import hmac
import json
import os
import re
import ssl
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from connectonion.network.host.session.storage import SessionStorage, session_owner
from connectonion.network.host.session.ui import session_to_chat_items

from ticket import issue_ticket, read_tickets, ticket_available


def family_overview(directory: Path, storage: SessionStorage) -> dict:
    now = time.time()
    members = []
    labels = {}
    for code, record in read_tickets(directory).items():
        if record.get("reusable"):
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
        label = labels.get(session_owner(session))
        if label is None:
            continue
        messages = [
            {"id": item["id"], "sender": item["type"], "text": item["content"]}
            for item in session_to_chat_items(session.session or {})
            if item["type"] in ("user", "agent") and isinstance(item.get("content"), str)
        ]
        if messages:
            conversations.append({"id": session.session_id, "label": label, "messages": messages})
    return {"members": members, "conversations": conversations}


def handler_for(directory: Path, address: str, token: str, storage: SessionStorage, child_name: str):
    expected = f"Bearer {token}".encode()

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
            if not self.authorized():
                return
            if self.path != "/family":
                self.respond(404, {"error": "Not found"})
                return
            self.respond(200, family_overview(directory, storage))

        def do_POST(self) -> None:
            if not self.authorized():
                return
            if self.path != "/family/invitations":
                self.respond(404, {"error": "Not found"})
                return
            try:
                length = int(self.headers.get("Content-Length", "0"))
                if not 0 < length <= 1024:
                    raise ValueError
                body = json.loads(self.rfile.read(length))
                label = body.get("label") if isinstance(body, dict) else None
                if not isinstance(label, str):
                    raise ValueError
                label = label.strip()
                if not 0 < len(label) <= 40 or not label.isprintable():
                    raise ValueError
            except (ValueError, UnicodeError):
                self.respond(400, {"error": "Invalid family label"})
                return
            invite = issue_ticket(directory, address, label)
            self.respond(201, {"invitation": f"孩子名字：{child_name}\n{invite}"})

    return Handler


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--address", required=True, help="Public address of the family agent")
    parser.add_argument("--child-name", required=True, help="Name shared with invited family members")
    parser.add_argument("--cert", required=True)
    parser.add_argument("--key", required=True)
    parser.add_argument("--bind", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8443)
    args = parser.parse_args()
    if not re.fullmatch(r"0x[0-9a-fA-F]{64}", args.address):
        parser.error("Provide the family agent's public 0x address")
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
    with ThreadingHTTPServer((args.bind, args.port), handler_for(directory, args.address, token, storage, child_name)) as server:
        server.socket = context.wrap_socket(server.socket, server_side=True)
        server.serve_forever()


if __name__ == "__main__":
    main()
