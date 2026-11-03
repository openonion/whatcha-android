package ai.openonion.whatcha

import android.app.Application
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale

@Serializable
data class Line(val fromParent: Boolean, val text: String, val at: Long)

enum class Status { UNPAIRED, CONNECTING, ONLINE, OFFLINE }

/** The one conversation on this phone. Keeps it on disk, reads replies aloud, reconnects on its own. */
class ChatViewModel(app: Application) : AndroidViewModel(app), AgentLink.Listener {

    private val prefs = app.getSharedPreferences("chat", 0)
    private val pairing = loadPairing(app)
    private val link = pairing?.let { AgentLink(it, Wire(Identity.load(app), it.agent), this) }
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
        append(Line(fromParent = true, text = prompt, at = System.currentTimeMillis()))
        if (!link.send(prompt)) _status.value = Status.OFFLINE
    }

    fun speak(text: String) {
        voice.language = Locale.SIMPLIFIED_CHINESE
        voice.speak(text, TextToSpeech.QUEUE_FLUSH, null, text.hashCode().toString())
    }

    override fun onConnected() {
        Log.i(TAG, "connected")
        _status.value = Status.ONLINE
    }

    override fun onProfile(name: String) {
        _title.value = name
        prefs.edit().putString("title", name).apply()
    }

    override fun onReply(text: String) {
        Log.i(TAG, "reply ${text.length} chars")
        append(Line(fromParent = false, text = text, at = System.currentTimeMillis()))
        speak(text)
    }

    override fun onError(message: String) {
        Log.w(TAG, "agent error: $message")
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

    private fun append(line: Line) {
        _lines.update { it + line }
        prefs.edit().putString("lines", Json.encodeToString(_lines.value)).apply()
    }

    private fun loadLines(): List<Line> =
        prefs.getString("lines", null)?.let { Json.decodeFromString<List<Line>>(it) } ?: emptyList()

    private companion object {
        const val TAG = "Whatcha"
        const val RECONNECT_DELAY_MS = 5_000L
    }
}
