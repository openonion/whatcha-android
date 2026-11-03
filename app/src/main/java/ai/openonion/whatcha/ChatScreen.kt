package ai.openonion.whatcha

import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
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
private val Small = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, color = Color(0xFF888888))
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

    Column(Modifier.fillMaxSize().background(ChatBackground).safeDrawingPadding().imePadding()) {
        Header(headerTitle(title, lines), status)
        Conversation(lines, title, onReplay = vm::speak, modifier = Modifier.weight(1f))
        if (status != Status.UNPAIRED) Composer(onSend = vm::send)
    }
}

@Composable
private fun Header(title: String, status: Status) {
    Column(Modifier.fillMaxWidth().background(ChatBackground).padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = Body.copy(fontWeight = FontWeight.Medium))
        statusText(status)?.let { Text(it, style = Small) }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Divider))
}

// WeChat's own words while the other side is writing, so the parent knows to wait.
internal fun headerTitle(name: String, lines: List<Line>): String =
    if (lines.any { it.delivery == Delivery.SENT }) "对方正在输入..." else name

// The parent only ever sees a plain sentence, never an error code.
private fun statusText(status: Status): String? = when (status) {
    Status.UNPAIRED -> "还没有连上，请让孩子重新发一个安装包"
    Status.CONNECTING -> "正在连接…"
    Status.ONLINE -> null
    Status.OFFLINE -> "暂时联系不上，稍后会自己重试"
}

@Composable
private fun Conversation(lines: List<Line>, agentName: String, onReplay: (String) -> Unit, modifier: Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex) }
    LazyColumn(modifier.fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Spacer(Modifier.size(4.dp)) }
        itemsIndexed(lines) { i, line ->
            // Like WeChat: a grey time line when five minutes have passed since the last message.
            if (i == 0 || line.at - lines[i - 1].at > TimeGapMillis) TimeLabel(line.at)
            if (line.note) Note(line.text) else Bubble(line, agentName, onReplay)
        }
        item { Spacer(Modifier.size(4.dp)) }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = Small, modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp), textAlign = TextAlign.Center)
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
private fun Bubble(line: Line, agentName: String, onReplay: (String) -> Unit) {
    val mine = line.fromParent
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        if (!mine) Avatar(R.drawable.avatar_agent, agentName)
        if (!mine) Tail(pointsLeft = true, color = Color.White)
        // Tapping one of the agent's bubbles reads it aloud again.
        Box(
            Modifier
                .widthIn(max = 270.dp)
                .background(if (mine) ParentBubble else Color.White, RoundedCornerShape(6.dp))
                .clickable(enabled = !mine) { onReplay(line.text) }
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(line.text, style = Body)
        }
        if (mine) Tail(pointsLeft = false, color = ParentBubble)
        if (mine) Avatar(R.drawable.avatar_parent, "我")
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

// WeChat's bottom bar: a white box, and a green 发送 that appears only once there is something to send.
@Composable
private fun Composer(onSend: (String) -> Unit) {
    var draft by remember { mutableStateOf("") }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Divider))
    Row(
        Modifier.fillMaxWidth().background(BarBackground).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = draft,
            onValueChange = { draft = it },
            textStyle = Body,
            cursorBrush = SolidColor(Green),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = TargetSize)
                .background(Color.White, RoundedCornerShape(6.dp))
                .padding(horizontal = 12.dp, vertical = 13.dp),
        )
        if (draft.isNotBlank()) {
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .heightIn(min = TargetSize)
                    .widthIn(min = 88.dp)
                    .background(Green, RoundedCornerShape(6.dp))
                    .clickable { onSend(draft); draft = "" },
                contentAlignment = Alignment.Center,
            ) {
                Text("发送", style = Body.copy(color = Color.White))
            }
        }
    }
}
