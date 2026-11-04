package ai.openonion.whatcha

import kotlinx.serialization.Serializable

/** Where a parent's message is on its way to the agent. The agent's own lines are always DONE. FAILED waits for the parent's tap. */
enum class Delivery { QUEUED, SENT, DONE, FAILED }

/**
 * A `note` is a grey line from the app itself, centred like WeChat's system messages; nobody said it.
 * A `voice` line is a recording kept in the app's files: its path, and how many seconds it lasts.
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
)

// Plain words, no error code, and no promise the app cannot keep.
const val AGENT_FAILED = "小乐这会儿出了点问题，没能回答。点一下红色的感叹号再发一次。"

/** Messages written while the link was down, or that the host refused: they go out on the next CONNECTED. */
fun List<Line>.queued(): List<Line> = filter { it.delivery == Delivery.QUEUED }

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
 * The agent failed on the oldest waiting message: mark it, as WeChat's red !, and say so in a grey note.
 * The relay sends empty ERRORs every few seconds while the host is down; those are not failures.
 */
fun List<Line>.failOldest(message: String, at: Long): List<Line> {
    val i = indexOfFirst { it.delivery == Delivery.SENT }
    if (message.isEmpty() || i < 0) return this
    val failed = toMutableList().apply { this[i] = this[i].copy(delivery = Delivery.FAILED) }
    return failed + Line(fromParent = false, text = AGENT_FAILED, at = at, note = true)
}

/** The parent tapped the red mark: that one message goes back in the queue, in its place. */
fun List<Line>.retry(line: Line): List<Line> =
    map { if (it === line && it.delivery == Delivery.FAILED) it.copy(delivery = Delivery.QUEUED) else it }

/**
 * After a reconnect the host only streams a reply for a run still in progress. If the agent is idle, a
 * message still waiting lost its reply (the app was killed, or the link dropped as the run ended), so ask again.
 */
fun List<Line>.afterReconnect(agentRunning: Boolean): List<Line> = if (agentRunning) this else requeueSent()
