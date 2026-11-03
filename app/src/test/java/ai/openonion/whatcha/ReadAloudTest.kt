package ai.openonion.whatcha

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadAloudTest {
    @Test
    fun tappingTheReplyBeingReadStopsIt() {
        assertTrue(tapStops(speaking = "他在上班", tapped = "他在上班"))
    }

    @Test
    fun tappingAnotherReplyReadsThatOneInstead() {
        assertFalse(tapStops(speaking = "他在上班", tapped = "晚上七点到家"))
    }

    @Test
    fun tappingWhenNothingIsBeingReadReadsIt() {
        assertFalse(tapStops(speaking = null, tapped = "他在上班"))
    }
}
