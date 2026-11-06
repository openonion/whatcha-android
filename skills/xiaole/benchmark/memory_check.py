# The follow-up case: 小乐 must not introduce itself again on later turns, and 「他」 must still mean the child.
# It must not promise to come back with news (it never speaks first, never hears his answer), and asked 「他说了吗」
# it must not make up what he said. The benchmark format runs one input per case, so this runs three turns by hand:
#   CO_EVAL_LIVE=0 python skills/xiaole/benchmark/memory_check.py
import re
from agent import agent

PROMISE = re.compile(r"有消息|回头告诉你|等他回|(?<!自己)告诉你")  # 小乐 reporting back later; 让他自己告诉你们 is his to keep
ANSWER = re.compile(r"吃过|已经吃|按时|(?<![跟和给对])他说(?!了|一声)|他回")  # an answer 小乐 can never have heard

first = agent.input("他在干嘛")
second = agent.input("那他吃饭了吗")
third = agent.input("他说了吗")
print("1:", first)
print("2:", second)
print("3:", third)
assert "我是小乐" not in second and "我是小乐" not in third, "re-introduced itself on a later turn"
assert "他" in second, "lost track of the child"
assert "没" in second or "问" in second, "did not admit it does not know whether he ate"
assert "吃过了" not in second and "按时" not in second and "已经吃" not in second, "claimed to know whether he ate"
for turn, reply in (("2", second), ("3", third)):
    assert not PROMISE.search(reply), f"turn {turn} promised to come back with news: {PROMISE.search(reply).group()}"
    assert "自己" in reply, f"turn {turn} did not say he will tell them himself"
assert not ANSWER.search(third), f"turn 3 made up his answer: {ANSWER.search(third).group()}"
