package ai.openonion.whatcha

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Sizes follow MIIT's 2021 elderly-app spec: main text >= 18, main targets >= 60x60, line height >= 1.3x.
private val Body = TextStyle(fontSize = 24.sp, lineHeight = 34.sp, color = Color(0xFF111111))
private val Small = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, color = Color(0xFF555555))
private val ChatBackground = Color(0xFFEDEDED)
private val ParentBubble = Color(0xFF95EC69)
private val Green = Color(0xFF07C160)
private val TargetSize = 60.dp

@Composable
fun ChatScreen(vm: ChatViewModel) {
    val lines by vm.lines.collectAsState()
    val title by vm.title.collectAsState()
    val status by vm.status.collectAsState()

    Column(Modifier.fillMaxSize().background(ChatBackground).safeDrawingPadding().imePadding()) {
        Header(title, status)
        Conversation(lines, title, onReplay = vm::speak, modifier = Modifier.weight(1f))
        if (status != Status.UNPAIRED) Composer(onSend = vm::send)
    }
}

@Composable
private fun Header(title: String, status: Status) {
    Column(Modifier.fillMaxWidth().background(Color(0xFFF7F7F7)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = Body.copy(fontWeight = FontWeight.Bold))
        statusText(status)?.let { Text(it, style = Small) }
    }
}

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
        item { Spacer(Modifier.size(8.dp)) }
        items(lines) { line -> Bubble(line, agentName, onReplay) }
        item { Spacer(Modifier.size(8.dp)) }
    }
}

@Composable
private fun Bubble(line: Line, agentName: String, onReplay: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = if (line.fromParent) Arrangement.End else Arrangement.Start,
    ) {
        if (!line.fromParent) Avatar(agentName)
        // Tapping one of the agent's bubbles reads it aloud again.
        Box(
            Modifier
                .padding(horizontal = 8.dp)
                .widthIn(max = 300.dp)
                .background(if (line.fromParent) ParentBubble else Color.White, RoundedCornerShape(8.dp))
                .clickable(enabled = !line.fromParent) { onReplay(line.text) }
                .padding(14.dp)
        ) {
            Text(line.text, style = Body)
        }
    }
}

@Composable
private fun Avatar(name: String) {
    Box(Modifier.size(TargetSize).background(Green, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
        Text(name.take(1), style = Body.copy(color = Color.White, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun Composer(onSend: (String) -> Unit) {
    var draft by remember { mutableStateOf("") }
    Row(
        Modifier.fillMaxWidth().background(Color(0xFFF7F7F7)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = draft,
            onValueChange = { draft = it },
            textStyle = Body,
            modifier = Modifier.weight(1f).heightIn(min = TargetSize),
        )
        Spacer(Modifier.size(12.dp))
        Button(
            onClick = { onSend(draft); draft = "" },
            enabled = draft.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = Green),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.heightIn(min = TargetSize).widthIn(min = 96.dp),
        ) {
            Text("发送", style = Body.copy(color = Color.White))
        }
    }
}
