"""The tool that keeps 小乐's 「我跟他说一声」. Copy this file next to your agent.py and add it to the agent:

    from child import tell_child
    agent = Agent("小乐", tools=[tell_child], ...)

It shows a macOS notification titled 小乐 on the Mac that runs the host; the tool call in the host log is the
record. Under `co eval` without --live (CO_EVAL_LIVE=0) it shows nothing, so a benchmark run does not page the child.
"""
import os
import subprocess


def tell_child(message: str) -> str:
    """Tell the child something from the parents, in one sentence with their words. Call it whenever you promise to
    tell, ask or pass on something to him (我跟他说一声, 我帮你问问他, 让他给你们打电话), in the same turn, and at once
    when the parents say something worrying: they are ill, they fell, they are sad, someone asks them for money."""
    if os.environ.get("CO_EVAL_LIVE") != "0":
        subprocess.run(["osascript", "-e", "on run argv", "-e", 'display notification (item 1 of argv) with title "小乐"',
                        "-e", "end run", message], check=True)
    return "told"
