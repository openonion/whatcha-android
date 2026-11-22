package ai.openonion.whatcha

import kotlinx.serialization.Serializable

/** Where a parent's message is on its way to the agent. The agent's own lines are always DONE. FAILED waits for the parent's tap. */
enum class Delivery { QUEUED, SENT, DONE, FAILED }

/**
 * A `voice` line is a recording kept in the app's files: its path, and how many seconds it lasts.
 * A `photo` line is the shrunk copy of a picture from the album, kept the same way; it is what was sent.
 * `note` is only read from histories saved before round 5, which stored the failure note as a line of its own;
 * the Json config fails on unknown keys, so the field stays and `withoutNotes` drops those lines on load.
 */
@Serializable
data class Line(
    val fromParent: Boolean,
    val text: String,
    val at: Long,
    val delivery: Delivery = Delivery.DONE,
    val note: Boolean = false,
    val voice: String? = null,
    val seconds: Int = 0,
    val photo: String? = null,
)

fun List<Line>.withoutNotes(): List<Line> = filterNot { it.note }

const val VOICE_KEPT_MILLIS = 30L * 24 * 60 * 60 * 1_000

/**
 * Recordings the phone no longer needs: heard by the agent (DONE) and a month old. One still QUEUED, SENT or
 * FAILED is kept however old, because a retry uploads the file again.
 */
fun List<Line>.staleVoices(now: Long): List<String> =
    filter { it.voice != null && it.delivery == Delivery.DONE && it.at < now - VOICE_KEPT_MILLIS }.map { it.voice!! }

// Plain words, no error code, and no promise the app cannot keep.
const val AGENT_FAILED = "小乐这会儿出了点问题，没能回答。点一下红色的感叹号，再发一次。"

/**
 * The one message to send now, or null. The host takes one INPUT per turn: a second one while a turn runs is
 * answered with `ERROR running turn is not accepting runtime input` (connectonion 1.9.3rc41), which would land
 * on the first message as a failure. So nothing goes out while a message is SENT; after its OUTPUT or ERROR
 * the first QUEUED line follows, and lines written while the link was down go out one by one on reconnect.
 */
fun List<Line>.nextToSend(): Line? =
    if (any { it.delivery == Delivery.SENT }) null else firstOrNull { it.delivery == Delivery.QUEUED }

/** Replies carry no id, and the agent answers in order, so a reply belongs to the oldest message still waiting. */
fun List<Line>.answerOldest(): List<Line> {
    val i = indexOfFirst { it.delivery == Delivery.SENT }
    return if (i < 0) this else toMutableList().apply { this[i] = this[i].copy(delivery = Delivery.DONE) }
}

fun List<Line>.requeueSent(): List<Line> =
    map { if (it.delivery == Delivery.SENT) it.copy(delivery = Delivery.QUEUED) else it }

fun List<Line>.markSent(line: Line): List<Line> =
    map { if (it === line) it.copy(delivery = Delivery.SENT) else it }

/**
 * The agent failed on the oldest waiting message: mark it, as WeChat's red !. The screen says so under the
 * bubble for as long as the mark is there, so nothing is stored beyond the delivery state.
 * The relay sends empty ERRORs every few seconds while the host is down; those are not failures.
 */
fun List<Line>.failOldest(message: String): List<Line> {
    val i = indexOfFirst { it.delivery == Delivery.SENT }
    if (message.isEmpty() || i < 0) return this
    return toMutableList().apply { this[i] = this[i].copy(delivery = Delivery.FAILED) }
}

/** The parent tapped the red mark: that one message goes back in the queue, in its place. */
fun List<Line>.retry(line: Line): List<Line> =
    map { if (it === line && it.delivery == Delivery.FAILED) it.copy(delivery = Delivery.QUEUED) else it }

/**
 * After a reconnect the host only streams a reply for a run still in progress. When the run has ended, a message
 * still waiting lost its reply (the app was killed, or the link dropped as the run ended). connectonion's
 * establish_connection (network/host/ws_router/connect.py) sends the stored conversation as `chat_items` when it
 * is newer than ours, which [history] holds as (fromParent, text): if its last question is this message and an
 * answer follows that is not already the newest reply here, that answer is shown. Otherwise the message is asked
 * again: the question never arrived, the session is new, the run failed, or that answer was to an earlier,
 * identical question.
 */
fun List<Line>.afterReconnect(agentRunning: Boolean, history: List<Pair<Boolean, String>>, now: Long): List<Line> {
    if (agentRunning) return this
    val answer = answerIn(history) ?: return requeueSent()
    return answerOldest() + Line(fromParent = false, text = answer, at = now)
}

private fun List<Line>.answerIn(history: List<Pair<Boolean, String>>): String? {
    val waiting = firstOrNull { it.delivery == Delivery.SENT } ?: return null
    val asked = history.indexOfLast { it.first }
    if (asked < 0 || history[asked].second != waiting.text) return null
    val answer = history.drop(asked + 1).lastOrNull()?.second ?: return null
    return answer.takeIf { it != lastOrNull { !it.fromParent }?.text }
}
