# The old-news case: a fact already told in this chat today is told shorter, as old, the second time, never as
# the same new sentence. The benchmark runs one input per case, so this runs two turns by hand:
#   python skills/xiaole/benchmark/old_news_check.py
# The first reply opens with the introduction (20 characters); the comparison is against the fact after it.
from agent import agent

first = agent.input("他在干嘛")
second = agent.input("有什么好事")
print("1:", first)
print("2:", second)
fact = first.partition("AI。")[2]
assert second != fact, "told the same sentence as new twice"
assert len(second) <= len(fact), f"the second telling is longer than the first: {len(second)} > {len(fact)}"
assert "没新的" not in second, "handed the good thing back as scant"
assert "还是" in second or "说的那个" in second, "did not say it as old"
