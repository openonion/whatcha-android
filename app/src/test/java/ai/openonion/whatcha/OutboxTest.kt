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
        assertEquals(lines, lines.afterReconnect(agentRunning = true, history = emptyList(), now = 1))
    }

    @Test
    fun aReconnectToAnIdleAgentAsksAgainBecauseThatReplyWillNeverCome() {
        val lines = listOf(asked("a", Delivery.SENT))
        assertEquals(listOf(Delivery.QUEUED), lines.afterReconnect(agentRunning = false, history = emptyList(), now = 1).map { it.delivery })
    }

    @Test
    fun aReconnectLeavesFailedMessagesForTheParent() {
        val lines = listOf(asked("a", Delivery.FAILED), asked("b", Delivery.SENT))
        assertEquals(listOf(Delivery.FAILED, Delivery.QUEUED), lines.requeueSent().map { it.delivery })
        assertEquals(listOf(Delivery.FAILED, Delivery.QUEUED), lines.afterReconnect(agentRunning = false, history = emptyList(), now = 1).map { it.delivery })
    }

    // What CONNECTED's chat_items says, as (fromParent, text): the run finished while the app was away.
    private val answered = listOf(true to "Has he eaten", false to "Yes, noodles", true to "What is he doing", false to "Fixing a shop's app")

    private fun answer(text: String) = Line(fromParent = false, text = text, at = 0)

    @Test
    fun answeredWhileAwayIsShownNotAskedAgain() {
        val lines = listOf(asked("Has he eaten", Delivery.DONE), answer("Yes, noodles"), asked("What is he doing", Delivery.SENT))
        val after = lines.afterReconnect(agentRunning = false, history = answered, now = 5)
        assertEquals(listOf(Delivery.DONE, Delivery.DONE, Delivery.DONE, Delivery.DONE), after.map { it.delivery })
        assertEquals(Line(fromParent = false, text = "Fixing a shop's app", at = 5), after.last())
        assertNull(after.nextToSend())
    }

    @Test
    fun stillRunningKeepsWaiting() {
        val lines = listOf(asked("What is he doing", Delivery.SENT))
        assertEquals(lines, lines.afterReconnect(agentRunning = true, history = answered, now = 5))
    }

    @Test
    fun neverArrivedIsAskedAgain() {
        val lines = listOf(asked("Has he eaten", Delivery.DONE), answer("Yes, noodles"), asked("Is he busy", Delivery.SENT))
        assertEquals(Delivery.QUEUED, lines.afterReconnect(agentRunning = false, history = answered, now = 5).last().delivery)
        assertEquals(Delivery.QUEUED, lines.afterReconnect(agentRunning = false, history = emptyList(), now = 5).last().delivery)
    }

    // The host holds the answer to an earlier identical question, already on the phone: it is not this one's.
    @Test
    fun sameQuestionTwiceDoesNotTakeTheOldAnswer() {
        val lines = listOf(asked("What is he doing", Delivery.DONE), answer("Fixing a shop's app"), asked("What is he doing", Delivery.SENT))
        val after = lines.afterReconnect(agentRunning = false, history = answered, now = 5)
        assertEquals(3, after.size)
        assertEquals(Delivery.QUEUED, after.last().delivery)
    }

    @Test
    fun failedRunIsAskedAgain() {
        val lines = listOf(asked("What is he doing", Delivery.SENT))
        val after = lines.afterReconnect(agentRunning = false, history = answered.dropLast(1), now = 5)
        assertEquals(listOf(Delivery.QUEUED), after.map { it.delivery })
    }
}
