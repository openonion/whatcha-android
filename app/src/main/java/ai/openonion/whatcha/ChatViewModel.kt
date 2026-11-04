package ai.openonion.whatcha

import android.app.Application
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale

enum class Status { UNPAIRED, CONNECTING, ONLINE, OFFLINE }

// Like a second tap on a WeChat voice message: tapping the reply being read stops it.
internal fun tapStops(speaking: String?, tapped: String): Boolean = speaking == tapped

/** The one conversation on this phone. Keeps it on disk, reads replies aloud, reconnects on its own. */
class ChatViewModel(app: Application) : AndroidViewModel(app), AgentLink.Listener {

    private val prefs = app.getSharedPreferences("chat", 0)
    private val pairing = loadPairing(app)
    private val link = pairing?.let { AgentLink(it, Wire(Identity.load(app), it.agent), this, prefs.getString("session", null)) }
    private val voice = TextToSpeech(app) { }
    private var spoken: String? = null
    private val recorder = Recorder(app)
    private val player = Player()

    private val _lines = MutableStateFlow(loadLines())
    val lines: StateFlow<List<Line>> = _lines

    private val _title = MutableStateFlow(prefs.getString("title", null) ?: "小乐")
    val title: StateFlow<String> = _title

    private val _status = MutableStateFlow(if (link == null) Status.UNPAIRED else Status.CONNECTING)
    val status: StateFlow<Status> = _status

    private val _voiceMode = MutableStateFlow(prefs.getBoolean("voiceMode", false))
    /** Hold-to-talk or the keyboard; remembered, as WeChat does, so a parent who talks never has to switch again. */
    val voiceMode: StateFlow<Boolean> = _voiceMode

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording

    init {
        Log.i(TAG, "address=${Identity.load(app).address} agent=${pairing?.agent}")
        link?.open()
    }

    fun send(text: String) {
        val prompt = text.trim()
        if (prompt.isEmpty() || link == null) return
        queue(Line(fromParent = true, text = prompt, at = System.currentTimeMillis(), delivery = Delivery.QUEUED))
    }

    fun setVoiceMode(on: Boolean) {
        _voiceMode.value = on
        prefs.edit().putBoolean("voiceMode", on).apply()
    }

    fun startRecording() {
        voice.stop()
        player.stop()
        recorder.start(System.currentTimeMillis(), onFull = { onMain(::stopRecording) })
        _recording.value = true
    }

    /** Called on release, and by the recorder at the limit; whichever comes second finds nothing to stop. */
    fun stopRecording() {
        if (!_recording.value) return
        _recording.value = false
        val (file, seconds) = recorder.stop(System.currentTimeMillis())
            ?: return Toast.makeText(getApplication(), "说话时间太短", Toast.LENGTH_SHORT).show()
        queue(Line(fromParent = true, text = voicePrompt(seconds), at = System.currentTimeMillis(),
            delivery = Delivery.QUEUED, voice = file.path, seconds = seconds))
    }

    /** A tap on the red mark beside a failed message sends that same message, or recording, once more. */
    fun retry(line: Line) {
        save(_lines.value.retry(line))
        if (_status.value == Status.ONLINE) sendQueued()
    }

    fun playVoice(path: String) {
        voice.stop()
        player.toggle(path)
    }

    private fun queue(line: Line) {
        append(line)
        if (_status.value == Status.ONLINE) sendQueued()
    }

    // Everything the parent wrote goes out in order, however long the link was down.
    private fun sendQueued() {
        for (line in _lines.value.queued()) {
            val sound = line.voice?.let { File(it) }?.let { Voice(it.name, it.readBytes()) }
            if (!link!!.send(line.text, sound)) return
            save(_lines.value.markSent(line))
        }
    }

    // A tap on 小乐's bubble reads it again, or stops it if that is the one being read.
    fun replay(text: String) {
        if (tapStops(if (voice.isSpeaking) spoken else null, text)) voice.stop() else speak(text)
    }

    private fun speak(text: String) {
        player.stop()
        spoken = text
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
        save(_lines.value.failOldest(message))
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
        player.stop()
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
        prefs.getString("lines", null)?.let { Json.decodeFromString<List<Line>>(it).withoutNotes() } ?: emptyList()

    private companion object {
        const val TAG = "Whatcha"
        const val RECONNECT_DELAY_MS = 5_000L
    }
}
