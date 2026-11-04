"""The tools 小乐 needs to answer 「他在干嘛」. Copy this file next to your agent.py and add the three to the agent:

    from rem import recent_sessions, show_page, search_notebook
    agent = Agent("小乐", tools=[recent_sessions, show_page, search_notebook], ...)

They read the child's `co rem` notebook (~/.co/rem, or REM_ROOT) and return the pages as they are; the skill
decides what of them the parents may hear.
"""
import os
import subprocess
from pathlib import Path


def notebook_root() -> str:
    """Read REM_ROOT on every call, so a value loaded from the project's .env by host() is seen."""
    return os.environ.get("REM_ROOT", str(Path.home() / ".co/rem"))


def recent_sessions(count: int = 3) -> str:
    """The child's newest work sessions, newest first, each cut before '## What you typed'."""
    pages = sorted((Path(notebook_root()) / "sessions").glob("*.md"), reverse=True)[:count]
    return "\n\n".join(page.read_text().split("## What you typed")[0] for page in pages)


def show_page(page: str) -> str:
    """One notebook page, e.g. projects/lantern.md"""
    return subprocess.run(["co", "rem", "--root", notebook_root(), "show", page], capture_output=True, text=True, check=True).stdout


def search_notebook(text: str) -> str:
    """Exact text search across pages (co rem search)."""
    return subprocess.run(["co", "rem", "--root", notebook_root(), "search", text], capture_output=True, text=True, check=True).stdout
