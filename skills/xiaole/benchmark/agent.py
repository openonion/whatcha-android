# 小乐 for `co eval`: the skill as system prompt, the voice and notebook tools, no host().
# REM_ROOT points at the fictional fixture notebook unless the caller already set it
# (REM_ROOT=.../notebook-empty for the unknown-day case).
import os
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
os.environ.setdefault("REM_ROOT", str(HERE / "notebook"))
sys.path.insert(0, str(HERE.parent))

from connectonion import Agent
from rem import recent_sessions, search_notebook, show_page
from voice import transcribe_voice

skill = (HERE.parent / "SKILL.md").read_text().split("---", 2)[2]

agent = Agent("小乐", system_prompt=skill, tools=[recent_sessions, show_page, search_notebook, transcribe_voice])
