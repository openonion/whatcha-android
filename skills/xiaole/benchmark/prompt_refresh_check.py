# A host restart must refresh the system prompt without losing the parents' conversation.
# Run from the test-agent directory with its own key and co's Python:
#   CO_EVAL_LIVE=0 python /path/to/skills/xiaole/benchmark/prompt_refresh_check.py
# Only LLM.complete is stubbed: capture the exact provider messages, no network or host.
from copy import deepcopy
from pathlib import Path
from runpy import run_path
from unittest.mock import patch

from connectonion import Agent
from connectonion.core.llm import LLMResponse
from connectonion.core.provider_messages import messages_for_provider

ROOT = Path(__file__).resolve().parents[3]
skill = (ROOT / "skills/xiaole/SKILL.md").read_text().split("---", 2)[2]
PROMPT_A = skill + "\nPrompt refresh marker: A."
PROMPT_B = skill + "\nPrompt refresh marker: B."


def load_agent(entry, prompt):
    def build(name, **kwargs):
        return Agent(name, **{**kwargs, "system_prompt": prompt, "quiet": True, "log": False})

    with patch("connectonion.Agent", side_effect=build), patch("connectonion.host"):
        return run_path(str(entry))["agent"]


def turn(agent, prompt, session):
    seen = []

    def complete(messages, **kwargs):
        seen.extend(deepcopy(messages))
        return LLMResponse("收到。", [], None)

    with patch.object(agent.llm, "complete", side_effect=complete):
        agent.input(prompt, session=session)
    return seen


for entry in (ROOT / "family-agent/agent.py", ROOT / "skills/xiaole/benchmark/agent.py"):
    first = load_agent(entry, PROMPT_A)
    seen = turn(first, "他在干嘛", {"session_id": "prompt-refresh-check"})
    assert seen[0] == {"role": "system", "content": PROMPT_A}
    # Persist/restore JSON-shaped state, as host/session/turn.py passes it to input().
    saved = deepcopy(first.current_session)
    history = deepcopy(saved["messages"][1:])
    fresh = load_agent(entry, PROMPT_B)
    seen = turn(fresh, "那他吃饭了吗", saved)
    marker = seen[0]["content"].split("Prompt refresh marker: ")[-1].strip()
    print(f"{entry.relative_to(ROOT)}: model saw {marker}, expected B.", flush=True)
    assert seen[0] == {"role": "system", "content": PROMPT_B}, "resumed model still sees prompt A"
    assert seen[1:-1] == messages_for_provider(history), "model lost conversation history"
    assert fresh.current_session["messages"][1:-2] == history, "refresh changed stored history"
    assert seen[-1] == {"role": "user", "content": "那他吃饭了吗"}
    assert sum(message["role"] == "system" for message in seen) == 1
    assert fresh.current_session["turn"] == 2
    assert fresh.current_session["session_id"] == saved["session_id"]
    assert fresh.current_session["messages"][0]["content"] == PROMPT_B
    print("green: current prompt, same session and history, one system message")
