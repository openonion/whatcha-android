package ai.openonion.whatcha

import kotlinx.serialization.Serializable

/** Where a parent's message is on its way to the agent. The agent's own lines are always DONE. */
enum class Delivery { QUEUED, SENT, DONE }

@Serializable
data class Line(val fromParent: Boolean, val text: String, val at: Long, val delivery: Delivery = Delivery.DONE)

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
