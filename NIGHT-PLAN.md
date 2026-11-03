# Night plan, 2026-11-03 → 2026-11-04 08:00 (Sydney)

The ten biggest problems for the people this app is for: parents in their sixties, on Huawei phones,
who know WeChat and nothing else. Ranked by how badly each one hurts them. One fix per round, each
with a test that failed before the fix, and an emulator screenshot where the change is visible.

Evidence marked *seen* comes from the emulator run on 2026-11-03 21:48–21:55; *read* means read in the code.

| # | Problem | Status |
|---|---|---|
| 1 | A message sent while the link is down is lost | open |
| 2 | No sign that a message is sending or failed to send | open |
| 3 | Nothing shows that 小乐 is replying | open |
| 4 | No app icon: the home screen shows Android's default robot | open |
| 5 | An agent error is swallowed: the parent waits forever | open |
| 6 | First screen is empty: nothing says what to do | open |
| 7 | Large system font never tried | open |
| 8 | A reply that arrives after the app was killed is lost | open |
| 9 | Opening the keyboard hides the latest message | open |
| 10 | Read-aloud cannot be stopped | open |

## 1. A message sent while the link is down is lost

- **Evidence.** *Seen*: after the test host restarted, the app still looked online; the host logged
  `INPUT rejected: not authenticated (send CONNECT first)` and the parent's message got no answer (issue #1).
  *Read*: `ChatViewModel.send` marks the status OFFLINE when `link.send` returns false and drops the
  message; the ERROR frame goes to `onError`, which only logs.
- **Fix.** Keep unanswered messages in an outbox on disk. Send them after every CONNECTED. When the host
  answers ERROR "not authenticated", reconnect.
- **Verify.** Unit test on the outbox; on the emulator, restart the host mid-conversation and see the
  message answered without the parent doing anything.

## 2. No sign that a message is sending or failed

- **Evidence.** *Read*: a `Line` has no state; the green bubble looks the same whether it arrived or not.
  WeChat shows a spinner, then a red ! to tap and resend.
- **Fix.** A pending line shows a small grey spinner beside the bubble; after 60 s without a reply,
  a red ! that resends on tap.
- **Verify.** Unit test on the state; screenshot with the host stopped.

## 3. Nothing shows that 小乐 is replying

- **Evidence.** *Seen*: a reply took about 10 s and the screen did nothing meanwhile. Parents resend or give up.
- **Fix.** Like WeChat's 「对方正在输入…」: while a message waits for its reply, the title reads 「小乐正在输入…」.
- **Verify.** Screenshot taken during the wait.

## 4. No app icon

- **Evidence.** *Read*: the manifest has no `android:icon`. On the home screen the app is the default
  robot, and parents find apps by their picture.
- **Fix.** An adaptive icon: WeChat-green square, white speech bubble, the label 在干嘛.
- **Verify.** Screenshot of the launcher.

## 5. Agent errors are swallowed

- **Evidence.** *Read*: `onError` logs and nothing else. An agent out of credit or crashing leaves the
  parent waiting forever.
- **Fix.** Show a plain grey line in the chat: 「小乐这会儿出了点问题，已经告诉孩子了」. Never an error code.
- **Verify.** Unit test that an ERROR frame (other than the reconnect case) becomes that line.

## 6. Empty first screen

- **Evidence.** *Seen*: after pairing, the chat is a blank grey page.
- **Fix.** While the chat is empty, a centred grey hint: 「想知道孩子在干嘛，就在下面打字问小乐」, with the agent's name.
- **Verify.** Screenshot on first launch.

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

## Ask Aaron in the morning

- Hold-to-talk is the biggest gap and is not on this list, because it needs a decision: record on the
  phone and have the agent transcribe (`co asr`), or not. Huawei phones have no Google speech service.
- Can the APK's pairing invite code be reused for a second phone (the other parent)? Today it can.
