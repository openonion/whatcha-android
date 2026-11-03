package ai.openonion.whatcha

import android.app.Application
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale

enum class Status { UNPAIRED, CONNECTING, ONLINE, OFFLINE }

/** The one conversation on this phone. Keeps it on disk, reads replies aloud, reconnects on its own. */
class ChatViewModel(app: Application) : AndroidViewModel(app), AgentLink.Listener {

    private val prefs = app.getSharedPreferences("chat", 0)
    private val pairing = loadPairing(app)
    private val link = pairing?.let { AgentLink(it, Wire(Identity.load(app), it.agent), this, prefs.getString("session", null)) }
    private val voice = TextToSpeech(app) { }

    private val _lines = MutableStateFlow(loadLines())
    val lines: StateFlow<List<Line>> = _lines

    private val _title = MutableStateFlow(prefs.getString("title", null) ?: "在干嘛")
    val title: StateFlow<String> = _title

    private val _status = MutableStateFlow(if (link == null) Status.UNPAIRED else Status.CONNECTING)
    val status: StateFlow<Status> = _status

    init {
        Log.i(TAG, "address=${Identity.load(app).address} agent=${pairing?.agent}")
        link?.open()
    }

    fun send(text: String) {
        val prompt = text.trim()
        if (prompt.isEmpty() || link == null) return
        append(Line(fromParent = true, text = prompt, at = System.currentTimeMillis(), delivery = Delivery.QUEUED))
        if (_status.value == Status.ONLINE) sendQueued()
    }

    // Everything the parent wrote goes out in order, however long the link was down.
    private fun sendQueued() {
        for (line in _lines.value.queued()) {
            if (!link!!.send(line.text)) return
            save(_lines.value.markSent(line))
        }
    }

    fun speak(text: String) {
        voice.language = Locale.SIMPLIFIED_CHINESE
        voice.speak(text, TextToSpeech.QUEUE_FLUSH, null, text.hashCode().toString())
    }

    // OkHttp calls back on its own thread; the conversation is only ever changed on the main thread.
    override fun onConnected(sessionId: String, running: Boolean) = onMain {
        Log.i(TAG, "connected session=$sessionId running=$running")
        prefs.edit().putString("session", sessionId).apply()
        save(_lines.value.afterReconnect(running))
        _status.value = Status.ONLINE
        sendQueued()
    }

    override fun onRejected() = onMain {
        Log.w(TAG, "host forgot this phone; reconnecting")
        save(_lines.value.requeueSent())
    }

    override fun onProfile(name: String) {
        _title.value = name
        prefs.edit().putString("title", name).apply()
    }

    override fun onReply(text: String) = onMain {
        Log.i(TAG, "reply ${text.length} chars")
        save(_lines.value.answerOldest())
        append(Line(fromParent = false, text = text, at = System.currentTimeMillis()))
        speak(text)
    }

    override fun onError(message: String) = onMain {
        Log.w(TAG, "agent error: $message")
        save(_lines.value.failOldest(message, System.currentTimeMillis()))
    }

    override fun onDisconnected(reason: String) {
        Log.w(TAG, "disconnected: $reason")
        _status.value = Status.OFFLINE
        viewModelScope.launch {
            delay(RECONNECT_DELAY_MS)
            _status.value = Status.CONNECTING
            link?.open()
        }
    }

    override fun onCleared() {
        voice.shutdown()
    }

    private fun onMain(block: () -> Unit) {
        viewModelScope.launch { block() }
    }

    private fun append(line: Line) = save(_lines.value + line)

    private fun save(lines: List<Line>) {
        _lines.value = lines
        prefs.edit().putString("lines", Json.encodeToString(lines)).apply()
    }

    private fun loadLines(): List<Line> =
        prefs.getString("lines", null)?.let { Json.decodeFromString<List<Line>>(it) } ?: emptyList()

    private companion object {
        const val TAG = "Whatcha"
        const val RECONNECT_DELAY_MS = 5_000L
    }
}
