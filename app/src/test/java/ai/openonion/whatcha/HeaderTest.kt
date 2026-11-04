package ai.openonion.whatcha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun aFailedMessageIsNotBeingAnswered() {
        assertEquals("小乐", headerTitle("小乐", listOf(asked(Delivery.FAILED))))
    }

    // Each clause on its own line: at the largest system font a status sentence wrapped mid-word (自己|重试, 重新|发一个安装包).
    @Test
    fun theOfflineSentenceBreaksAtItsComma() {
        assertEquals("暂时联系不上，\n稍后会自己重试", statusText(Status.OFFLINE))
    }

    @Test
    fun theUnpairedSentenceBreaksIntoShortClauses() {
        assertEquals("还没有连上，\n请让孩子\n重新发一个安装包", statusText(Status.UNPAIRED))
    }

    @Test
    fun nothingToSayWhileOnline() {
        assertNull(statusText(Status.ONLINE))
        assertEquals("正在连接…", statusText(Status.CONNECTING))
    }
}
