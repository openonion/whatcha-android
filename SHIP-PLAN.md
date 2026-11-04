# Ship plan: an APK the parents can use

Started 2026-11-04 21:50 Sydney. Target: by Saturday 2026-11-07 the child can install one APK on the parents' Huawei phone and it works for a week without anyone touching it.

Hourly rounds, three roles, each a fresh subagent with this file as its brief:

1. **Product manager** reads this file, `README.md`, `NIGHT-PLAN.md`, `skills/xiaole/SKILL.md`, and picks the one item that moves the parents closest to a working app. Writes the acceptance test in the parents' terms. May reorder the backlog; must say why.
2. **Architect** builds that item on branch `wechat-look` in this worktree. Unit test red before green. `./gradlew :app:testDebugUnitTest :app:assembleDebug`. Anything visible is checked on the emulator with a screenshot. Commits in English, no Co-Authored-By, pushes.
3. **UI designer** looks at the changed screen on the emulator next to WeChat's equivalent and the elder-app rules (24sp body, 60dp targets, plain sentences, same place and colour as WeChat). Writes findings into the backlog below; fixes only what is a one-line change.

The session owner verifies the evidence, logs the round here and in the journal, and pushes.

## What "shippable" means

- [ ] G1 小乐 answers 「在干嘛」 with something true about the child, taken from REM, and never with money, contracts or health details.
- [ ] G2 小乐 remembers the conversation within a day; it does not re-introduce itself every message.
- [ ] G3 A parent's text or voice message is never lost, and the screen says when one is still on its way or failed.
- [ ] G4 The child's own agent (the real `~/.co` identity, not the test agent) runs `co host` as a launchd service with the xiaole skill, voice and REM tools, and survives a reboot. A `RUNBOOK.md` says how, in under a page.
- [ ] G5 The APK packed for that agent installs on a Huawei phone and pairs on first launch. Install steps for the child are written in Chinese, including the unknown-sources warning.
- [ ] G6 Every screen has been judged by the designer against WeChat and the elder rules, at normal and largest system font.
- [ ] G7 Recordings and uploads do not grow forever.

G4 and G5 need Aaron's hands (his identity, his parents' phone). Everything else is prepared so that those two take him minutes, not an evening.

## Backlog, most valuable first

| # | Item | Gate | State |
|---|---|---|---|
| 1 | REM read tool for 小乐: `co rem search/show/list` as tools, benchmark with a fixture notebook, must_not leak money/health | G1 | open |
| 2 | Conversation memory: decide client-side (recent lines sent with the prompt) vs host-side; ship the one that works this week | G2 | open |
| 3 | Sending state: grey spinner while queued/sent, red mark and tap-to-retry on failure (NIGHT-PLAN #2) | G3 | open |
| 4 | `RUNBOOK.md` + `family-agent/` reference: agent.py, host.yaml, launchd, pack, send | G4 | open |
| 5 | Chinese install guide for Huawei, with the exact warnings the phone shows | G5 | open |
| 6 | Designer pass of every screen at font scale 1.0 and max | G6 | open |
| 7 | Delete recordings after send; host-side uploads cleanup | G7 | open |
| 8 | Photos from album or camera (roadmap 2b, Aaron asked) | — | open |
| 9 | Picture avatars (Aaron asked 11-03 22:15) | — | open |

Parked, needs connectonion: host-to-phone push (proactive messages, child answering in the same thread), second roles (clone, son-in-law) need a conversation list.

## Rules

Only commit on `wechat-look`. Never merge to main, change repo visibility, publish, touch release workflows or signing keys. No try/except or runCatching fallbacks, no utils/helpers, short functions, YAGNI. English on GitHub, no real names in READMEs or screenshots. The test agent in the scratchpad has its own key; never the `~/.co` main identity. The emulator is borrowed: confirm it is idle at the launcher before use, switch IME to `com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME` for tests, afterwards restore `com.whoops.ime/.WhoopsIMEService`, uninstall `ai.openonion.whatcha`, press home, stop the test host, reset `font_scale` if changed, `adb emu avd hostmicoff` after mic use.

## Round log

