# 在干嘛 · Whatcha

[English](README.md)

**「在干嘛」是一个只有一屏的安卓 app，爸妈用它问子女的 AI agent：孩子最近在忙什么。**

爸妈总是挂念，子女又常常忙得顾不上回。子女的 agent 知道子女在做什么，在「在干嘛」里它叫小乐。小乐会直说自己是 AI，从不冒充子女，只讲真实发生的事。

界面和用法照着微信做，因为很多爸妈只会用微信。爸妈那边什么都不用设置，也看不到账号、地址和设置项。所有配置都在 agent 这一侧完成。

<img src="screenshots/chat.png" alt="微信样式的聊天：爸妈的绿色气泡在右边、茶杯头像，小乐的白色气泡在左边、笑脸机器人头像，大字号" width="300">

## 现状

还在早期。第一个里程碑已经端到端跑通：最近一次是 2026-11-04 在安卓模拟器上，通过公共 relay 连 ConnectOnion 1.9.3rc35 的 agent 测过。

现在能做到：

- **配对信息打进安装包。** 爸妈装一个文件、打开就已经连上，不用输入也不用粘贴。
- **身份静默生成。** 手机第一次打开时自己生成 Ed25519 密钥。
- **邀请码自动入门。** app 出示随包带来的邀请码，agent 把这台手机加为联系人。
- **微信样式的聊天。** 绿白气泡带小尖角，两边都有头像，灰色时间，打了字才出现绿色「发送」。标题是 agent 在 `host.yaml` 里设的名字；agent 正在回复时，标题和微信一样显示「对方正在输入...」。
- **爸妈找得到的图标。** 微信绿底上一个白色对话气泡。
- **空聊天会告诉爸妈怎么用：**「想知道孩子在干嘛，就在下面问小乐，打字说话都行」。
- **按住说话。** 跟微信一样，输入框左边的圆按钮切换到「按住 说话」，松开就把录音原样发出去，agent 收到后自己转成文字。按不到一秒的不发，提示「说话时间太短」。点一下语音就能播放。
- **消息不会丢。** 断线时写的消息会等着，agent 回来（包括 agent 重启）后自动发出去。回复还在路上时手机把 app 杀了，下次打开会接上原来的对话，回复照样收到。
- **出错说人话。** agent 出错时，聊天里出一行灰字说明，不让爸妈一直干等。
- **最新一句总在眼前**，字体调多大、键盘开着都一样。
- **回复自动朗读。** 点一下回复可以再听一遍；正在读的时候再点一下就停。
- **聊天记录保存在手机上。**

## 原理

```
子女的 agent ── tools/pack.py ──> 在干嘛-家里.apk ──> 爸妈的手机
      ^                                                  │
      └──────── wss://oo.openonion.ai（relay）──────────┘
```

1. 子女跑一个接受邀请码的 agent。
2. `tools/pack.py` 把 agent 地址和邀请码写进一份 APK 副本。写在 APK 签名区块里，签名不覆盖这一段，所以不用重新签名，也不需要签名密钥。
3. app 第一次打开时，从自己的安装包里读出这一段，经 relay 连上 agent，用邀请码完成入门。

## 试一下

编译并跑测试，需要 JDK 17 和带 API 36 的 Android SDK：

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

起一个接受邀请码的 agent：

```bash
CO_INVITE_CODE=随便起一个码 co host
```

给一家人打一个包并安装：

```bash
python3 tools/pack.py app/build/outputs/apk/debug/app-debug.apk 在干嘛-家里.apk \
  --agent 0x你的agent地址 --invite 随便起一个码
adb install 在干嘛-家里.apk
```

## 路线

| 里程碑 | 内容 |
|---|---|
| 1 ✅ | 安装包内配对、身份静默生成、邀请码入门、微信样式的文字聊天、朗读 |
| 2 ✅ | 按住说话：手机发录音，agent 转文字 |
| 2b | 从相册或相机发照片 |
| 3 | 小乐主动报喜，不用爸妈先问 |
| 4 | 爸妈看到的头像、字号、朗读由 agent 设置（名字已经可以） |
| 5 | 远程照看：agent 能截图、改设置、看日志、更新 app |
| 6 | 在华为等没有谷歌服务的手机上后台保持连接 |

## 小乐：agent 这一侧

app 只是爸妈那一端。小乐说什么，由子女 agent 上的一个 skill 决定：[`skills/xiaole/SKILL.md`](skills/xiaole/SKILL.md)，复制到你的 agent 的 `.co/skills/xiaole/` 即可。它要求 agent：

- 直说自己是 AI，从不以子女的口吻说话；
- 只讲真实发生的事，不知道就说「我帮你问问」；
- 钱、身体、感情留给子女自己说；
- 爸妈说了让人担心的事，马上告诉子女；
- 收到语音先转成文字再回答；没录上声音就请爸妈再说一遍；
- 只说 app 能做到的事（打字、按住说话、点回复重听），不说「我给你打电话」「给你发张照片」。

语音消息需要 agent 有一个转写工具：把 [`skills/xiaole/voice.py`](skills/xiaole/voice.py) 复制到 `agent.py` 旁边，传 `tools=[transcribe_voice]`，机器上要有 `ffmpeg`。它会先查有没有声音，因为对着一段静音，转写服务会编出一句话来。

回答「在干嘛」需要 agent 能读子女的笔记本：把 [`skills/xiaole/rem.py`](skills/xiaole/rem.py) 复制到 `agent.py` 旁边，传它的三个工具 `recent_sessions`、`show_page`、`search_notebook`。它们读 `co rem` 的笔记本（`~/.co/rem`，或 `REM_ROOT`），把页面原样交给 agent；skill 只从中取工作的部分，钱、身体、感情不说。

这个 skill 带一套测试：[`skills/xiaole/benchmark/xiaole.yaml`](skills/xiaole/benchmark/xiaole.yaml)，配一个虚构的笔记本 [`skills/xiaole/benchmark/notebook/`](skills/xiaole/benchmark/notebook/)，里面把修好的 bug 和逾期发票、没睡够、吵架放在一起，专门测这些坑。把 yaml 复制到你 agent 的 `.co/benchmarks/`，改 skill 前后各跑一次 `co eval run xiaole --agent skills/xiaole/benchmark/agent.py`；`python skills/xiaole/benchmark/empty_check.py` 查空笔记本时的回答。

## 设计规矩

- **像微信。** 爸妈在微信里会做的动作，在这里位置、颜色、手势都一样；微信有、这里用不上的，干脆不放。
- **只有一屏。** 爸妈那边没有菜单、通讯录和设置。
- **默认就大。** 正文 24sp，主要按钮至少 60dp，按工信部 2021 年《移动互联网应用（APP）适老化通用设计规范》对主要文字和点击区域的最低要求。
- **只说人话。** 爸妈永远看不到错误码。

## 许可

MIT，见 [LICENSE](LICENSE)。
