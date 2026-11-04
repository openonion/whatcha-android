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
| 6 | Designer pass of every screen at font scale 1.0 and max | G6 | done round 3 (findings 6a–6j below; `screenshots/design-*-1.0.png` and `-1.3.png`, 1.3 is this emulator's largest; paired chat judged from `chat.png`, `memory-*.png`, `voice-*.png`) |
| 6a | A message written or spoken while the link is down looks exactly like a sent one: `design-chat-1.3.png` shows two queued voice bubbles under 「暂时联系不上」 with no mark at all, so a parent believes 小乐 got it. WeChat puts a 20dp grey spinning circle on the outer side of the bubble while sending and a red circled ! (#FA5151) that is tapped to resend when it failed. Fix: in `Bubble`, a 20dp spinner before the bubble while `line.delivery == QUEUED`/`SENT`, the red mark on `FAILED` (this is the design spec for #5) | G6 G3 | open |
| 6b | Before the agent's name arrives (first launch offline, or the host down) the title is the app name 在干嘛 and the hint reads 「就在下面问在干嘛」 (`design-empty-1.0.png`): `ChatViewModel._title` defaults to "在干嘛" and `emptyHint` interpolates it. WeChat's title is always the contact's name. Fix: default `_title` to "小乐" in ChatViewModel; hint then reads 问小乐 | G6 | open |
| 6c | Small grey text (`Small`, #888888 on #EDEDED) measured 3.0:1, under the MIIT/WCAG 4.5:1 floor for 18sp: that is the time lines, the status line and the one grey note that tells a parent something failed. Fixed: `Small` colour → #666666 (4.9:1), `ChatScreen.kt` line 61, re-shot in `design-recording-1.0.png`, `design-chat-1.3.png` | G6 | done round 3 |
| 6d | The recording card was translucent (0xCC000000) so the hint and bubbles showed through it (`voice-recording.png`); WeChat's card is opaque dark grey. Fixed: `RecordingCard` background → #2F2F2F (same shade the alpha produced over #EDEDED), `ChatScreen.kt` line 94, re-shot in `design-recording-1.0.png`, `design-recording-1.3.png` | G6 | done round 3 |
| 6e | The status sentence sits in `Header` with no horizontal padding: at 1.3 「还没有连上，请让孩子重新发一个安装包」 runs edge to edge (`design-unpaired-1.3.png`) and at Huawei's larger scales will wrap mid-sentence. WeChat shows link trouble as a strip under the title, pale red with a red circled ! and dark text, not as a subtitle. Fix now: `Header` Column `.padding(horizontal = 24.dp, vertical = 14.dp)`; fix properly: a `StatusStrip` composable under the divider, 24sp, min 60dp tall | G6 | open |
| 6f | `RecordingCard` is a fixed `size(200.dp)`: 「松开 发送」 at 1.3 (31sp) fits with about 20dp to spare (`design-recording-1.3.png`); at Huawei 特大 (≈1.75, 42sp) text and the 72dp icon overflow the card. Fix: `widthIn(min = 200.dp).heightIn(min = 200.dp).padding(24.dp)` instead of `size` | G6 | open |
| 6g | Empty hint uses `Color.Gray` (#888888) on #EDEDED, 3.0:1: passes only because it is 24sp large text, and is the first sentence a parent reads. Fix: `EmptyHint` `Body.copy(color = Color(0xFF666666))` | G6 | open |
| 6h | Mic permission is the system dialog, shown on the first tap of the round button (`design-mic-permission-1.0.png`; English on this emulator, Chinese on the Huawei). If the parent taps 拒绝 the button silently stays on the keyboard, and after a second 拒绝 Android stops asking. WeChat has the same dialog. Fix: the install guide (#4) tells the child to tap the round button once and 允许 while setting up | G6 G5 | open |
| 6i | Voice bubble keeps the duration 「3"」 inside the bubble; WeChat prints it outside in grey next to the bubble, and only the wave inside. Parents still read it, so low. Fix: move `voiceLabel` out of `VoiceContent` into `Bubble` as a `Small` text beside the bubble | G6 | open |
| 6j | Checked and fine at 1.0 and 1.3: body 24sp → 31sp without clipping, round button 60dp, 按住 说话 bar ≥60dp, 发送 88×60dp, avatars 60dp, bubbles max 270dp, header/composer in WeChat's places and colours (#EDEDED page, #F7F7F7 bar, #95EC69/#FFFFFF bubbles, #07C160 发送), latest line stays in view with the keyboard open (`design-keyboard-1.3.png`). 发送 white on #07C160 is 2.9:1, same as WeChat, accepted for 24sp text | G6 | — |
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
