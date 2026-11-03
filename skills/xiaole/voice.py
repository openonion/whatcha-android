"""The tool 小乐 needs for voice messages. Copy this file next to your agent.py and add it to the agent:

    from voice import transcribe_voice
    agent = Agent("小乐", tools=[transcribe_voice], ...)

The app sends the recording as it is (AAC in .m4a); the host saves it under .co/uploads/ and tells the agent
the path. Needs ffmpeg on the agent's machine.
"""
import subprocess
from pathlib import Path

from connectonion import transcribe


def loudest_db(path: str) -> float:
    """The loudest moment of a recording in dB; a silent one is around -90."""
    report = subprocess.run(["ffmpeg", "-hide_banner", "-i", path, "-af", "volumedetect", "-f", "null", "-"],
                            capture_output=True, text=True, check=True).stderr
    return float(report.split("max_volume:")[1].split("dB")[0])


def transcribe_voice(path: str) -> str:
    """Turn a parent's voice message (an audio file) into the words they said."""
    # Given silence, the transcription service invents a sentence; so say there was nothing to hear.
    if loudest_db(path) < -50:
        return "（这条语音没有声音，什么也没录到）"
    # The phone sends AAC; the transcription service takes only wav and mp3.
    mp3 = str(Path(path).with_suffix(".mp3"))
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", path, mp3], check=True)
    return transcribe(mp3, prompt="一位六十多岁的中国家长发给 AI 助手的语音，普通话，可能带口音")
