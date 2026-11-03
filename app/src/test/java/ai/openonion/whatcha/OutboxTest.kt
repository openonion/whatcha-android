package ai.openonion.whatcha

import org.junit.Assert.assertEquals
import org.junit.Test

class OutboxTest {
    private fun asked(text: String, delivery: Delivery) = Line(fromParent = true, text = text, at = 0, delivery = delivery)

    @Test
    fun onlyQueuedMessagesAreWaitingToGoOut() {
        val lines = listOf(asked("a", Delivery.DONE), asked("b", Delivery.SENT), asked("c", Delivery.QUEUED))
        assertEquals(listOf("c"), lines.queued().map { it.text })
    }

    @Test
    fun aReplyAnswersTheOldestSentMessage() {
        val lines = listOf(asked("a", Delivery.SENT), asked("b", Delivery.SENT))
        assertEquals(listOf(Delivery.DONE, Delivery.SENT), lines.answerOldest().map { it.delivery })
    }

    @Test
    fun aRejectionPutsSentMessagesBackInTheQueue() {
        val lines = listOf(asked("a", Delivery.DONE), asked("b", Delivery.SENT))
        assertEquals(listOf(Delivery.DONE, Delivery.QUEUED), lines.requeueSent().map { it.delivery })
    }

    @Test
    fun historySavedBeforeDeliveryExistedStillLoads() {
        val old = """[{"fromParent":true,"text":"hi","at":1}]"""
        assertEquals(Delivery.DONE, kotlinx.serialization.json.Json.decodeFromString<List<Line>>(old).single().delivery)
    }

    @Test
    fun aReconnectToAStillRunningAgentKeepsWaitingForItsReply() {
        val lines = listOf(asked("a", Delivery.SENT))
        assertEquals(lines, lines.afterReconnect(agentRunning = true))
    }

    @Test
    fun aReconnectToAnIdleAgentAsksAgainBecauseThatReplyWillNeverCome() {
        val lines = listOf(asked("a", Delivery.SENT))
        assertEquals(listOf(Delivery.QUEUED), lines.afterReconnect(agentRunning = false).map { it.delivery })
    }
}
