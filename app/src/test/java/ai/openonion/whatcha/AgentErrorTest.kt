package ai.openonion.whatcha

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentErrorTest {
    private val waiting = listOf(Line(fromParent = true, text = "在干嘛", at = 0, delivery = Delivery.SENT))

    @Test
    fun anErrorMarksTheOldestWaitingMessageFailed() {
        val lines = waiting.failOldest("credit exhausted")
        assertEquals(1, lines.size)
        assertEquals(Delivery.FAILED, lines.first().delivery)
    }

    @Test
    fun theRelaysEmptyErrorsWhileTheHostIsDownChangeNothing() {
        assertEquals(waiting, waiting.failOldest(""))
    }

    @Test
    fun anErrorWithNothingWaitingChangesNothing() {
        val answered = waiting.answerOldest()
        assertEquals(answered, answered.failOldest("boom"))
    }

    // 11 characters a line at the largest font (9g, measured at 2.0): 31 characters end on a whole 9-character line,
    // the 35 with 「消息旁边」 left a lone 「次。」 on a fourth line.
    @Test
    fun theFailedNoteEndsOnAWholeLineAtTheLargestFont() {
        assertEquals(31, AGENT_FAILED.length)
        assertEquals(false, AGENT_FAILED.contains("消息旁边"))
    }

    @Test
    fun anOldHistorysStoredNoteIsDroppedOnLoad() {
        val saved = """[{"fromParent":true,"text":"在干嘛","at":0,"delivery":"FAILED"},""" +
            """{"fromParent":false,"text":"$AGENT_FAILED","at":1,"note":true}]"""
        val lines = Json.decodeFromString<List<Line>>(saved).withoutNotes()
        assertEquals(1, lines.size)
        assertEquals(Delivery.FAILED, lines.first().delivery)
    }
}
