package ai.openonion.whatcha

import kotlinx.serialization.Serializable

/** Where a parent's message is on its way to the agent. The agent's own lines are always DONE. */
enum class Delivery { QUEUED, SENT, DONE }

/** A `note` is a grey line from the app itself, centred like WeChat's system messages; nobody said it. */
@Serializable
data class Line(
    val fromParent: Boolean,
    val text: String,
    val at: Long,
    val delivery: Delivery = Delivery.DONE,
    val note: Boolean = false,
)

// Plain words, no error code, and no promise the app cannot keep.
const val AGENT_FAILED = "小乐这会儿出了点问题，没能回答。过一会儿再问问看。"

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
 * The agent failed on the oldest waiting message: stop waiting for it and say so in a grey note.
 * The relay sends empty ERRORs every few seconds while the host is down; those are not failures.
 */
fun List<Line>.failOldest(message: String, at: Long): List<Line> {
    if (message.isEmpty() || none { it.delivery == Delivery.SENT }) return this
    return answerOldest() + Line(fromParent = false, text = AGENT_FAILED, at = at, note = true)
}

/**
 * After a reconnect the host only streams a reply for a run still in progress. If the agent is idle, a
 * message still waiting lost its reply (the app was killed, or the link dropped as the run ended), so ask again.
 */
fun List<Line>.afterReconnect(agentRunning: Boolean): List<Line> = if (agentRunning) this else requeueSent()
