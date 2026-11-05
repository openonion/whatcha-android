package ai.openonion.whatcha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class RetryTest {
    private fun asked(text: String, delivery: Delivery) = Line(fromParent = true, text = text, at = 0, delivery = delivery)

    @Test
    fun tappingTheRedMarkQueuesTheMessageAgainInPlace() {
        val failed = asked("a", Delivery.FAILED)
        val lines = listOf(failed, asked("b", Delivery.DONE), asked("c", Delivery.SENT))
        val retried = lines.retry(failed)
        assertEquals(listOf(Delivery.QUEUED, Delivery.DONE, Delivery.SENT), retried.map { it.delivery })
        assertEquals("a", retried[0].text)
        assertSame(lines[1], retried[1])
        assertSame(lines[2], retried[2])
    }

    @Test
    fun onlyAFailedMessageCanBeRetried() {
        val done = asked("a", Delivery.DONE)
        val lines = listOf(done, asked("b", Delivery.QUEUED))
        assertEquals(lines, lines.retry(done))
    }

    @Test
    fun aSuccessfulResendLeavesNoNoteBehind() {
        val sent = asked("a", Delivery.SENT)
        val failed = listOf(sent).failOldest("boom")
        val retried = failed.retry(failed.first()).let { it.markSent(it.first()) }.answerOldest()
        assertEquals(1, retried.size)
        assertFalse(retried.any { it.note })
        assertEquals(Delivery.DONE, retried.first().delivery)
    }
}
