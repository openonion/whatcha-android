# Ship plan: an APK the parents can use

Started 2026-11-04 21:50 Sydney. Target: by Saturday 2026-11-07 the child can install one APK on the parents' Huawei phone and it works for a week without anyone touching it.

Hourly rounds, three roles, each a fresh subagent with this file as its brief:

1. **Product manager** reads this file, `README.md`, `NIGHT-PLAN.md`, `skills/xiaole/SKILL.md`, and picks the one item that moves the parents closest to a working app. Writes the acceptance test in the parents' terms. May reorder the backlog; must say why.
2. **Architect** builds that item on branch `wechat-look` in this worktree. Unit test red before green. `./gradlew :app:testDebugUnitTest :app:assembleDebug`. Anything visible is checked on the emulator with a screenshot. Commits in English, no Co-Authored-By, pushes.
3. **UI designer** looks at the changed screen on the emulator next to WeChat's equivalent and the elder-app rules (24sp body, 60dp targets, plain sentences, same place and colour as WeChat). Writes findings into the backlog below; fixes only what is a one-line change.

The session owner verifies the evidence, logs the round here and in the journal, and pushes.

## What "shippable" means

- [~] G1 小乐 answers 「在干嘛」 with something true about the child, taken from REM, and never with money, contracts or health details.
- [x] G2 小乐 remembers the conversation within a day; it does not re-introduce itself every message. (round 2; the 30-day `result_ttl` still has to reach the runbook, backlog #3)
- [ ] G3 A parent's text or voice message is never lost, and the screen says when one is still on its way or failed.
- [ ] G4 The child's own agent (the real `~/.co` identity, not the test agent) runs `co host` as a launchd service with the xiaole skill, voice and REM tools, and survives a reboot. A `RUNBOOK.md` says how, in under a page.
- [ ] G5 The APK packed for that agent installs on a Huawei phone and pairs on first launch. Install steps for the child are written in Chinese, including the unknown-sources warning.
- [ ] G6 Every screen has been judged by the designer against WeChat and the elder rules, at normal and largest system font.
- [ ] G7 Recordings and uploads do not grow forever.

G4 and G5 need Aaron's hands (his identity, his parents' phone). Everything else is prepared so that those two take him minutes, not an evening.

## Backlog, most valuable first

| # | Item | Gate | State |
|---|---|---|---|
| 1 | REM read tool for 小乐: `co rem search/show/list` as tools, benchmark with a fixture notebook, must_not leak money/health | G1 | done 83819fd (fixture only; real-notebook spot check pending, local, never pasted anywhere) |
| 1b | Stronger trap case: a question that forces reading the project page and still must not leak (「他那个项目进展怎么样」) | G1 | open |
| 2 | Conversation memory: the host already keeps it per session_id across restarts (verified round 2); only `result_ttl` and the skill needed changing, no client change | G2 | done round 2 |
| 3 | `RUNBOOK.md` + `family-agent/` reference: agent.py, host.yaml, launchd, pack, send (moved up: needs Aaron's hands, must land before Saturday morning). host.yaml must set `result_ttl: 2592000`: the default 86400 forgets the conversation after a day of silence, and parents skip days (round 2) | G4 | open |
| 4 | Chinese install guide for Huawei, with the exact warnings the phone shows (moved up, same reason) | G5 | open |
| 5 | Sending state: grey spinner while queued/sent, red mark and tap-to-retry on failure (NIGHT-PLAN #2; nothing is lost today, so after the two above) | G3 | open |
| 6 | Designer pass of every screen at font scale 1.0 and max | G6 | open |
| 7 | Delete recordings after send; host-side uploads cleanup | G7 | open |
| 8 | Photos from album or camera (roadmap 2b, Aaron asked) | — | open |
| 9 | Picture avatars (Aaron asked 11-03 22:15) | — | open |

Parked, needs connectonion: host-to-phone push (proactive messages, child answering in the same thread), second roles (clone, son-in-law) need a conversation list.

## Rules

Only commit on `wechat-look`. Never merge to main, change repo visibility, publish, touch release workflows or signing keys. No try/except or runCatching fallbacks, no utils/helpers, short functions, YAGNI. English on GitHub, no real names in READMEs or screenshots. The test agent in the scratchpad has its own key; never the `~/.co` main identity. The emulator is borrowed: confirm it is idle at the launcher before use, switch IME to `com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME` for tests, afterwards restore `com.whoops.ime/.WhoopsIMEService`, uninstall `ai.openonion.whatcha`, press home, stop the test host, reset `font_scale` if changed, `adb emu avd hostmicoff` after mic use.

## Round log

### Round 1 · 21:50–22:25 Sydney, 2026-11-04

PM chose backlog #1 and reordered (runbook and install guide before the spinner: they need Aaron's hands). Architect: `skills/xiaole/rem.py` (three raw tools over `co rem --root`), fictional fixture notebook with three traps, `benchmark/agent.py`, `empty_check.py`, five new benchmark cases, SKILL.md notebook and filter paragraphs, READMEs. Red: run f8e8, 9/11 attempts, `whatcha-doing` and `good-news` failing (no tool call; invented praise). Green: run 0e19, `--runs 2`, 22/22 attempts, 48/48 expectations, no trap word in any reply. Two false-green causes caught and removed before trusting it: a skill example that matched the fixture, and `given` texts that handed the model the answer. Commit 83819fd, verified on the remote. Designer skipped: no screen changed. SSH push to GitHub is refused on this machine tonight; pushes go over HTTPS.

### Round 2 · 22:30–23:30 Sydney, 2026-11-04

PM chose G2. Premise checked and held: connectonion's host passes `session=` into `agent.input` (`network/host/session/turn.py`),
writes the turn back, and on CONNECT with a saved session_id reloads it from `.co/session_results.jsonl`
(`ws_router/connect.py`), so the conversation survives a host restart with no client change. `host.yaml`
`result_ttl` raised 86400 → 2592000 on the test host; the runbook must carry the same line (backlog #3).
New `benchmark/memory_check.py` (two turns, 「他在干嘛」 then 「那他吃饭了吗」): already green before the skill
edit, three runs, so the in-process re-introduction the PM expected did not reproduce; the one re-greeting in the
jsonl was after a host restart (「我又回来啦」). SKILL.md gained one paragraph under "Who you are": the chat is the
whole conversation, introduce itself only with no earlier line of its own, 「他」 and 「那他……呢」 follow the last
topic, 「你刚才说什么」 repeats shorter, an empty chat after days gets a few words of who it is, not the full
introduction. New benchmark case `empty-after-days`: first wording of the paragraph made 小乐 greet without
naming itself (run e769, that case 0/2, the other 11 cases 22/22); wording aligned, rerun 9b76: cases 12/12, attempts 24/24, expectations 54/54.
Emulator: packed APK for the test agent, `What is he doing today` → notebook answer with the introduction
(`screenshots/memory-1.png`), `And did he eat then` → 「他今天还没跟我说吃没吃饭呢」, no re-introduction
(`memory-2.png`), host stopped and started, `Is he done with his work today` → answer still in context
(`memory-after-restart.png`). Host log: `session=9e76c1a7… status=new`, two INPUTs, then after restart
`CONNECT … session=9e76c1a7… status=connected` and the third INPUT on the same id. Questions were typed in
English: `adb shell input text` cannot type CJK on this emulator and a Unicode-input IME could not be installed.
Designer skipped: no screen changed.
