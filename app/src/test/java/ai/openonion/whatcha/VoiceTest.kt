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

    private val day = 24 * 60 * 60 * 1_000L
    private val now = 100 * day
    private fun voice(delivery: Delivery, daysAgo: Int) =
        Line(fromParent = true, text = "[语音 3秒]", at = now - daysAgo * day, delivery = delivery, voice = "/v/$daysAgo.m4a", seconds = 3)

    @Test
    fun aVoiceOlderThanAMonthIsSweptOnceItIsDone() {
        assertEquals(listOf("/v/31.m4a"), listOf(voice(Delivery.DONE, 31)).staleVoices(now))
    }

    @Test
    fun aVoiceStillOnItsWayIsNeverSweptHoweverOld() {
        val waiting = listOf(voice(Delivery.QUEUED, 40), voice(Delivery.SENT, 40), voice(Delivery.FAILED, 40))
        assertEquals(emptyList<String>(), waiting.staleVoices(now))
    }

    @Test
    fun aRecentVoiceStays() {
        assertEquals(emptyList<String>(), listOf(voice(Delivery.DONE, 29)).staleVoices(now))
    }

    @Test
    fun aTextLineHasNoFileToSweep() {
        val text = Line(fromParent = true, text = "吃了吗", at = now - 40 * day, delivery = Delivery.DONE)
        assertEquals(emptyList<String>(), listOf(text).staleVoices(now))
    }

    @Test
    fun allowingTheMicrophoneSwitchesToHoldToTalk() {
        for (rationale in listOf(false, true)) for (saidNoBefore in listOf(false, true))
            assertEquals(MicStep.TALK, afterMicAnswer(granted = true, rationale = rationale, saidNoBefore = saidNoBefore))
    }

    @Test
    fun backOnTheFirstQuestionAsksAgainInsteadOfOpeningSettings() {
        assertEquals(MicStep.ASK_AGAIN, afterMicAnswer(granted = false, rationale = false, saidNoBefore = false))
    }

    @Test
    fun aFirstNoIsAskedAgainNextTime() {
        assertEquals(MicStep.ASK_AGAIN, afterMicAnswer(granted = false, rationale = true, saidNoBefore = false))
    }

    @Test
    fun aNoAfterAnEarlierNoThatTheSystemWillStillAskIsAskedAgain() {
        assertEquals(MicStep.ASK_AGAIN, afterMicAnswer(granted = false, rationale = true, saidNoBefore = true))
    }

    @Test
    fun aSecondNoShowsWhereToTap() {
        assertEquals(MicStep.OPEN_SETTINGS, afterMicAnswer(granted = false, rationale = false, saidNoBefore = true))
    }

    @Test
    fun theDialogSaysWhereToTapInThePhonesOwnWords() {
        assertTrue(MIC_SETTINGS_STEPS.contains("录音"))
        assertTrue(MIC_SETTINGS_STEPS.contains("权限 →\n麦克风 → 允许"))
        assertTrue(MIC_ASK_AGAIN.contains("录音"))
        assertFalse(MIC_ASK_AGAIN.contains("麦克风"))
    }

    @Test
    fun theDialogLinesStayWholeAtTheLargestFont() {
        // At font 2.0 the 272dp body holds 8 full-width characters; count a space or an arrow as one to be safe.
        for (line in MIC_SETTINGS_STEPS.lines()) assertTrue(line, line.length <= 8)
        assertTrue(MIC_SETTINGS_STEPS.contains("按到聊天出来"))
    }
}
