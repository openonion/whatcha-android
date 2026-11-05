"""小乐 for the parents' phone. Run from this directory: `co host --background`. See ../RUNBOOK.md."""
import sys
from pathlib import Path

from connectonion import Agent, host

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "skills/xiaole"))

from rem import recent_sessions, search_notebook, show_page  # noqa: E402
from voice import transcribe_voice  # noqa: E402

skill = (HERE.parent / "skills/xiaole/SKILL.md").read_text().split("---", 2)[2]

agent = Agent("小乐", system_prompt=skill, tools=[transcribe_voice, recent_sessions, show_page, search_notebook])
host(agent)
