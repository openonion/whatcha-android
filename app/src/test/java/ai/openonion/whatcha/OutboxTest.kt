package ai.openonion.whatcha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OutboxTest {
    private fun asked(text: String, delivery: Delivery) = Line(fromParent = true, text = text, at = 0, delivery = delivery)

    // The host takes one INPUT per turn: a second one while a turn runs is answered with
    // `ERROR running turn is not accepting runtime input; retry after OUTPUT` (connectonion 1.9.3rc41).
    @Test
    fun aSecondQuestionWaitsUntilTheFirstIsAnswered() {
        val lines = listOf(asked("a", Delivery.SENT), asked("b", Delivery.QUEUED))
        assertNull(lines.nextToSend())
    }

    @Test
    fun theNextQuestionGoesOutOnceTheAnswerIsIn() {
        val lines = listOf(asked("a", Delivery.DONE), asked("b", Delivery.QUEUED), asked("c", Delivery.QUEUED))
        assertEquals("b", lines.nextToSend()?.text)
    }

    @Test
    fun nothingWaitingGivesNull() {
        val lines = listOf(asked("a", Delivery.DONE), asked("b", Delivery.FAILED))
        assertNull(lines.nextToSend())
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

    @Test
    fun aReconnectLeavesFailedMessagesForTheParent() {
        val lines = listOf(asked("a", Delivery.FAILED), asked("b", Delivery.SENT))
        assertEquals(listOf(Delivery.FAILED, Delivery.QUEUED), lines.requeueSent().map { it.delivery })
        assertEquals(listOf(Delivery.FAILED, Delivery.QUEUED), lines.afterReconnect(agentRunning = false).map { it.delivery })
    }
}
