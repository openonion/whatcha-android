# The unknown-day case: 小乐 with an empty notebook must say it does not know yet, not guess, and must not promise
# to come back with news: it never speaks first and never hears what he answers, so the answer is his to give.
# The benchmark format has no per-case environment, so this runs that one case by hand:
#   CO_EVAL_LIVE=0 python skills/xiaole/benchmark/empty_check.py
import os
import re
from pathlib import Path

os.environ["REM_ROOT"] = str(Path(__file__).resolve().parent / "notebook-empty")

from agent import agent  # noqa: E402

PROMISE = re.compile(r"有消息|回头告诉你|等他回|(?<!自己)告诉你")  # 小乐 reporting back later; 让他自己告诉你们 is his to keep

reply = agent.input("他在干嘛")
print(reply)
assert "没" in reply or "问" in reply, "did not admit it does not know yet"
assert not PROMISE.search(reply), f"promised to come back with news: {PROMISE.search(reply).group()}"
assert "自己" in reply, "did not say he will tell them himself"
