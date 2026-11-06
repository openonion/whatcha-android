package ai.openonion.whatcha

import android.app.Application
import android.net.Uri
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

// Each failed try waits twice as long as the last, up to a minute: a phone on mobile data all night, Mac asleep,
// must not open a TLS connection every 5 s.
internal fun nextDelay(previous: Long): Long = minOf(previous * 2, 60_000L)

/** The one conversation on this phone. Keeps it on disk, reads replies aloud, reconnects on its own. */
class ChatViewModel(app: Application) : AndroidViewModel(app), AgentLink.Listener {

    private val prefs = app.getSharedPreferences("chat", 0)
    private val pairing = loadPairing(app)
    private val link = pairing?.let { AgentLink(it, Wire(Identity.load(app), it.agent), this, prefs.getString("session", null)) }
    private val voice = TextToSpeech(app) { }
    private var spoken: String? = null
    private val recorder = Recorder(app)
    private val player = Player()
    private var reconnectDelay = RECONNECT_DELAY_MS

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
        _lines.value.staleVoices(System.currentTimeMillis()).forEach { File(it).delete() }
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

    /** Whether this parent has ever said No to the microphone; it tells Back on the first question from "don't ask again". */
    val saidNoToMic get() = prefs.getBoolean("saidNoToMic", false)

    fun rememberSaidNoToMic() = prefs.edit().putBoolean("saidNoToMic", true).apply()

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

    /** A picture picked from the album: shrunk first, because the host takes 10 MB and a phone photo can be more. */
    fun sendPhoto(uri: Uri) {
        val file = shrink(getApplication(), uri, System.currentTimeMillis())
        queue(Line(fromParent = true, text = photoPrompt(), at = System.currentTimeMillis(), delivery = Delivery.QUEUED, photo = file.path))
    }

    /** A tap on the red mark beside a failed message sends that same message, recording or picture, once more. */
    fun retry(line: Line) {
        save(_lines.value.retry(line))
        if (_status.value == Status.ONLINE) sendQueued()
    }

    /** The bubble stays after its recording was swept (a month after it was heard), so a tap says so instead of playing. */
    fun playVoice(path: String) {
        if (!File(path).exists()) return Toast.makeText(getApplication(), "这条语音太久了，已经不在了", Toast.LENGTH_SHORT).show()
        voice.stop()
        player.toggle(path)
    }

    private fun queue(line: Line) {
        append(line)
        if (_status.value == Status.ONLINE) sendQueued()
    }

    // One message at a time, in order: the next goes out from onReply or onError, however long the link was down.
    private fun sendQueued() {
        val line = _lines.value.nextToSend() ?: return
        val sound = line.voice?.let { File(it) }?.let { Voice(it.name, it.readBytes()) }
        val picture = line.photo?.let { File(it).readBytes() }
        if (link!!.send(line.text, sound, picture)) save(_lines.value.markSent(line))
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
        reconnectDelay = RECONNECT_DELAY_MS
        _status.value = Status.ONLINE
        sendQueued()
    }

    override fun onRejected() = onMain {
        Log.w(TAG, "host forgot this phone; reconnecting")
        save(_lines.value.requeueSent())
    }

    // Still retried by onDisconnected, slower and slower: a host whose code is corrected picks the phone up untouched.
    override fun onRefused() = onMain {
        Log.w(TAG, "host refused this phone's invite code")
        _status.value = Status.UNPAIRED
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
        sendQueued()
    }

    override fun onError(message: String) = onMain {
        Log.w(TAG, "agent error: $message")
        save(_lines.value.failOldest(message))
        sendQueued()
    }

    // Stays OFFLINE (or UNPAIRED) while retrying: the strip must not change on every try. onConnected sets ONLINE
    // and puts the wait back to 5 s; 5, 10, 20, 40, 60, 60… s until then.
    override fun onDisconnected(reason: String) = onMain {
        Log.w(TAG, "disconnected: $reason")
        if (_status.value != Status.UNPAIRED) _status.value = Status.OFFLINE
        viewModelScope.launch {
            delay(reconnectDelay)
            reconnectDelay = nextDelay(reconnectDelay)
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
