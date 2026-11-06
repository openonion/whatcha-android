package ai.openonion.whatcha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun openingTheAppDuringAWaitTriesAtOnce() {
        assertTrue(retryNow(Status.OFFLINE, waiting = true))
    }

    @Test
    fun openingTheAppWithAWrongCodeWaitingTriesAtOnce() {
        assertTrue(retryNow(Status.UNPAIRED, waiting = true))
    }

    @Test
    fun aTryAlreadyUnderWayIsNotDoubled() {
        assertFalse(retryNow(Status.OFFLINE, waiting = false))
    }

    @Test
    fun aConnectedAppIsLeftAlone() {
        assertFalse(retryNow(Status.ONLINE, waiting = false))
    }

    @Test
    fun theFirstLaunchIsLeftAlone() {
        assertFalse(retryNow(Status.CONNECTING, waiting = false))
    }

    @Test
    fun comingBackWhileOfflineSaysConnecting() {
        assertEquals(Status.CONNECTING, shownOnReturn(Status.OFFLINE))
    }

    @Test
    fun comingBackWithAWrongCodeKeepsItsStrip() {
        assertEquals(Status.UNPAIRED, shownOnReturn(Status.UNPAIRED))
    }

    @Test
    fun comingBackOnlineChangesNothing() {
        assertEquals(Status.ONLINE, shownOnReturn(Status.ONLINE))
    }
}
