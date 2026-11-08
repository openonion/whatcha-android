# 小乐 for `co eval`: the skill as system prompt, the voice, notebook and tell_child tools, no host().
# tell_child shows no notification under `co eval` (CO_EVAL_LIVE=0); run the *_check.py scripts with CO_EVAL_LIVE=0 too.
# REM_ROOT points at the fictional fixture notebook unless the caller already set it
# (REM_ROOT=.../notebook-empty for the unknown-day case).
import os
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
os.environ.setdefault("REM_ROOT", str(HERE / "notebook"))
sys.path.insert(0, str(HERE.parent))

from connectonion import Agent, before_llm
from child import tell_child
from rem import recent_sessions, search_notebook, show_page
from voice import transcribe_voice

skill = (HERE.parent / "SKILL.md").read_text().split("---", 2)[2]


@before_llm
def refresh_prompt(agent):
    # Stored sessions may carry instructions from before the host restarted.
    agent.current_session["messages"][0] = {"role": "system", "content": agent.system_prompt}


agent = Agent("小乐", system_prompt=skill, tools=[recent_sessions, show_page, search_notebook, transcribe_voice, tell_child],
              on_events=[refresh_prompt])
