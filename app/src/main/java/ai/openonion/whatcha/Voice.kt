package ai.openonion.whatcha

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.io.File

// Hold-to-talk, the way parents already use WeChat. The sound goes to the agent as it is;
// turning it into words is the agent's job, after it arrives.

const val MAX_VOICE_SECONDS = 60

/** WeChat drops a press too short to hold any words, rather than sending a blip. */
internal fun worthSending(millis: Long): Boolean = millis >= 1_000

enum class MicStep { TALK, ASK_AGAIN, OPEN_SETTINGS }

const val MIC_ASK_AGAIN = "要允许用麦克风，才能按住说话"
const val MIC_OPEN_SETTINGS = "点「权限」，把「麦克风」打开，再回来"

/**
 * What happens after the system's microphone question. [canAskAgain] is shouldShowRequestPermissionRationale,
 * read in the answer: once it is false after a no, Android will not ask again and only the settings page helps.
 */
internal fun afterMicAnswer(granted: Boolean, canAskAgain: Boolean): MicStep = when {
    granted -> MicStep.TALK
    canAskAgain -> MicStep.ASK_AGAIN
    else -> MicStep.OPEN_SETTINGS
}

internal fun voiceLabel(seconds: Int): String = "$seconds″"

/** What the agent reads alongside the file; the host adds the file's path after it. */
internal fun voicePrompt(seconds: Int): String = "[语音 ${seconds}秒]"

/** Like WeChat: a longer message is a wider bubble, so a parent can tell them apart at a glance. */
internal fun voiceBubbleWidth(seconds: Int): Dp = 90.dp + 2.dp * seconds.coerceAtMost(MAX_VOICE_SECONDS)

/** Records one AAC voice message at a time into the app's own storage, where it waits if the link is down. */
class Recorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    /** [onFull] runs when a message reaches the limit; like WeChat, it is then sent without waiting for release. */
    fun start(at: Long, onFull: () -> Unit) {
        val out = File(context.filesDir, "voice").apply { mkdirs() }.resolve("$at.m4a")
        recorder = newRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16_000)
            setAudioEncodingBitRate(32_000)
            setMaxDuration(MAX_VOICE_SECONDS * 1_000)
            setOutputFile(out.path)
            setOnInfoListener { _, what, _ -> if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) onFull() }
            prepare()
            start()
        }
        file = out
        startedAt = at
    }

    /** The finished file and its length, or null when the press was too short and the file is gone. */
    fun stop(at: Long): Pair<File, Int>? {
        recorder!!.stop()
        recorder!!.release()
        recorder = null
        val out = file!!
        val millis = (at - startedAt).coerceAtMost(MAX_VOICE_SECONDS * 1_000L)
        if (worthSending(millis)) return out to ((millis + 500) / 1_000).toInt()
        out.delete()
        return null
    }

    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
}

/** Plays one voice message; a tap on the one playing stops it, as with read-aloud. */
class Player {
    private var player: MediaPlayer? = null
    private var playing: String? = null

    fun toggle(path: String) {
        val stops = tapStops(if (player?.isPlaying == true) playing else null, path)
        stop()
        if (stops) return
        playing = path
        player = MediaPlayer().apply { setDataSource(path); prepare(); start() }
    }

    fun stop() {
        player?.release()
        player = null
    }
}
