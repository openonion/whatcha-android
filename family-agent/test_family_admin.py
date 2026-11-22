import json
import time
from http.client import HTTPConnection
from http.server import ThreadingHTTPServer
from threading import Thread
from uuid import uuid4

from connectonion.network.host.session.storage import Session, SessionStorage
from nacl.signing import SigningKey

from family_admin import handler_for
from ticket import edit_tickets


def test_family_admin_requires_separate_token_and_only_returns_paired_sessions(tmp_path):
    storage = SessionStorage(tmp_path / "sessions.jsonl")
    for owner in ("paired-parent", "another-phone"):
        storage.save(Session(
            session_id=owner, status="complete", prompt="你在干嘛",
            session={
                "requester": {"address": owner},
                "messages": [
                    {"role": "system", "content": "hidden configuration"},
                    {"role": "user", "content": "你在干嘛"},
                    {"role": "assistant", "content": "在整理笔记"},
                ],
            },
        ))
    token = "synthetic-admin-token"
    handler = handler_for(tmp_path, "0x" + "33" * 32, token, storage, "小雨")
    with ThreadingHTTPServer(("127.0.0.1", 0), handler) as server:
        thread = Thread(target=server.serve_forever, daemon=True)
        thread.start()
        connection = HTTPConnection("127.0.0.1", server.server_port, timeout=5)
        try:
            connection.request("GET", "/family")
            response = connection.getresponse()
            assert response.status == 401
            response.read()
            headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
            connection.request("POST", "/family/invitations", json.dumps({"label": "妈妈"}), headers)
            response = connection.getresponse()
            assert response.status == 201
            invitation = json.load(response)["invitation"]
            assert invitation.startswith("孩子名字：小雨\n")
            connection.request("GET", "/family", headers=headers)
            before = json.load(connection.getresponse())
            assert before["members"][0]["paired"] is False
            assert before["conversations"] == []
            with edit_tickets(tmp_path) as tickets:
                next(iter(tickets.values())).update(address="paired-parent", used_at=1)
            connection.request("GET", "/family", headers=headers)
            after = json.load(connection.getresponse())
            assert after["members"][0]["paired"] is True
            [conversation] = after["conversations"]
            assert conversation["id"] == "paired-parent"
            assert [message["text"] for message in conversation["messages"]] == ["你在干嘛", "在整理笔记"]
        finally:
            connection.close()
            server.shutdown()
            thread.join(timeout=5)


def test_direct_messages_are_idempotent_persistent_and_visible_only_to_recipient(tmp_path):
    agent = "0x" + "33" * 32
    key, stranger = SigningKey.generate(), SigningKey.generate()
    parent = "0x" + key.verify_key.encode().hex()
    with edit_tickets(tmp_path) as tickets:
        tickets["accepted"] = {"address": parent}
    storage = SessionStorage(tmp_path / "sessions.jsonl")
    handler = handler_for(tmp_path, agent, "admin", storage, "小雨")
    with ThreadingHTTPServer(("127.0.0.1", 0), handler) as server:
        server_thread = Thread(target=server.serve_forever, daemon=True)
        server_thread.start()
        connection = HTTPConnection("127.0.0.1", server.server_port, timeout=5)

        def get_after(cursor, receiving_key=key, timestamp=None):
            address = "0x" + receiving_key.verify_key.encode().hex()
            timestamp = timestamp if timestamp is not None else int(time.time())
            proof = f"whatcha-child-messages-v1\n{agent}\n{address}\n{timestamp}\n{cursor}"
            headers = {
                "X-Whatcha-Parent": address, "X-Whatcha-Time": str(timestamp),
                "X-Whatcha-Signature": receiving_key.sign(proof.encode()).signature.hex(),
            }
            connection.request("GET", f"/family/child-messages?after={cursor}", headers=headers)
            response = connection.getresponse()
            return response.status, json.load(response)

        def send(text):
            connection.request("POST", "/family/child-messages",
                               json.dumps({"id": message_id, "parent": parent, "text": text}),
                               {"Authorization": "Bearer admin"})
            result = connection.getresponse()
            result.read()
            return result.status

        message_id = str(uuid4())
        try:
            assert send("我到了") == 200
            assert send("我到了") == 200
            assert send("更改过的内容") == 409
            status, page = get_after(0)
            assert status == 200
            message, = page["messages"]
            assert (message["id"], message["text"]) == (message_id, "我到了")
            assert get_after(message["sequence"]) == (200, {"messages": []})
            assert get_after(0, stranger)[0] == 403
            assert get_after(0, timestamp=1)[0] == 401
            from family_admin import ChildMessages
            assert ChildMessages(tmp_path).list(parent) == [message]
            with edit_tickets(tmp_path) as tickets:
                tickets["accepted"]["revoked_at"] = time.time()
            assert get_after(0)[0] == 403
            assert send("撤销后") == 404
        finally:
            connection.close()
            server.shutdown()
            server_thread.join(timeout=5)
