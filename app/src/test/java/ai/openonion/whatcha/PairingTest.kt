package ai.openonion.whatcha

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Base64
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PairingTest {

    private val address = ByteArray(32) { it.toByte() }
    private val token = ByteArray(12) { (it + 200).toByte() }
    private val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(address + token)
    private val expected = Pairing(
        agent = "0x" + address.joinToString("") { "%02x".format(it) },
        invite = Base64.getUrlEncoder().withoutPadding().encodeToString(token),
    )

    @Test fun legacyChatIsAcceptedOnlyWhenHistoryExists() {
        val history = """[{"fromParent":true,"text":"在干嘛","at":1}]"""
        listOf(
            """{"agent":"old-agent","invite":"old-code"}""" to listOf(true, false, false),
            """{"agent":"old-agent","invite":"old-code","accepted":false}""" to listOf(false, false, false),
            """{"agent":"old-agent","invite":"old-code","accepted":true}""" to listOf(true, true, true),
        ).forEach { (saved, acceptedResults) ->
            val decoded = Json.decodeFromString<Pairing>(saved)
            listOf(history, "[]", null).zip(acceptedResults).forEach { (lines, accepted) ->
                assertEquals(decoded.copy(accepted = accepted), migratedPairing(saved, lines))
            }
        }
    }

    @Test fun aRefusedTicketCanBeReplacedImmediately() {
        val refused = expected.copy(invite = "expired")
        assertTrue(canPastePairing(refused, Status.REFUSED))
        assertEquals(expected, pairingAfterPaste(refused, Status.REFUSED, "#WC1.$payload#"))
    }

    @Test fun aRefusedPackedPairingCanBeReplacedToo() {
        val packed = Json.decodeFromString<Pairing>("""{"agent":"old-agent","invite":"old-code"}""")
        assertEquals(expected, pairingAfterPaste(packed, Status.REFUSED, "#WC1.$payload#"))
    }

    @Test fun anAcceptedPairingIsNeverReplacedEvenAfterRestart() {
        val accepted = expected.copy(invite = "original").afterConnected()
        assertTrue(accepted.accepted)
        val restored = Json.decodeFromString<Pairing>(Json.encodeToString(accepted))
        Status.entries.forEach { status ->
            assertFalse(canPastePairing(restored, status))
            assertEquals(restored, pairingAfterPaste(restored, status, "#WC1.$payload#"))
        }
    }

    @Test fun aLegacyPendingPairingCanBeReplacedUntilItsNextConnection() {
        val saved = Json.decodeFromString<Pairing>("""{"agent":"old-agent","relay":"wss://relay.example","invite":"old-code"}""")
        listOf(Status.CONNECTING, Status.OFFLINE, Status.ONLINE).forEach { status ->
            assertEquals(expected, pairingAfterPaste(saved, status, "#WC1.$payload#"))
        }
        assertEquals(saved, pairingAfterPaste(saved, Status.REFUSED, "ordinary clipboard text"))
        val accepted = saved.afterConnected()
        assertEquals(saved.copy(accepted = true), accepted)
        assertEquals(accepted, pairingAfterPaste(accepted, Status.REFUSED, "#WC1.$payload#"))
    }

    @Test fun aPendingPairingSurvivesRestartWithoutBecomingPermanent() {
        val restored = Json.decodeFromString<Pairing>(Json.encodeToString(expected))
        assertFalse(restored.accepted)
        assertEquals(expected, restored)
        assertEquals(expected, pairingAfterPaste(restored, Status.REFUSED, "#WC1.$payload#"))
        assertEquals(expected, pairingAfterPaste(restored, Status.REFUSED, "invalid"))
    }

    @Test fun anUnpairedPhoneCanPasteWithoutWaitingForRefusal() {
        assertEquals(expected, pairingAfterPaste(null, Status.UNPAIRED, "#WC1.$payload#"))
        assertNull(pairingAfterPaste(null, Status.UNPAIRED, "invalid"))
    }

    @Test fun everyPendingStatusCanTakeADifferentTicket() {
        val pending = expected.copy(invite = "unanswered")
        Status.entries.forEach { status ->
            assertTrue(canPastePairing(pending, status))
            assertEquals(expected, pairingAfterPaste(pending, status, "#WC1.$payload#"))
            assertEquals(pending, pairingAfterPaste(pending, status, "ordinary text"))
        }
    }

    @Test fun theSamePendingTicketDoesNotRestartPairing() {
        Status.entries.forEach { status ->
            assertEquals(expected, pairingAfterPaste(expected, status, "#WC1.$payload#"))
        }
    }

    @Test fun aButtonExplainsMissingOrdinaryAndPartialClipboardText() {
        listOf("", "ordinary WeChat message", "#WC1.${payload.dropLast(1)}#").forEach { text ->
            Status.entries.forEach { status ->
                assertEquals(
                    "没找到口令。回微信长按孩子发的那条，点「复制」，再回来",
                    pairingPasteHint(null, status, text, fromButton = true),
                )
                assertEquals(
                    "没找到口令。回微信长按孩子发的那条，点「复制」，再回来",
                    pairingPasteHint(expected, status, text, fromButton = true),
                )
            }
        }
    }

    @Test fun aButtonExplainsTheSameUsedTicket() {
        assertEquals(
            "这条口令用过了，请孩子重新发一条",
            pairingPasteHint(expected, Status.REFUSED, "#WC1.$payload#", fromButton = true),
        )
    }

    @Test fun theSameUnansweredTicketStillExplainsHowToCopyANewOne() {
        listOf(Status.CONNECTING, Status.OFFLINE).forEach { status ->
            assertEquals(
                "没找到口令。回微信长按孩子发的那条，点「复制」，再回来",
                pairingPasteHint(expected, status, "#WC1.$payload#", fromButton = true),
            )
        }
    }

    @Test fun aFreshTicketClearsThePasteHintInEveryPendingStatus() {
        val pending = expected.copy(invite = "unanswered")
        Status.entries.forEach { status ->
            assertNull(pairingPasteHint(pending, status, "#WC1.$payload#", fromButton = true))
        }
        assertNull(pairingPasteHint(null, Status.UNPAIRED, "#WC1.$payload#", fromButton = true))
    }

    @Test fun automaticFocusReadsNeverExplainClipboardFailures() {
        listOf("", "ordinary text", "#WC1.${payload.dropLast(1)}#", "#WC1.$payload#").forEach { text ->
            assertNull(pairingPasteHint(null, Status.UNPAIRED, text, fromButton = false))
            assertNull(pairingPasteHint(expected, Status.REFUSED, text, fromButton = false))
        }
    }

    @Test fun acceptedPairingsNeverShowAPasteHint() {
        Status.entries.forEach { status ->
            assertNull(pairingPasteHint(expected.afterConnected(), status, "", fromButton = true))
        }
    }

    @Test fun parsesTicket() = assertEquals(expected, parsePairing("#WC1.$payload#"))

    @Test fun findsTicketInWeChatText() = assertEquals(
        expected, parsePairing("孩子：\n「在干嘛」家人口令 #WC1.$payload#\n长按这条消息，点「复制」，再打开「在干嘛」\n转发"),
    )

    @Test fun refusesWrongLengths() {
        assertNull(parsePairing("#WC1.${payload.dropLast(1)}#"))
        assertNull(parsePairing("#WC1.${payload}A#"))
    }

    @Test fun refusesWrongPrefix() {
        assertNull(parsePairing("#WC2.$payload#"))
        assertNull(parsePairing("#wc1.$payload#"))
        assertNull(parsePairing("普通微信文字"))
    }

    @Test
    fun findsOurEntryAmongOtherSigningBlockPairs() {
        val apk = fakeApk(listOf(0x7109871a to ByteArray(40) { 7 }, PAIRING_BLOCK_ID to "hello".toByteArray()))
        assertArrayEquals("hello".toByteArray(), readSigningBlockEntry(apk, PAIRING_BLOCK_ID))
    }

    @Test
    fun anApkThatWasNeverPackedHasNoPairing() {
        val apk = fakeApk(listOf(0x7109871a to ByteArray(40) { 7 }))
        assertNull(readSigningBlockEntry(apk, PAIRING_BLOCK_ID))
    }

    /** Entries, then a signing block holding [pairs], then a one-byte central directory, then the EOCD. */
    private fun fakeApk(pairs: List<Pair<Int, ByteArray>>): ByteArray {
        val entries = ByteArray(100)
        val body = pairs.sumOf { 12 + it.second.size }
        val blockSize = body + 24L
        val centralDirectory = entries.size + 8 + body + 24
        val buf = ByteBuffer.allocate(centralDirectory + 1 + 22).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(entries).putLong(blockSize)
        pairs.forEach { (id, value) -> buf.putLong(value.size + 4L).putInt(id).put(value) }
        buf.putLong(blockSize).put("APK Sig Block 42".toByteArray())
        buf.put(0)
        buf.putInt(0x06054b50).putInt(0).putShort(0).putShort(0).putInt(1).putInt(centralDirectory).putShort(0)
        return buf.array()
    }
}
