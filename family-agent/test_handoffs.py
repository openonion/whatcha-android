from types import SimpleNamespace

from handoffs import Handoffs


def test_handoffs_reopen_resolve_and_deduplicate(tmp_path):
    inbox = Handoffs(tmp_path)
    item_id = inbox.add("paired-parent", "今晚可以给我打电话吗")
    assert inbox.add("paired-parent", "今晚可以给我打电话吗") == item_id
    restarted = Handoffs(tmp_path)
    assert restarted.pending() == [{"id": item_id, "parent": "paired-parent", "text": "今晚可以给我打电话吗"}]
    assert restarted.resolve(item_id)
    assert restarted.pending() == []


def test_push_uses_generic_payload_and_removes_expired_device(tmp_path, monkeypatch):
    inbox = Handoffs(tmp_path)
    inbox.add("secret-parent", "private message")
    inbox.device("aabbcc", True)
    monkeypatch.setenv("WHATCHA_APNS_CERT", "test-cert")
    monkeypatch.setenv("WHATCHA_APNS_KEY", "test-key")
    monkeypatch.setenv("WHATCHA_APNS_SANDBOX", "1")
    calls = []

    def send(command, **kwargs):
        calls.append(command)
        return SimpleNamespace(stdout="{}\n410")

    monkeypatch.setattr("handoffs.subprocess.run", send)
    inbox.alert_devices()
    inbox.alert_devices()
    assert len(calls) == 1
    assert calls[0][-1] == "https://api.sandbox.push.apple.com/3/device/aabbcc"
    assert "secret-parent" not in " ".join(calls[0])
    assert "private message" not in " ".join(calls[0])
