# Whatcha · 在干嘛

[中文](README.zh-CN.md)

**Whatcha is a one-screen Android app for parents. They use it to ask their child's AI agent what the child is up to.**

Parents worry, and their child is often too busy to answer. The child's agent knows what the child has been doing. In Whatcha it is called 小乐 (Xiaole). 小乐 says plainly that it is an AI, never pretends to be the child, and only tells the parents things that really happened.

The app looks and works like a WeChat chat, because WeChat is the one app many parents already know. The parent configures nothing and never sees an account, an address or a setting. All setup is done on the agent's side.

<img src="screenshots/chat.png" alt="A WeChat-style chat: the parent's green bubble on the right with a teacup avatar, 小乐's white bubble on the left with a smiling robot avatar, large text" width="300">

## Status

This is early work. The first milestone works end to end. It was last checked on 2026-11-04 on an Android emulator against a ConnectOnion 1.9.3rc35 agent through the public relay.

What works today:

- **Pairing is packed into the APK.** The parent installs one file, opens it, and the app is already connected. Nothing is typed or pasted.
- **Silent identity.** The phone makes its own Ed25519 key on first launch.
- **Invite onboarding.** The app presents the invite code packed with it, and the agent accepts the phone as a contact.
- **A WeChat-style chat.** Green and white bubbles with tails, avatars on both sides, grey time lines, and a green 发送 that appears once there is text. The agent's name, set in its `host.yaml`, is the title. While the agent is writing, the title reads 对方正在输入..., as in WeChat.
- **An icon parents can find.** A white chat bubble on WeChat green.
- **An empty chat says what to do:** 「想知道孩子在干嘛，就在下面打字问小乐」.
- **No message is lost.** A message written while the link is down waits and goes out when the agent is back, including after the agent restarts. If the phone kills the app while a reply is on its way, the app picks the conversation up again on the next launch and the reply still arrives.
- **Failures are said plainly.** If the agent fails, a grey line says so instead of leaving the parent waiting.
- **The latest line stays in view**, at any font size and with the keyboard open.
- **Replies are read aloud.** Tapping a reply reads it again.
- **History is kept** on the phone.

## How it works

```
child's agent ── tools/pack.py ──> whatcha-family.apk ──> parent's phone
      ^                                                         │
      └──────────── wss://oo.openonion.ai (relay) ─────────────┘
```

1. The child runs an agent that accepts an invite code.
2. `tools/pack.py` writes the agent's address and the invite code into a copy of the APK. The entry goes in the APK Signing Block, which the signature does not cover, so no re-signing and no signing key are needed.
3. On first launch the app reads that entry from its own installed APK, connects through the relay, and onboards with the code.

## Try it

Build the app and run the tests. You need JDK 17 and the Android SDK with API 36.

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Start an agent that accepts an invite code:

```bash
CO_INVITE_CODE=pick-a-code co host
```

Pack a copy for one family and install it:

```bash
python3 tools/pack.py app/build/outputs/apk/debug/app-debug.apk whatcha-family.apk \
  --agent 0xYOUR_AGENT_ADDRESS --invite pick-a-code
adb install whatcha-family.apk
```

## Roadmap

| Milestone | What |
|---|---|
| 1 ✅ | Packed pairing, silent identity, invite onboarding, WeChat-style text chat, read aloud |
| 2 | Hold-to-talk voice as the main input. Who transcribes it is not decided yet |
| 3 | 小乐 sends good news on its own, without being asked |
| 4 | The agent sets the avatar, text size and read-aloud the parent gets (the name already works) |
| 5 | Remote care: the agent can take a screenshot, change settings, read logs and update the app |
| 6 | Staying connected in the background on Huawei and other phones without Google services |

## 小乐, the agent's side

The app is only the parents' end. What 小乐 says is decided by a skill on the child's agent: [`skills/xiaole/SKILL.md`](skills/xiaole/SKILL.md). Copy it into your agent's `.co/skills/xiaole/`. It tells the agent to:

- say it is an AI, and never speak as the child;
- tell only what really happened, and say "I'll ask" when it does not know;
- leave money, health and relationships for the child to tell;
- tell the child at once if the parents say something worrying;
- describe only what the app can do (type, and tap a reply to hear it), never "just speak" or "I'll call you".

The skill has a benchmark, [`skills/xiaole/benchmark/xiaole.yaml`](skills/xiaole/benchmark/xiaole.yaml). Copy it to your agent's `.co/benchmarks/` and run `co eval run xiaole --agent <your agent file>` before and after you change the skill.

## Design rules

- **Like WeChat.** Anything the parent already does in WeChat works the same way here, in the same place and colour. What WeChat has and this app does not need is left out.
- **One screen.** There are no menus, contacts or settings for the parent.
- **Large by default.** Body text is 24sp and main targets are at least 60dp. This follows the main-text and target-size minimums of China's 2021 elderly-app spec from MIIT.
- **Plain sentences only.** The parent never sees an error code.

## License

MIT. See [LICENSE](LICENSE).
