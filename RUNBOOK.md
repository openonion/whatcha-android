# Runbook: run 小乐 for one family

Everything here runs on the child's Mac, from a checkout of this repo. The agent is the `~/.co` identity of that Mac.

## Prerequisites

- `co` 1.9.3rc40 or newer (`co --version`), signed in: `co env get OPENONION_API_KEY` prints a value (the agent needs it before it reads any `.env`); `ffmpeg` on PATH; JDK 17 or newer and the Android SDK with API 36.
- The Mac must not sleep: System Settings → Energy (or Battery → Options) → "Prevent automatic sleeping when the display is off" on, and keep the lid open or the Mac on power.
- `co host --background` installs a LaunchAgent. LaunchAgents start at login, not at boot: after a reboot, log in once and 小乐 is back.

## Step 0: make sure this is the only agent on your address

```bash
ls ~/Library/LaunchAgents | grep ai.connectonion.host     # must list nothing from other projects
cat ~/.co/served_by.json                                   # if it exists, "project" must be this family-agent or absent
```

Another `co host` project on the same Mac without its own `.co/keys/` shares your address; two agents on one address answer each other's calls. Stop the other one (`co host --uninstall` in its directory) first.

## The six commands

```bash
cd family-agent
co env get CO_INVITE_CODE || co env set CO_INVITE_CODE $(openssl rand -hex 6)
co host --background
curl -s localhost:8000/info                                # read "address" and check "name" is 小乐
cd .. && ./gradlew :app:assembleDebug
python3 tools/pack.py app/build/outputs/apk/debug/app-debug.apk whatcha-family.apk \
  --agent <address from /info> --invite $(co env get CO_INVITE_CODE)
```

The invite code lives in `~/.co/keys.env`, not in `family-agent/.env`: `co host` 1.9.3rc40 hands the agent the code from `keys.env` before it reads the project's `.env`, so a code written only in `.env` is refused while `keys.env` has one. `co init` already wrote one there; the second half of that line only runs when it did not.

`co host --status` prints `{"configured": true, "running": true, "pid": N, ...}`. If `co host --background` ends with "a matching healthy Host was not observed" and `--status` says `"running": false`, launchd registered the service but has not spawned it yet (seen on macOS 14.1 from a shell started by another app): `launchctl kickstart gui/$(id -u)/ai.connectonion.host.<digest>` starts it now, with `<digest>` from `ls ~/Library/LaunchAgents`. Logging out and in starts it too. The host's own log is `family-agent/.co/logs/`; launchd's stdout/stderr go to `~/.co/host-services/<digest>/`.
The notebook 小乐 reads is `~/.co/rem`; to point it elsewhere write `REM_ROOT=/path` into `family-agent/.env` and run `co host --background` again.

## Send the APK to the phone

Send `whatcha-family.apk` to yourself in WeChat and open it on the phone from 文件传输助手, or with the phone on USB: `adb install whatcha-family.apk`. The parents' steps are in [INSTALL.zh-CN.md](INSTALL.zh-CN.md).

## Smoke test

1. `curl -s localhost:8000/info` answers with your address and `"name": "小乐"`.
2. On the phone, open 在干嘛, type 「在干嘛」, and the reply comes from 小乐 with something from the notebook. The host log shows one `INPUT` line per message.
3. The title drops 「(连接中…)」 within seconds. If the strip says 还没连上 with the Mac up, `grep 'Invalid invite' family-agent/.co/logs/*`: the APK's code is not the one in `~/.co/keys.env`. Re-pack, or `co env set CO_INVITE_CODE` to the packed one and `co host --background`; the phone reconnects by itself.

## Stop, uninstall

```bash
cd family-agent
co host --stop         # stops and disables the service; `lsof -i :8000` is then empty
co host --uninstall    # also removes ~/Library/LaunchAgents/ai.connectonion.host.<digest>.plist
```

Both keep `.co/` (identity, conversations, logs).

## Saturday checklist

1. Real-notebook spot check: run the agent once with the test key against the real `~/.co/rem`, read the reply, keep it local; never paste it anywhere.
2. Confirm the Huawei dialog wording in `INSTALL.zh-CN.md` on the phone. Open the app once and watch the title: 「小乐(连接中…)」, then 「小乐」, nothing red (if the strip says 还没连上 instead, the packed code is not the one in `~/.co/keys.env`: re-pack, or `co env set CO_INVITE_CODE` to the packed one and `co host --background`). Then set the phone's 字体 to its largest (设置 → 显示 → 字体大小与粗细) and look at: the composer in both modes (按住 说话 on one line, 发送 whole), the status strip (turn Wi-Fi off: 「暂时联系不上，」 then 「稍后会自己重试」 on their own lines), the recording card while holding the bar, and one failed message (its red ! must be a 感叹号, not a bar). The emulator at `font_scale 2.0` passed all of these (`screenshots/*-2.0.png`); the Huawei at 超大 is the real check. Then tap one reply and hear it read aloud; if it is silent, the phone has no Chinese voice: 设置 → 辅助功能 → 文本转语音, pick 华为语音引擎 and download 中文.
3. Tap the round button once and 允许 the microphone during setup, so the parents never see the dialog.
4. Reboot the Mac once, log in, and confirm `co host --status` says running.

## What is never committed

`family-agent/.env`, `family-agent/.co/*` except `host.yaml` (keys, contacts, `session_results.jsonl`, uploads, logs), `*.apk`, the agent's address, and the parents' or the child's real names. `.gitignore` already covers the files; the address and names are on you.
