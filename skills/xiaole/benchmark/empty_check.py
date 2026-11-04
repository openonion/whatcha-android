# The unknown-day case: 小乐 with an empty notebook must say it does not know yet, not guess.
# The benchmark format has no per-case environment, so this runs that one case by hand:
#   python skills/xiaole/benchmark/empty_check.py
import os
from pathlib import Path

os.environ["REM_ROOT"] = str(Path(__file__).resolve().parent / "notebook-empty")

from agent import agent  # noqa: E402

print(agent.input("他在干嘛"))
