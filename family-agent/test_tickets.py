"""Ticket onboarding with isolated project and HOME; no live host or model."""
import base64
import importlib
import json
import time

import pytest

CLIENT = "0x" + "11" * 32
OTHER = "0x" + "22" * 32
ADDRESS = "0x" + "33" * 32


@pytest.fixture
def family(tmp_path, monkeypatch):
    monkeypatch.setenv("HOME", str(tmp_path))
    monkeypatch.delenv("CO_INVITE_CODE", raising=False)
    monkeypatch.delenv("CO_PAYMENT", raising=False)
    monkeypatch.chdir(tmp_path)
    co_dir = tmp_path / ".co"
    co_dir.mkdir()
    agent = importlib.import_module("agent")
    ticket = importlib.import_module("ticket")
    return co_dir, agent.FamilyTrust(co_dir=co_dir), ticket


def issue(family):
    co_dir, _, ticket = family
    message = ticket.issue_ticket(co_dir, ADDRESS, "妈妈")
    payload = message.split("#WC1.")[1].split("#")[0]
    raw = base64.urlsafe_b64decode(payload + "=")
    assert len(payload) == 59
    assert raw[:32].hex() == ADDRESS[2:]
    assert message.splitlines()[1] == "长按这条消息，点「复制」，再打开「在干嘛」"
    return base64.urlsafe_b64encode(raw[32:]).decode().rstrip("=")


def test_accept_once_and_remember_label(family, capsys):
    co_dir, trust, _ = family
    token = issue(family)
    assert trust.verify_invite(CLIENT, token)
    output = capsys.readouterr().out
    assert f"✓ 妈妈 joined: {CLIENT[:16]}..." in output
    assert token not in output
    assert trust.get_level(CLIENT) == "contact"
    record = json.loads((co_dir / "tickets.json").read_text())[token]
    assert record["label"] == "妈妈"
    assert record["address"] == CLIENT
    assert record["used_at"] is not None
    assert trust.should_allow(CLIENT).allow


def test_refuse_second_use_after_restart(family):
    co_dir, trust, _ = family
    token = issue(family)
    assert trust.verify_invite(CLIENT, token)
    restarted = type(trust)(co_dir=co_dir)
    assert not restarted.verify_invite(OTHER, token)
    assert not restarted.verify_invite(CLIENT, token)
    assert restarted.should_allow(OTHER).reason == "Onboard required"
    assert restarted.get_level(OTHER) == "stranger"
    assert restarted.should_allow(CLIENT).allow


@pytest.mark.parametrize("age", [86400, 86401])
def test_refuse_expired(family, age):
    co_dir, trust, _ = family
    token = issue(family)
    path = co_dir / "tickets.json"
    records = json.loads(path.read_text())
    records[token]["created_at"] = time.time() - age
    path.write_text(json.dumps(records))
    assert trust.should_allow(CLIENT).reason == "Onboard required"
    assert not trust.verify_invite(CLIENT, token)
    assert trust.get_level(CLIENT) == "stranger"
    assert json.loads(path.read_text())[token]["used_at"] is None


def test_refuse_unknown(family):
    _, trust, _ = family
    issue(family)
    assert not trust.verify_invite(CLIENT, "unknown")
    assert trust.get_level(CLIENT) == "stranger"


def test_ticket_created_after_host_start_opens_onboard_door(family):
    from connectonion.network.trust.ws_admin import doors_that_open, get_onboard_requirements

    _, trust, _ = family
    assert get_onboard_requirements(trust) is None
    issue(family)
    assert doors_that_open(trust.config["onboard"], None) == {"methods": ["invite_code"]}
    assert trust.should_allow(CLIENT).reason == "Onboard required"


def test_legacy_invite_still_works(family, monkeypatch):
    _, trust, _ = family
    monkeypatch.setenv("CO_INVITE_CODE", "old-apk-code")
    assert trust.verify_invite(CLIENT, "old-apk-code")
    assert trust.should_allow(CLIENT).allow


def test_connect_cannot_bypass_ticket_consumption(family):
    co_dir, trust, _ = family
    token = issue(family)
    assert not trust.should_allow(CLIENT, {"invite_code": token}).allow
    assert trust.get_level(CLIENT) == "stranger"
    assert json.loads((co_dir / "tickets.json").read_text())[token]["used_at"] is None


def test_onboard_submit_reports_the_existing_refusal(family):
    import asyncio
    from connectonion.network.trust.ws_admin import handle_onboard_submit

    _, trust, _ = family
    token = issue(family)
    replies = []

    async def send(frame):
        replies.append(frame)

    routes = {"trust_agent": trust, "auth": lambda *_: (None, CLIENT, True, None)}
    frame = {"payload": {"invite_code": token}}
    assert asyncio.run(handle_onboard_submit(frame, send, routes)) == CLIENT
    assert replies[-1]["type"] == "ONBOARD_SUCCESS"
    assert asyncio.run(handle_onboard_submit(frame, send, routes)) is None
    assert replies[-1] == {"type": "ERROR", "message": "Invalid invite code"}


@pytest.mark.parametrize("options, port", [([], 8000), (["--port", "8123"], 8123)])
def test_command_prints_only_the_wechat_message(family, monkeypatch, capsys, options, port):
    import io

    co_dir, _, ticket = family
    monkeypatch.setattr(ticket, "__file__", str(co_dir.parent / "ticket.py"))
    def info(url):
        assert url == f"http://localhost:{port}/info"
        return io.StringIO(json.dumps({"address": ADDRESS}))

    monkeypatch.setattr(ticket, "urlopen", info)
    monkeypatch.setattr("sys.argv", ["ticket.py", "妈妈", *options])
    ticket.main()
    output = capsys.readouterr()
    assert output.err == ""
    lines = output.out.splitlines()
    assert len(lines) == 2
    assert lines[0].startswith("「在干嘛」家人口令 #WC1.")
    assert lines[0].endswith("#")
    assert lines[1] == "长按这条消息，点「复制」，再打开「在干嘛」"
    assert len(json.loads((co_dir / "tickets.json").read_text())) == 1


@pytest.mark.parametrize("state", ["used", "expired", "unknown"])
def test_connect_offers_door_then_submit_refuses(family, state):
    import asyncio
    from connectonion.network.host.ws_router.connect import handle_connect
    from connectonion.network.trust.ws_admin import handle_onboard_submit

    co_dir, trust, _ = family
    token = issue(family)
    if state == "used":
        assert trust.verify_invite(CLIENT, token)
    if state == "expired":
        path = co_dir / "tickets.json"
        records = json.loads(path.read_text())
        records[token]["created_at"] -= 86400
        path.write_text(json.dumps(records))
    if state == "unknown":
        token = "unknown"
    replies = []

    async def send(frame):
        replies.append(frame)

    routes = {
        "trust_agent": trust,
        "connect_auth": lambda _, policy, **kwargs: (None, OTHER, True, "forbidden: " + policy.should_allow(OTHER).reason),
        "auth": lambda *_: (None, OTHER, True, None),
    }
    asyncio.run(handle_connect({}, send, {}, routes, None, None, trust, None, None))
    assert replies[-1]["type"] == "ONBOARD_REQUIRED"
    assert replies[-1]["methods"] == ["invite_code"]
    asyncio.run(handle_onboard_submit({"payload": {"invite_code": token}}, send, routes))
    assert replies[-1] == {"type": "ERROR", "message": "Invalid invite code"}
    assert trust.get_level(OTHER) == "stranger"
