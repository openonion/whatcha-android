from family_rem import SharedREM


def test_shared_rem_excludes_private_files_links_and_raw_quotes(tmp_path):
    root = tmp_path / "shared"
    sessions = root / "sessions"
    sessions.mkdir(parents=True)
    private = tmp_path / "private.md"
    private.write_text("隐私秘密")
    (sessions / "2026-11-22.md").write_text("修好了店里的库存软件\n## What you typed\n隐私原话")
    (root / "linked.md").symlink_to(private)

    shared = SharedREM(root)
    assert shared.recent_sessions() == "修好了店里的库存软件"
    assert "修好了店里的库存软件" in shared.search_notebook("库存")
    assert "隐私" not in shared.search_notebook("隐私")
    assert "隐私" not in shared.show_page("../private.md")
    assert "隐私" not in shared.show_page("linked.md")
