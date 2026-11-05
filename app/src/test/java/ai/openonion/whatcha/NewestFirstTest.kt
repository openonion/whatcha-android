package ai.openonion.whatcha

import org.junit.Assert.assertEquals
import org.junit.Test

class NewestFirstTest {
    private fun at(minute: Long) = Line(fromParent = true, text = "m$minute", at = minute * 60_000)

    // The list is laid out from the bottom, as WeChat's is, so the newest line must come first: then the end of
    // the latest reply stays in view when it is long and when the keyboard opens.
    @Test
    fun theNewestLineComesFirstAndKeepsItsTimeLabel() {
        val rows = newestFirst(listOf(at(0), at(1), at(10)))
        assertEquals(listOf("m10", "m1", "m0"), rows.map { it.line.text })
        assertEquals(listOf(true, false, true), rows.map { it.showTime })
    }
}
