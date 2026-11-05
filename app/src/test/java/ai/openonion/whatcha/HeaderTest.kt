package ai.openonion.whatcha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeaderTest {
    private fun asked(delivery: Delivery) = Line(fromParent = true, text = "在干嘛", at = 0, delivery = delivery)

    @Test
    fun whileTheAgentWorksOnAnAnswerTheTitleSaysSoLikeWeChat() {
        assertEquals("对方正在输入...", headerTitle("小乐", listOf(asked(Delivery.SENT)), Status.ONLINE))
    }

    @Test
    fun otherwiseTheTitleIsTheAgentsName() {
        assertEquals("小乐", headerTitle("小乐", listOf(asked(Delivery.DONE)), Status.ONLINE))
        assertEquals("小乐", headerTitle("小乐", listOf(asked(Delivery.QUEUED)), Status.ONLINE))
        assertEquals("小乐", headerTitle("小乐", emptyList(), Status.ONLINE))
        assertEquals("小乐", headerTitle("小乐", emptyList(), Status.OFFLINE))
    }

    @Test
    fun aFailedMessageIsNotBeingAnswered() {
        assertEquals("小乐", headerTitle("小乐", listOf(asked(Delivery.FAILED)), Status.ONLINE))
    }

    // WeChat's cold launch says 「微信(连接中…)」 in the title and shows nothing red; a reply on its way still wins.
    @Test
    fun whileConnectingTheTitleSaysSoLikeWeChat() {
        assertEquals("小乐(连接中…)", headerTitle("小乐", emptyList(), Status.CONNECTING))
        assertEquals("对方正在输入...", headerTitle("小乐", listOf(asked(Delivery.SENT)), Status.CONNECTING))
    }

    // The strip under the title says 暂时联系不上; a title saying the other side is typing would contradict it.
    @Test
    fun nobodyIsTypingWhileTheMacCannotBeReached() {
        assertEquals("小乐", headerTitle("小乐", listOf(asked(Delivery.SENT)), Status.OFFLINE))
    }

    // Each clause on its own line: at the largest system font a status sentence wrapped mid-word (自己|重试, 重新|发一个安装包).
    @Test
    fun theOfflineSentenceBreaksAtItsComma() {
        assertEquals("暂时联系不上，\n稍后会自己重试", statusText(Status.OFFLINE))
    }

    // Two clauses, the second eight characters: at the largest font the text column beside the 36dp ! is 315dp,
    // and nine characters of 35dp wrapped the last one onto a third line. Two lines at both scales, not a poem.
    @Test
    fun theUnpairedSentenceBreaksIntoShortClauses() {
        assertEquals("还没连上，\n让孩子重发安装包", statusText(Status.UNPAIRED))
    }

    // The strip is only for trouble, as WeChat's is only for 网络不可用; connecting is said in the title.
    @Test
    fun nothingToSayWhileOnline() {
        assertNull(statusText(Status.ONLINE))
        assertNull(statusText(Status.CONNECTING))
    }
}
