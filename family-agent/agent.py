"""小乐 for the parents' phone. Run from this directory: `co host --background`. See ../RUNBOOK.md."""
import sys
from pathlib import Path

from connectonion import Agent, before_llm, host

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "skills/xiaole"))

from child import tell_child  # noqa: E402
from rem import recent_sessions, search_notebook, show_page  # noqa: E402
from voice import transcribe_voice  # noqa: E402

skill = (HERE.parent / "skills/xiaole/SKILL.md").read_text().split("---", 2)[2]


@before_llm
def refresh_prompt(agent):
    # Stored sessions may carry instructions from before the host restarted.
    agent.current_session["messages"][0] = {"role": "system", "content": agent.system_prompt}


agent = Agent("小乐", system_prompt=skill, tools=[transcribe_voice, recent_sessions, show_page, search_notebook, tell_child],
              on_events=[refresh_prompt])
host(agent)
