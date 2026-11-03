package ai.openonion.whatcha

import org.junit.Assert.assertEquals
import org.junit.Test

class AgentErrorTest {
    private val waiting = listOf(Line(fromParent = true, text = "在干嘛", at = 0, delivery = Delivery.SENT))

    @Test
    fun anErrorEndsTheWaitWithAPlainNote() {
        val lines = waiting.failOldest("credit exhausted", at = 1)
        assertEquals(Delivery.DONE, lines.first().delivery)
        assertEquals(Line(fromParent = false, text = AGENT_FAILED, at = 1, note = true), lines.last())
    }

    @Test
    fun theRelaysEmptyErrorsWhileTheHostIsDownChangeNothing() {
        assertEquals(waiting, waiting.failOldest("", at = 1))
    }

    @Test
    fun anErrorWithNothingWaitingChangesNothing() {
        val answered = waiting.answerOldest()
        assertEquals(answered, answered.failOldest("boom", at = 1))
    }
}
