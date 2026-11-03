package ai.openonion.whatcha

import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceTest {
    @Test
    fun aVoiceBubbleShowsItsLengthTheWayWeChatDoes() {
        assertEquals("3″", voiceLabel(3))
    }

    @Test
    fun aLongerVoiceMessageHasAWiderBubbleUpToALimit() {
        assertTrue(voiceBubbleWidth(1) < voiceBubbleWidth(10))
        assertEquals(voiceBubbleWidth(60), voiceBubbleWidth(120))
        assertTrue(voiceBubbleWidth(60) <= 230.dp)
    }

    @Test
    fun aPressShorterThanASecondIsNotSent() {
        assertFalse(worthSending(millis = 600))
        assertTrue(worthSending(millis = 1_000))
    }

    @Test
    fun theAgentIsToldItIsAVoiceMessageAndHowLong() {
        assertEquals("[语音 3秒]", voicePrompt(3))
    }

    @Test
    fun aConversationSavedBeforeVoiceExistedStillLoads() {
        val lines = Json.decodeFromString<List<Line>>("""[{"fromParent":true,"text":"吃了吗","at":1}]""")
        assertNull(lines.single().voice)
    }
}
