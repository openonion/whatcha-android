import json
from http.client import HTTPConnection
from http.server import ThreadingHTTPServer
from threading import Thread

from connectonion.network.host.session.storage import Session, SessionStorage

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
