import json
import time
from copy import deepcopy
from http.client import HTTPConnection
from http.server import ThreadingHTTPServer
from threading import Thread
from unittest.mock import Mock
from uuid import uuid4

from connectonion.network.host.session.storage import Session, SessionStorage
from nacl.signing import SigningKey

from family_admin import ChildMessages, family_overview, handler_for
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
            assert all("at" not in message for message in conversation["messages"])
        finally:
            connection.close()
            server.shutdown()
            thread.join(timeout=5)


def test_family_overview_preserves_three_quick_turns_with_trace_timestamps(tmp_path):
    parent = "paired-parent"
    with edit_tickets(tmp_path) as tickets:
        tickets["accepted"] = {"address": parent, "label": "妈妈"}
    epoch = 1_700_000_000
    session = {
        "requester": {"address": parent},
        "messages": [{"role": "system", "content": "hidden configuration"}],
        "trace": [],
    }
    expected = []
    for turn in range(1, 4):
        prompt, reply = "Are you there?", f"Reply {turn}"
        start_ms = (turn - 1) * 250
        user_id = f"msg-{len(session['messages'])}"
        reply_id = f"response-{turn}"
        session["messages"].extend([
            {"role": "user", "content": prompt},
            {"role": "user", "content": "hidden reminder", "internal": True},
            {"role": "assistant", "content": reply, "id": reply_id},
        ])
        session["trace"].extend([
            {"type": "user_input", "turn": turn, "ts": epoch + start_ms / 1000, "content": prompt},
            {"type": "turn_result", "turn": turn, "ts": epoch + (start_ms + 125) / 1000},
        ])
        expected.extend([
            {"id": user_id, "sender": "user", "text": prompt, "at": epoch * 1000 + start_ms},
            {"id": reply_id, "sender": "agent", "text": reply, "at": epoch * 1000 + start_ms + 125},
        ])

    record = Session(session_id="thread", status="complete", prompt=prompt, session=session)
    storage = Mock(spec=SessionStorage)
    storage.list.return_value = [record]
    before = deepcopy(record.session)
    [conversation] = family_overview(tmp_path, storage)["conversations"]
    assert conversation["messages"] == expected

    assert record.session == before

    # A missing result must not borrow the timestamp from the following turn.
    del record.session["trace"][3]
    del expected[3]["at"]
    [conversation] = family_overview(tmp_path, storage)["conversations"]
    assert conversation["messages"] == expected

    record.session["messages"][1:1] = [
        {"role": "user", "content": "Old question"},
        {"role": "assistant", "content": "Old answer"},
    ]
    [conversation] = family_overview(tmp_path, storage)["conversations"]
    messages = conversation["messages"]
    assert all("at" not in message for message in messages[:2])
    assert [message.get("at") for message in messages[2:]] == [message.get("at") for message in expected]


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
            assert ChildMessages(tmp_path).list(parent) == [message]
            connection.request("POST", "/family/revoke", json.dumps({"id": parent}),
                               {"Authorization": "Bearer admin"})
            revoked = connection.getresponse()
            assert revoked.status == 200
            revoked.read()
            assert get_after(0)[0] == 403
            assert send("撤销后") == 404
        finally:
            connection.close()
            server.shutdown()
            server_thread.join(timeout=5)
