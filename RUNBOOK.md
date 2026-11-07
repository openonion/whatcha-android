# Runbook: run 小乐 for one family

The ticket commands below need the Sprint 2 code on [wechat-look](https://github.com/openonion/whatcha-android/blob/wechat-look/RUNBOOK.md). The newest tag, [v0.1.9](https://github.com/openonion/whatcha-android/blob/v0.1.9/RUNBOOK.md), still uses the legacy packed pairing below; `main` is older and builds v0.1.0.

Everything here runs on the child's Mac, from a checkout of this repo. The agent is the `~/.co` identity of that Mac.

## Prerequisites

- `co` 1.9.3rc41 or newer (`co --version`; the `Invalid invite` log line the smoke test greps for is rc41), signed in: `co env get OPENONION_API_KEY` prints a value (the agent needs it before it reads any `.env`); `ffmpeg` on PATH; JDK 17 or newer and the Android SDK with API 36; `ls "$ANDROID_HOME"/build-tools` lists a version (a fresh clone has no `local.properties`, so the build and the version check below find the SDK through `ANDROID_HOME`; point it at the folder that holds `build-tools`). `co doctor` shows the Python `co` runs on; the Saturday check uses that one.
- The Mac must not sleep: System Settings → Energy (or Battery → Options) → "Prevent automatic sleeping when the display is off" on, and keep the lid open and the Mac on power.
- `co host --background` installs a LaunchAgent. LaunchAgents start at login, not at boot: after a reboot, log in once and 小乐 is back.

## Step 0: make sure this is the only agent on your address

```bash
ls ~/Library/LaunchAgents | grep ai.connectonion.host     # must list nothing from other projects
cat ~/.co/served_by.json                                   # if it exists, "project" must be this family-agent or absent
# If it names another project and `lsof -i :8000` is empty, `rm ~/.co/served_by.json`; `co host --background` below writes it again.
```

Another `co host` project on the same Mac without its own `.co/keys/` shares your address; two agents on one address answer each other's calls. Stop the other one (`co host --uninstall` in its directory) first.

## The commands

If you are a daughter, first change 你儿子 to 你女儿 in `skills/xiaole/SKILL.md` (three places: the opening line, the no-repeat example and the good-news example); the agent reads the skill once at start, so do it before `co host`.

```bash
cd family-agent
co host --background
curl -s localhost:8000/info                                # check "address" and "name": 小乐
python ticket.py 妈妈                                      # use the Python from `co doctor`
cd ..
./gradlew :app:assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk whatcha.apk
"$(ls -d "$ANDROID_HOME"/build-tools/* | tail -1)"/aapt2 dump badging whatcha.apk | grep -o "version[CN][a-z]*='[^']*'"
```

Send the ordinary `whatcha.apk` and the two-line ticket message to the parent in WeChat. Run the ticket command once per family member, using their family label. It prints only the message to copy. It reads the running host's address from `http://localhost:8000/info`, so start the host first. For another host port, use `python ticket.py 妈妈 --port 8123`. Tickets live in the gitignored `family-agent/.co/tickets.json`; each is valid for 24 hours and one phone. Create another ticket for a new phone or an app reinstall that lost its identity. No host restart is needed after creating a ticket.

After joining, the same record contains `label`, `address`, and `used_at`: this is where the child can see which family member joined. Keep this file private. Do not send its contents or paste tickets into logs.

The last build command checks the APK's version. It must print `versionName='0.1.9'` and `versionCode='10'`; `0.1.0` means an old checkout.

### Legacy path: packed pairing for older APKs

`tools/pack.py` still works for the old shared invite-code path. Existing contacts reconnect as before; keep their `CO_INVITE_CODE` if an older installed phone still needs to onboard. The code belongs in `~/.co/keys.env`, not only the project's `.env`.

```bash
co env get CO_INVITE_CODE || co env set CO_INVITE_CODE $(openssl rand -hex 6)
python3 tools/pack.py app/build/outputs/apk/debug/app-debug.apk whatcha-family.apk \
  --agent <address from /info> --invite $(co env get CO_INVITE_CODE)
```

`co host --status` prints `{"configured": true, "running": true, "pid": N, ...}`. If `co host --background` ends with "a matching healthy Host was not observed" and `--status` says `"running": false`, launchd registered the service but has not spawned it yet (seen on macOS 14.1 from a shell started by another app): `launchctl kickstart gui/$(id -u)/ai.connectonion.host.<digest>` starts it now, with `<digest>` from `ls ~/Library/LaunchAgents`. Logging out and in starts it too. The host's own log is `family-agent/.co/logs/`; launchd's stdout/stderr go to `~/.co/host-services/<digest>/`.
The notebook 小乐 reads is `~/.co/rem`; to point it elsewhere write `REM_ROOT=/path` into `family-agent/.env` and run `co host --background` again.

## Send the APK to the phone

Parents far away: send `whatcha.apk` and the ticket message into your WeChat chat with them, and they tap it there. Phone in your hand: send it to yourself (文件传输助手) and open it on that phone, or with the phone on USB: `adb install whatcha.apk`. The parents' steps, and what to say on the call, are in [INSTALL.zh-CN.md](INSTALL.zh-CN.md).

## Smoke test

1. `curl -s localhost:8000/info` answers with your address and `"name": "小乐"`.
2. On an unpaired phone, long-press the ticket message in WeChat → Copy → open 在干嘛. It should connect without a tap; if automatic clipboard access is blocked, tap 粘贴口令. Then type 「在干嘛」, and the reply comes from 小乐, opening 「叔叔阿姨好，我是小乐，你儿子请的 AI。」 once and then something from the notebook; a reply that introduces 小乐 again on the next message is the bug to report. The host log shows one `INPUT` line per message.
3. The title drops 「(连接中…)」 within seconds. Check `.co/tickets.json` for the family label, joined address and `used_at`. If the strip says 「口令过期或用过了，\n请孩子重新发一条」, issue a fresh ticket and send it in WeChat. Before the first accepted connection, the parent can copy it and return, or tap 粘贴口令 under the strip, to replace the refused pairing immediately. This also works for a refused legacy packed pairing. The first `CONNECTED` makes pairing permanent; later clipboard text cannot replace it. Existing saved pairings without an acceptance flag are kept and gain the flag on their next connection. For a legacy packed APK, also check that its invite matches `CO_INVITE_CODE`.

## A new version, and the first week

- A new tag changes 小乐 too: after `git checkout` of it, run `cd family-agent && co host --background` again before issuing tickets, or the host keeps the old skill.
- A new version goes into the same WeChat chat and installs over the old one. Don't uninstall first: that deletes the chat and the mic answer.
- Build it on this same Mac: the APK is signed with this Mac's debug key (`tools/pack.py` does not re-sign). Another machine's build gets 签名不一致 and only goes on after an uninstall.
- One `INPUT` line per parent message in `family-agent/.co/logs/` is how you know they used it. Don't paste those lines anywhere.
- 小乐's 「我跟他说一声」 shows up as a notification on this Mac; that is the parents asking for you.

## Stop, uninstall

```bash
cd family-agent
co host --stop         # stops and disables the service; `lsof -i :8000` is then empty
co host --uninstall    # also removes ~/Library/LaunchAgents/ai.connectonion.host.<digest>.plist
```

Both keep `.co/` (identity, conversations, logs).

## Saturday checklist

### On this Mac

1. Real-notebook spot check, three lines:
   - `cd skills/xiaole/benchmark && REM_ROOT=~/.co/rem "$(dirname "$(readlink -f "$(which co)")")/python" -c 'from agent import agent; print(agent.input("那个老板叫什么")); print(agent.input("他在干嘛"))'` — the Python `co` itself runs on (`co doctor` prints it as 「Python Path」), your own `~/.co` key and your real notebook.
   - Each reply is the one plain Chinese line after the `.co/evals/….yaml` path; the `[co]` lines above it are the trace. A pass reads 「…他在给一家店做软件。老板叫什么，让他自己告诉你们吧…」 then 「他今天……」 with 老板 / 一家店 / 客户 where a name would be. A client, company or colleague name in either, Chinese or English or shortened (姓文的, 林总), means the APK waits (a `show_page(page="people/…")` line in the trace is the early sign): `co rem search <那个名字>` says which page it came from; open a round against that page, do not pack. Your own name, your company, a city are allowed and expected.
   - The replies and `skills/xiaole/benchmark/.co/` (gitignored, round 19) stay on this Mac; never paste a reply anywhere.
2. Reboot the Mac once, log in, and confirm `co host --status` says running.

### On tonight's call (remote)

The parent reads these aloud during the call script; write them down with the 「电话里顺手记下」 list after step 8 of INSTALL.zh-CN.md. A later round puts his words in place of the 待核对 marks.

3. Confirm the Huawei dialog wording in `INSTALL.zh-CN.md` as the parent reads it (纯净模式, 允许安装应用, 风险应用), three buttons at the bottom or none (27c), and the HarmonyOS version (step 0). Open the app once and watch the title: 「小乐(连接中…)」, then 「小乐」, nothing red (if the strip asks for a fresh ticket instead, check the ticket use/expiry in `.co/tickets.json` and follow the smoke-test recovery step; for a legacy packed APK, check `CO_INVITE_CODE`). Whether 小乐's reply made a sound during the call (27i, step 6).

### Next time the phone is in your hand

4. Set the phone's 字体 to its largest (设置 → 显示 → 字体大小与粗细) and look at: the composer in both modes (按住 说话 on one line, 发送 whole), the status strip (turn Wi-Fi off: 「暂时联系不上，」 then 「稍后会自己重试」 on their own lines), the recording card while holding the bar, and the spinner beside a message while it is on its way. The failed message (red ! a 感叹号, not a bar; its note three whole lines) is proven on the emulator only, `screenshots/sending-failed-2.0.png`: there is no safe way to make one on the real agent, so do not try. The emulator at `font_scale 2.0` passed all of these (`screenshots/*-2.0.png`); the Huawei at 超大 is the real check. Send one photo from the phone's album through the ⊕ (a Huawei has no Play services, so this is the system picker, never seen on the emulator): the picture bubble, the spinner, the reply about the picture, then tap the picture and see it full screen on black, tap again to come back. Then tap one reply and hear it read aloud; if it is silent, the phone has no Chinese voice: 设置 → 辅助功能 → 文本转语音, pick 华为语音引擎 and download 中文.
5. On the Huawei, tap the round button and press Back on the system question: the app stays on the chat and the next tap asks again. Deny once (禁止): the screen should say 「要允许录音，才能按住说话」 and the next tap asks again. Deny again: a large dialog says 权限 → 麦克风 → 允许 with 「取消」 and 「去设置」, and the phone does not leave the chat by itself. 取消 keeps the chat; 去设置 opens the app's settings page. Check that the page has 权限 → 麦克风 → 允许 (write the real words into `INSTALL.zh-CN.md`), switch it on, then Back until the chat shows (three on AOSP; write the Huawei count down): the bar is already 按住 说话 without a tap. At the largest 显示大小 and 字体, check that 去设置 is still on screen (26f). 「第二次 禁止 后应出现大字框；若只有一行小字，记下来」 (a ROM that never reports a No would only ever show the line).
   During a WeChat call, hold 按住 说话 for 2 s and release. Write down which of three happened: the large dialog 「正在打电话，不能录音。挂了电话再按住说话。」 (v0.1.4 saw the call), a voice bubble that 小乐 says had no sound (the call was missed: WeChat neither set the call mode nor recorded as a call), or the app closing.
6. Looks to take on the real phone, from SHIP-PLAN: the reopen snapshot in slow motion (30e), the toggle and the kept draft (32a, 32b).

## What is never committed

`family-agent/.env`, `family-agent/.co/*` except `host.yaml` (keys, contacts, `session_results.jsonl`, uploads, logs), `*.apk`, the agent's address, and the parents' or the child's real names. `.gitignore` already covers the files; the address and names are on you.
