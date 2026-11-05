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
    fun anUnpairedEmptyChatTellsTheParentToCallTheChild() {
        assertEquals("给孩子打个电话", emptyHint("小乐", emptyList(), Status.UNPAIRED))
    }

    @Test
    fun anUnpairedChatWithHistoryShowsTheHistoryAndNoHint() {
        assertNull(emptyHint("小乐", listOf(Line(fromParent = true, text = "在干嘛", at = 0)), Status.UNPAIRED))
    }
}
