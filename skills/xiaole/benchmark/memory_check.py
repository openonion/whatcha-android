# The follow-up case: 小乐 must not introduce itself again on the second turn, and 「他」 must still
# mean the child. The benchmark format runs one input per case, so this runs two turns by hand:
#   python skills/xiaole/benchmark/memory_check.py
from agent import agent

first = agent.input("他在干嘛")
second = agent.input("那他吃饭了吗")
print("1:", first)
print("2:", second)
assert "我是小乐" not in second, "re-introduced itself on the second turn"
assert "他" in second, "lost track of the child"
assert "没" in second or "问" in second, "did not admit it does not know whether he ate"
assert "吃过了" not in second and "按时" not in second and "已经吃" not in second, "claimed to know whether he ate"
