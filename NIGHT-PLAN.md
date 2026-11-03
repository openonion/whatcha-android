# Night plan, 2026-11-03 → 2026-11-04 08:00 (Sydney)

The ten biggest problems for the people this app is for: parents in their sixties, on Huawei phones,
who know WeChat and nothing else. Ranked by how badly each one hurts them. One fix per round, each
with a test that failed before the fix, and an emulator screenshot where the change is visible.

Evidence marked *seen* comes from the emulator run on 2026-11-03 21:48–21:55; *read* means read in the code.

| # | Problem | Status |
|---|---|---|
| 1 | A message sent while the link is down is lost | **done** |
| 2 | No sign that a message is sending or failed to send | open |
| 3 | Nothing shows that 小乐 is replying | **done** |
| 4 | No app icon: the home screen shows Android's default robot | **done** |
| 5 | An agent error is swallowed: the parent waits forever | **done** |
| 6 | First screen is empty: nothing says what to do | **done** |
| 7 | Large system font never tried | open |
| 8 | A reply that arrives after the app was killed is lost | open |
| 9 | Opening the keyboard hides the latest message | open |
| 10 | Read-aloud cannot be stopped | open |
| R1 | README said the agent answers in the child's voice | **done** |

## 1. A message sent while the link is down is lost

- **Evidence.** *Seen*: after the test host restarted, the app still looked online; the host logged
  `INPUT rejected: not authenticated (send CONNECT first)` and the parent's message got no answer (issue #1).
  *Read*: `ChatViewModel.send` marks the status OFFLINE when `link.send` returns false and drops the
  message; the ERROR frame goes to `onError`, which only logs.
- **Fix.** Keep unanswered messages in an outbox on disk. Send them after every CONNECTED. When the host
  answers ERROR "not authenticated", reconnect.
- **Verify.** Unit test on the outbox; on the emulator, restart the host mid-conversation and see the
  message answered without the parent doing anything.
- **Done 2026-11-03 22:00.** Messages now carry a delivery state (QUEUED → SENT → DONE) and are saved
  with it; QUEUED ones go out on every CONNECTED; the host's "authenticate first" ERROR sends CONNECT
  again and puts SENT messages back in the queue. Tests `OutboxTest` (4) and `AgentLinkTest` (2) failed
  to compile before the change, pass after. Emulator, real relay: (a) restarted the host between two
  messages — the app reconnected by itself and the second message was answered (host log
  `CONNECT … status=connected`, then `INPUT … prompt=after restart`); (b) stopped the host, sent
  「sent while down」, started the host — host log `INPUT … prompt=sent while down` 8 s after the app's
  `connected`, reply shown (`screenshots/night-01-sent-while-down.png`).
- **Seen on the way.** While the host is down the relay answers each reconnect with an ERROR whose
  message is empty, every 5 s. #5 must not turn those into lines in the chat.

## 2. No sign that a message is sending or failed

- **Evidence.** *Read*: a `Line` has no state; the green bubble looks the same whether it arrived or not.
  WeChat shows a spinner, then a red ! to tap and resend.
- **Fix.** A pending line shows a small grey spinner beside the bubble; after 60 s without a reply,
  a red ! that resends on tap.
- **Smaller since #1 and #5 (2026-11-04).** Unsent messages now go out by themselves on reconnect, the
  header says 「暂时联系不上」 while the link is down, and a failed run ends in a grey note. So no red !
  to tap is needed; what is left is a grey spinner beside a message still in the queue.
- **Verify.** Unit test on the state; screenshot with the host stopped.

## 3. Nothing shows that 小乐 is replying

- **Evidence.** *Seen*: a reply took about 10 s and the screen did nothing meanwhile. Parents resend or give up.
- **Fix.** Like WeChat's 「对方正在输入…」: while a message waits for its reply, the title reads 「小乐正在输入…」.
- **Verify.** Screenshot taken during the wait.
- **Done 2026-11-03 23:15.** Chosen this round over #2 and #4 because it happens on every single message.
  The title shows WeChat's exact words 「对方正在输入...」 while any message is SENT and unanswered, then the
  agent's name again. `HeaderTest` (2) failed to compile before, passes after. Emulator, real relay:
  `screenshots/night-03-typing.png` — left 1 s after sending, right after the reply.

## 4. No app icon

- **Evidence.** *Read*: the manifest has no `android:icon`. On the home screen the app is the default
  robot, and parents find apps by their picture.
- **Fix.** An adaptive icon: WeChat-green square, white speech bubble, the label 在干嘛.
- **Verify.** Screenshot of the launcher.
- **Done 2026-11-04 01:12.** Chosen over #6 because the icon is how a parent finds the app every time they
  open it; the empty screen is seen once. An adaptive icon drawn as a vector, so it is sharp at every size
  and survives any launcher's mask (EMUI's included): WeChat green `#07C160`, one white speech bubble with
  three dots. It reads as "chat" without copying WeChat's own two-bubble logo. The label stays 在干嘛.
  `IconTest` (2) failed before (no `android:icon`, no `mipmap-anydpi/ic_launcher.xml`), passes after.
  Emulator app drawer: `screenshots/night-04-icon.png`.

## 5. Agent errors are swallowed

- **Evidence.** *Read*: `onError` logs and nothing else. An agent out of credit or crashing leaves the
  parent waiting forever.
- **Fix.** Show a plain grey line in the chat: 「小乐这会儿出了点问题，已经告诉孩子了」. Never an error code.
- **Verify.** Unit test that an ERROR frame (other than the reconnect case) becomes that line.
- **Done 2026-11-04 00:17.** Chosen first this round because round 3's typing indicator made it worse: a
  failed run left 「对方正在输入...」 in the title for good. Now an ERROR ends the wait for the oldest message
  and adds a grey centred note, like WeChat's system lines: 「小乐这会儿出了点问题，没能回答。过一会儿再问问看。」
  The planned wording 「已经告诉孩子了」 was dropped: nothing tells the child yet, and the app must not
  promise what it does not do. The relay's empty ERRORs and errors with nothing waiting change nothing.
  `AgentErrorTest` (3) failed to compile before, passes after. Emulator, real relay, test agent pointed at
  a model that does not exist (host log `UnprocessableEntityError: Error code: 422`, app log
  `agent error: Unable to run agent`): the note appears and the title returns to 小乐; after restarting
  the agent on its normal model the next message is answered (`screenshots/night-05-agent-error.png`).

## 6. Empty first screen

- **Evidence.** *Seen*: after pairing, the chat is a blank grey page.
- **Fix.** While the chat is empty, a centred grey hint: 「想知道孩子在干嘛，就在下面打字问小乐」, with the agent's name.
- **Verify.** Screenshot on first launch.
- **Done 2026-11-04 02:11.** Chosen over #2 because every parent meets it, and alone with a blank page a
  parent may never type at all. Until the first message, the chat area shows, centred and grey,
  「想知道孩子在干嘛，就在下面打字问小乐」 with the agent's own name. No hint when the app is not paired,
  because then there is no box to type into. `EmptyHintTest` (3) failed to compile before, passes after.
  Emulator, fresh install, real relay: `screenshots/night-06-first-screen.png`.

## 7. Large system font never tried

- **Evidence.** Huawei phones set up for older people usually have the system font turned up. All sizes
  are in sp, so they scale, but nothing has been looked at at 1.3× or 2×.
- **Fix.** Whatever the screenshots show.
- **Verify.** Screenshots at font scale 1.3 and 2.0.

## 8. A reply that arrives after the app was killed is lost

- **Evidence.** *Read*: `AgentLink.sessionId` lives in memory only. Huawei kills background apps; the
  next launch opens a new session and the host's stored result for the old one is never collected.
- **Fix.** Keep the session id on disk and resume it on CONNECT.
- **Verify.** Unit test that CONNECT carries the saved id; on the emulator, send, kill the app at once,
  reopen, and see the reply.

## 9. Opening the keyboard hides the latest message

- **Evidence.** *Read*: the list scrolls only when a line is added. WeChat jumps to the bottom when the keyboard opens.
- **Fix.** Scroll to the last line when the input gets focus.
- **Verify.** Screenshot with the keyboard open on a long chat.

## 10. Read-aloud cannot be stopped

- **Evidence.** *Read*: every reply is spoken; tapping a bubble starts it again; nothing stops it.
- **Fix.** Tapping the bubble that is speaking stops it.
- **Verify.** Unit test on the toggle.

## R1. README said the agent answers in the child's voice

- **Evidence.** Both READMEs: "the agent answers in the child's voice" / 「它用子女的口吻回答」, and claimed the
  child can join the chat, which nothing implements. The opposite of 小乐's first rule.
- **Done 2026-11-03 22:25** (dc368bd). Both READMEs now describe 小乐, link its skill, show the WeChat-style
  screen, list the no-lost-message fix, and update the roadmap. Removed two screenshots (scrambled input;
  a made-up reply). Repo topics added. Draft PR #2 opened so CI runs on this branch: run 41626130558 green,
  including "A packed APK still verifies". Rendered README checked through the GitHub API: the image resolves.

## A1. Picture avatars instead of a letter (Aaron, 22:15)

- **Done 2026-11-03 22:30.** Six candidates generated with the Codex CLI's image tool (`design/avatars/`,
  contact sheet `candidates.jpg`): for 小乐 a sun, a green bird, a lantern, a robot; for the parent a
  teacup and a plant. No human faces, so nothing looks like the child. Defaults: the robot for 小乐 (it
  says "AI" at a glance) and the teacup for the parent. Emulator screenshot `screenshots/chat.png`.

## Ask Aaron in the morning

- Default avatars: robot for 小乐 and teacup for the parent, or another pair from `design/avatars/candidates.jpg`?

- Hold-to-talk is the biggest gap and is not on this list, because it needs a decision: record on the
  phone and have the agent transcribe (`co asr`), or not. Huawei phones have no Google speech service.
- Can the APK's pairing invite code be reused for a second phone (the other parent)? Today it can.
