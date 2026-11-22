package ai.openonion.whatcha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmptyHintTest {
    @Test
    fun anEmptyChatTellsTheParentWhatToDo() {
        assertEquals("想知道孩子在干嘛，\n就在下面问小乐，\n打字说话都行", emptyHint("小乐", emptyList(), Status.ONLINE))
    }

    @Test
    fun theHintGoesOnceThereIsAConversation() {
        assertNull(emptyHint("小乐", listOf(Line(fromParent = true, text = "在干嘛", at = 0)), Status.ONLINE))
    }

    @Test
    fun anUnpairedEmptyChatExplainsCopyAndReturn() {
        assertEquals("请孩子在微信里发来口令，\n长按复制，再回到这里", emptyHint("小乐", emptyList(), Status.UNPAIRED))
        assertEquals("请孩子在微信里发来口令，\n长按复制，再回到这里", emptyHint("小乐", emptyList(), Status.REFUSED))
    }

    @Test
    fun anUnpairedChatWithHistoryShowsTheHistoryAndNoHint() {
        assertNull(emptyHint("小乐", listOf(Line(fromParent = true, text = "在干嘛", at = 0)), Status.UNPAIRED))
    }
}
