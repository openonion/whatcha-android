package ai.openonion.whatcha

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// Looks and behaves like a WeChat chat, because WeChat is the one app parents already know.
// Sizes follow MIIT's 2021 elderly-app spec: main text >= 18, main targets >= 60x60, line height >= 1.3x.
private val Body = TextStyle(fontSize = 24.sp, lineHeight = 34.sp, color = Color(0xFF111111))
private val Small = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, color = Color(0xFF666666))
private val ChatBackground = Color(0xFFEDEDED)
private val BarBackground = Color(0xFFF7F7F7)
private val Divider = Color(0xFFD9D9D9)
private val ParentBubble = Color(0xFF95EC69)
private val Green = Color(0xFF07C160)
private val TargetSize = 60.dp
private const val TimeGapMillis = 5 * 60 * 1000L

@Composable
fun ChatScreen(vm: ChatViewModel) {
    val lines by vm.lines.collectAsState()
    val title by vm.title.collectAsState()
    val status by vm.status.collectAsState()
    val voiceMode by vm.voiceMode.collectAsState()
    val recording by vm.recording.collectAsState()
    var viewing by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().background(ChatBackground)) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Column(Modifier.fillMaxSize()) {
                Header(headerTitle(title, lines, status))
                statusText(status)?.let { StatusStrip(it) }
                val hint = emptyHint(title, lines, status)
                if (hint != null) EmptyHint(hint, Modifier.weight(1f))
                else Conversation(lines, title, onReplay = vm::replay, onPlay = vm::playVoice, onView = { viewing = it }, onRetry = vm::retry, modifier = Modifier.weight(1f))
                if (status != Status.UNPAIRED) Composer(vm, voiceMode, recording)
            }
            if (recording) RecordingCard(Modifier.align(Alignment.Center))
        }
        // Outside the padded box so the black reaches every edge of the screen, as WeChat's viewer does.
        viewing?.let { PhotoViewer(it, onClose = { viewing = null }) }
    }
}

// WeChat's full-screen picture: the kept JPEG (at most 1280px, so decoded whole) on black, fitted; a tap anywhere
// or Back brings the chat back exactly as it was. No zoom, no buttons: WeChat has none for closing either.
@Composable
private fun PhotoViewer(path: String, onClose: () -> Unit) {
    BackHandler { onClose() }
    val bitmap = remember(path) { BitmapFactory.decodeFile(path).asImageBitmap() }
    Box(Modifier.fillMaxSize().background(Color.Black).clickable { onClose() }) {
        Image(bitmap, contentDescription = "我发的照片，点一下关上", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    }
}

// WeChat's dark square in the middle of the screen while a parent holds to talk.
@Composable
private fun RecordingCard(modifier: Modifier) {
    Column(
        modifier.widthIn(min = 200.dp).heightIn(min = 200.dp).background(Color(0xFF2F2F2F), RoundedCornerShape(12.dp)).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(painterResource(R.drawable.ic_voice), contentDescription = null, modifier = Modifier.size(72.dp),
            colorFilter = ColorFilter.tint(Color.White))
        Spacer(Modifier.height(16.dp))
        Text("松开 发送", style = Body.copy(color = Color.White))
    }
}

@Composable
private fun Header(title: String) {
    Text(title, style = Body.copy(fontWeight = FontWeight.Medium), textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().background(ChatBackground).padding(horizontal = 24.dp, vertical = 14.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(Divider))
}

// WeChat shows link trouble as a pale red strip under the title, a red circled ! and dark text, not as a grey subtitle.
@Composable
private fun StatusStrip(text: String) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFFFDECEC)).heightIn(min = TargetSize).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RedBang()
        Spacer(Modifier.width(12.dp))
        Text(text, style = Body)
    }
}

// A blank grey page gives a parent no reason to type; say what this is for until the first message.
// With no line yet the grey middle says what the parent does next: ask, or, while the Mac has not accepted
// this phone's invite code, call the child. With history the history is the page (11d).
internal fun emptyHint(name: String, lines: List<Line>, status: Status): String? = when {
    lines.isNotEmpty() -> null
    status == Status.UNPAIRED -> "给孩子打个电话"
    else -> "想知道孩子在干嘛，\n就在下面问$name，\n打字说话都行"
}

@Composable
private fun EmptyHint(hint: String, modifier: Modifier) {
    Box(modifier.fillMaxWidth().padding(horizontal = 40.dp), contentAlignment = Alignment.Center) {
        Text(hint, style = Body.copy(color = Color(0xFF666666)), textAlign = TextAlign.Center)
    }
}

// WeChat's own words: 「对方正在输入...」 while the other side is writing, 「微信(连接中…)」 in the title on a cold
// launch, so the parent knows to wait and sees nothing red. CONNECTING only ever means that first connection.
internal fun headerTitle(name: String, lines: List<Line>, status: Status): String = when {
    lines.any { it.delivery == Delivery.SENT } -> "对方正在输入..."
    status == Status.CONNECTING -> "$name(连接中…)"
    else -> name
}

// The strip is only for trouble, as WeChat's is only for 网络不可用. The parent only ever sees a plain
// sentence, never an error code. One clause per line: at the largest system font a clause of eight characters
// still fits beside the !, a ninth wrapped onto a third line and a whole sentence wrapped mid-word.
internal fun statusText(status: Status): String? = when (status) {
    Status.UNPAIRED -> "还没连上，\n让孩子重发安装包"
    Status.CONNECTING -> null
    Status.ONLINE -> null
    Status.OFFLINE -> "暂时联系不上，\n稍后会自己重试"
}

@Composable
private fun Conversation(lines: List<Line>, agentName: String, onReplay: (String) -> Unit, onPlay: (String) -> Unit, onView: (String) -> Unit, onRetry: (Line) -> Unit, modifier: Modifier) {
    // Laid out from the bottom, as WeChat is: the end of the latest line stays in view when it is long,
    // when a new one arrives, and when the keyboard opens and the list gets shorter. A new line jumps the
    // list back to the bottom however far up the parent has scrolled: their own send, the reply they are
    // waiting for, a failed note. Nothing arrives unasked here, so there is no 「新消息」 pill to learn.
    val state = rememberLazyListState()
    LaunchedEffect(lines.size) { state.animateScrollToItem(0) }
    LazyColumn(modifier.fillMaxWidth(), state = state, reverseLayout = true, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Spacer(Modifier.size(4.dp)) }
        items(newestFirst(lines)) { row ->
            // One Column per row: in a reversed list, loose siblings are stacked bottom-up too,
            // which put the time label under its message.
            Column {
                if (row.showTime) TimeLabel(row.line.at)
                when {
                    row.line.voice != null -> Bubble(row.line, agentName, onTap = { onPlay(row.line.voice) }, onRetry = { onRetry(row.line) }) { VoiceContent(row.line) }
                    row.line.photo != null -> Bubble(row.line, agentName, onTap = { onView(row.line.photo) }, onRetry = { onRetry(row.line) }) { PhotoContent(row.line.photo) }
                    else -> Bubble(row.line, agentName, onTap = { onReplay(row.line.text) }.takeIf { !row.line.fromParent }, onRetry = { onRetry(row.line) }) {
                        Text(row.line.text, style = Body)
                    }
                }
                if (row.line.delivery == Delivery.FAILED) Note(AGENT_FAILED, Modifier.padding(top = 16.dp))
            }
        }
        item { Spacer(Modifier.size(4.dp)) }
    }
}

internal class Row(val line: Line, val showTime: Boolean)

// Like WeChat: a grey time line when five minutes have passed since the line before.
internal fun newestFirst(lines: List<Line>): List<Row> =
    lines.indices.reversed().map { i -> Row(lines[i], i == 0 || lines[i].at - lines[i - 1].at > TimeGapMillis) }

@Composable
private fun Note(text: String, modifier: Modifier = Modifier) {
    Text(text, style = Small, modifier = modifier.fillMaxWidth().padding(horizontal = 40.dp), textAlign = TextAlign.Center)
}

@Composable
private fun TimeLabel(at: Long) {
    Text(
        timeLabel(at, System.currentTimeMillis()),
        style = Small,
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        textAlign = TextAlign.Center,
    )
}

// Today shows "晚上 8:35"; earlier days add the date, the way WeChat does.
internal fun timeLabel(at: Long, now: Long): String {
    val then = Calendar.getInstance().apply { timeInMillis = at }
    val today = Calendar.getInstance().apply { timeInMillis = now }
    val hour = then.get(Calendar.HOUR_OF_DAY)
    val part = when (hour) { in 0..5 -> "凌晨"; in 6..11 -> "上午"; 12 -> "中午"; in 13..17 -> "下午"; else -> "晚上" }
    val clock = "$part ${if (hour > 12) hour - 12 else hour}:${"%02d".format(then.get(Calendar.MINUTE))}"
    val sameDay = then.get(Calendar.YEAR) == today.get(Calendar.YEAR) && then.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
    return if (sameDay) clock else SimpleDateFormat("M月d日 ", Locale.CHINA).format(then.time) + clock
}

@Composable
private fun Bubble(line: Line, agentName: String, onTap: (() -> Unit)?, onRetry: () -> Unit, content: @Composable () -> Unit) {
    val mine = line.fromParent
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        if (!mine) Avatar(R.drawable.avatar_agent, agentName)
        if (!mine) Tail(pointsLeft = true, color = Color.White)
        // The mark and the bubble share an inner row so the mark is centred on the bubble, as WeChat does,
        // while tail and avatar stay at the top of the outer row. Weighted so the tail and avatar are measured
        // first and keep their width; the bubble takes what is left, up to 270dp.
        Row(Modifier.weight(1f, fill = false), verticalAlignment = Alignment.CenterVertically) {
            // WeChat puts the sending mark on the outer side of the parent's own bubble.
            if (mine) DeliveryMark(line.delivery, onRetry)
            // Tapping one of the agent's bubbles reads it aloud again, or stops it while it is being read;
            // tapping a voice message plays it.
            Box(
                Modifier
                    .weight(1f, fill = false)
                    .widthIn(max = 270.dp)
                    .background(if (mine) ParentBubble else Color.White, RoundedCornerShape(6.dp))
                    .clickable(enabled = onTap != null) { onTap!!() }
                    .then(if (line.photo != null) Modifier else Modifier.padding(horizontal = 14.dp, vertical = 12.dp))
            ) {
                content()
            }
        }
        if (mine) Tail(pointsLeft = false, color = if (line.photo != null) Color.Transparent else ParentBubble)
        if (mine) Avatar(R.drawable.avatar_parent, "我")
    }
}

// WeChat's marks beside a message of your own: a grey circle while it is on its way, a red ! to tap and
// resend when it failed. The 60dp box (the elder target) stays, empty, once it is DONE, so a long bubble never changes width.
@Composable
private fun DeliveryMark(delivery: Delivery, onRetry: () -> Unit) {
    val failed = delivery == Delivery.FAILED
    Box(
        Modifier.size(TargetSize).clickable(enabled = failed) { onRetry() }
            .then(if (failed) Modifier.semantics { contentDescription = "没发出去，点一下再发一次"; role = Role.Button } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        when (delivery) {
            Delivery.QUEUED, Delivery.SENT -> CircularProgressIndicator(Modifier.size(with(LocalDensity.current) { 20.sp.toDp() }), color = Color(0xFF666666), strokeWidth = 3.dp)
            Delivery.FAILED -> RedBang()
            Delivery.DONE -> Unit
        }
    }
}

// WeChat's red circled !. Circle and glyph are both in sp, so they grow with the system font and keep their
// ratio: 24dp at 1.0, ~36dp at the largest font, still inside the 60dp delivery box and about the height
// of the strip's text. (A dp circle with an sp glyph read as a white bar; a dp pair stayed tiny beside big text.)
@Composable
private fun RedBang() {
    val circle = with(LocalDensity.current) { 24.sp.toDp() }
    Box(Modifier.size(circle).background(Color(0xFFFA5151), CircleShape), contentAlignment = Alignment.Center) {
        Text("!", style = TextStyle(fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold))
    }
}

// WeChat's picture bubble: the photo itself, no frame, up to 200dp, the same rounded corners as the other bubbles.
@Composable
private fun PhotoContent(path: String) {
    val bitmap = remember(path) { thumbnail(path).asImageBitmap() }
    Image(bitmap, contentDescription = "我发的照片", modifier = Modifier.sizeIn(maxWidth = 200.dp, maxHeight = 200.dp).clip(RoundedCornerShape(6.dp)))
}

// WeChat's voice bubble: its length in seconds, and a sound wave pointing at the speaker.
@Composable
private fun VoiceContent(line: Line) {
    Row(Modifier.width(voiceBubbleWidth(line.seconds)), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        Text(voiceLabel(line.seconds), style = Body)
        Spacer(Modifier.width(8.dp))
        Image(painterResource(R.drawable.ic_wave), contentDescription = "语音", modifier = Modifier.size(28.dp).scale(scaleX = -1f, scaleY = 1f))
    }
}

// The small triangle that points from a bubble to its sender's avatar.
@Composable
private fun Tail(pointsLeft: Boolean, color: Color) {
    val shape = GenericShape { size, _ ->
        if (pointsLeft) { moveTo(size.width, 0f); lineTo(0f, size.height / 2); lineTo(size.width, size.height) }
        else { moveTo(0f, 0f); lineTo(size.width, size.height / 2); lineTo(0f, size.height) }
        close()
    }
    Box(Modifier.padding(start = if (pointsLeft) 4.dp else 0.dp, end = if (pointsLeft) 0.dp else 4.dp, top = 22.dp).size(width = 8.dp, height = 16.dp).background(color, shape))
}

// Default pictures until the agent sends its own (roadmap 4). Candidates are in design/avatars/.
@Composable
private fun Avatar(@DrawableRes picture: Int, who: String) {
    Image(
        painterResource(picture),
        contentDescription = who,
        modifier = Modifier.size(TargetSize).clip(RoundedCornerShape(6.dp)),
    )
}

// WeChat's bottom bar: a switch between talking and typing, then either the hold-to-talk bar or a white box,
// then ⊕ for a photo from the album, which gives way to a green 发送 once there is something typed to send.
@Composable
private fun Composer(vm: ChatViewModel, voiceMode: Boolean, recording: Boolean) {
    val context = LocalContext.current
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) vm.setVoiceMode(true) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.sendPhoto(uri) }
    var draft by rememberSaveable { mutableStateOf("") }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Divider))
    Row(
        Modifier.fillMaxWidth().background(BarBackground).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Box(
            Modifier.size(TargetSize).clickable {
                val micAllowed = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (voiceMode || micAllowed) vm.setVoiceMode(!voiceMode) else askMic.launch(Manifest.permission.RECORD_AUDIO)
            },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painterResource(if (voiceMode) R.drawable.ic_keyboard else R.drawable.ic_voice),
                contentDescription = if (voiceMode) "打字" else "按住说话",
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        if (voiceMode) HoldToTalk(recording, onPress = vm::startRecording, onRelease = vm::stopRecording, modifier = Modifier.weight(1f))
        else TypeBox(draft, onChange = { draft = it }, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        if (voiceMode || draft.isBlank()) PickPhoto { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        else SendButton { vm.send(draft); draft = "" }
    }
}

@Composable
private fun PickPhoto(onTap: () -> Unit) {
    Box(Modifier.size(TargetSize).clickable { onTap() }, contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.ic_plus), contentDescription = "发照片", modifier = Modifier.size(40.dp))
    }
}

@Composable
private fun SendButton(onTap: () -> Unit) {
    Box(
        Modifier.heightIn(min = TargetSize).widthIn(min = 88.dp).background(Green, RoundedCornerShape(6.dp)).clickable { onTap() },
        contentAlignment = Alignment.Center,
    ) {
        Text("发送", style = Body.copy(color = Color.White))
    }
}

@Composable
private fun HoldToTalk(recording: Boolean, onPress: () -> Unit, onRelease: () -> Unit, modifier: Modifier) {
    Box(
        modifier
            .heightIn(min = TargetSize)
            .background(if (recording) Color(0xFFC7C7C7) else Color.White, RoundedCornerShape(6.dp))
            .pointerInput(Unit) { detectTapGestures(onPress = { onPress(); tryAwaitRelease(); onRelease() }) },
        contentAlignment = Alignment.Center,
    ) {
        Text(if (recording) "松开 发送" else "按住 说话", style = Body.copy(fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun TypeBox(draft: String, onChange: (String) -> Unit, modifier: Modifier) {
    BasicTextField(
        value = draft,
        onValueChange = onChange,
        textStyle = Body,
        cursorBrush = SolidColor(Green),
        maxLines = 4,
        modifier = modifier
            .heightIn(min = TargetSize)
            .background(Color.White, RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 13.dp),
    )
}
