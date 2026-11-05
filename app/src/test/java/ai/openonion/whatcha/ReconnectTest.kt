package ai.openonion.whatcha

import org.junit.Assert.assertEquals
import org.junit.Test

class ReconnectTest {
    @Test
    fun theWaitDoublesAfterEachFailedTry() {
        assertEquals(10_000L, nextDelay(5_000L))
        assertEquals(20_000L, nextDelay(10_000L))
    }

    @Test
    fun theWaitStopsAtAMinute() {
        assertEquals(60_000L, nextDelay(40_000L))
        assertEquals(60_000L, nextDelay(60_000L))
    }
}
