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

    @Test
    fun anOldHistorysStoredNoteIsDroppedOnLoad() {
        val saved = """[{"fromParent":true,"text":"在干嘛","at":0,"delivery":"FAILED"},""" +
            """{"fromParent":false,"text":"$AGENT_FAILED","at":1,"note":true}]"""
        val lines = Json.decodeFromString<List<Line>>(saved).withoutNotes()
        assertEquals(1, lines.size)
        assertEquals(Delivery.FAILED, lines.first().delivery)
    }
}
