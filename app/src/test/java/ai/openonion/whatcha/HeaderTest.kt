package ai.openonion.whatcha

import org.junit.Assert.assertEquals
import org.junit.Test

class HeaderTest {
    private fun asked(delivery: Delivery) = Line(fromParent = true, text = "在干嘛", at = 0, delivery = delivery)

    @Test
    fun whileTheAgentWorksOnAnAnswerTheTitleSaysSoLikeWeChat() {
        assertEquals("对方正在输入...", headerTitle("小乐", listOf(asked(Delivery.SENT))))
    }

    @Test
    fun otherwiseTheTitleIsTheAgentsName() {
        assertEquals("小乐", headerTitle("小乐", listOf(asked(Delivery.DONE))))
        assertEquals("小乐", headerTitle("小乐", listOf(asked(Delivery.QUEUED))))
        assertEquals("小乐", headerTitle("小乐", emptyList()))
    }
}
