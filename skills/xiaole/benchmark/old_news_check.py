# The old-news case: a fact already told in this chat today is told shorter, as old, the second time, never as
# the same new sentence. The benchmark runs one input per case, so this runs two turns by hand:
#   python skills/xiaole/benchmark/old_news_check.py
# The first reply opens with the introduction (20 characters); the comparison is against the fact after it.
# A page fact the first turn left out may follow the retelling once (round 21); a page fact told in both is the bug.
import re
from itertools import takewhile
from agent import agent

PAGE = ["谢谢", "数对"]  # the fixture page's facts past 改好了, as the parent hears them

first = agent.input("他在干嘛")
second = agent.input("有什么好事")
print("1:", first)
print("2:", second)
fact = first.partition("AI。")[2]
new = [word for word in PAGE if word in second]
assert not [word for word in new if word in fact], f"told the same thing as new twice: {new}"
clauses = re.split("(?<=[，。])", second)
retold = "".join(takewhile(lambda clause: not any(word in clause for word in new), clauses))
assert len(retold) <= len(fact), f"the retold part is longer than the first telling: {len(retold)} > {len(fact)}"
assert "没新的" not in second, "handed the good thing back as scant"
assert "还是" in second or "说的那个" in second, "did not say it as old"
