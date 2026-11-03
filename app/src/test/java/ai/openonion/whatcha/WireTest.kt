package ai.openonion.whatcha

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class WireTest {

    private val me = Identity(Ed25519PrivateKeyParameters(ByteArray(32) { it.toByte() }, 0))
    private val wire = Wire(me, agent = "0xagent", now = { 1_700_000_000L })

    @Test
    fun canonicalMatchesPythonSortedCompactJson() {
        // json.dumps({"to":"0xa","prompt":"吃了吗","timestamp":1}, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
        val element = Json.parseToJsonElement("""{"to":"0xa","prompt":"吃了吗","timestamp":1}""")
        assertEquals("""{"prompt":"吃了吗","timestamp":1,"to":"0xa"}""", canonical(element))
    }

    @Test
    fun inputCarriesASignedPayloadTheHostCanVerify() {
        val frame = Json.parseToJsonElement(wire.input("最近忙啥呢")).jsonObject
        val payload = frame["payload"]!!.jsonObject
        assertEquals("INPUT", payload["type"]!!.jsonPrimitive.content)
        assertEquals("0xagent", payload["to"]!!.jsonPrimitive.content)
        assertEquals(me.address, frame["from"]!!.jsonPrimitive.content)
        assertTrue(verifies(canonical(payload), frame["signature"]!!.jsonPrimitive.content))
    }

    @Test
    fun aVoiceMessageTravelsAsASignedFileTheHostSavesForTheAgent() {
        val sound = byteArrayOf(0, 1, 2, -1)
        val frame = Json.parseToJsonElement(wire.input("[语音 3秒]", Voice("voice.m4a", sound))).jsonObject
        val payload = frame["payload"]!!.jsonObject
        val file = payload["files"]!!.jsonArray.single().jsonObject
        assertEquals("voice.m4a", file["name"]!!.jsonPrimitive.content)
        val data = file["data"]!!.jsonPrimitive.content
        assertEquals("data:audio/mp4;base64,", data.substringBefore(",") + ",")
        assertArrayEquals(sound, Base64.getDecoder().decode(data.substringAfter(",")))
        assertTrue(verifies(canonical(payload), frame["signature"]!!.jsonPrimitive.content))
    }

    @Test
    fun aTypedMessageCarriesNoFiles() {
        val payload = Json.parseToJsonElement(wire.input("吃了吗")).jsonObject["payload"]!!.jsonObject
        assertFalse("files" in payload)
    }

    private fun verifies(message: String, signature: String): Boolean {
        val verifier = Ed25519Signer()
        verifier.init(false, Ed25519PublicKeyParameters(me.address.removePrefix("0x").hexToBytes(), 0))
        val bytes = message.toByteArray()
        verifier.update(bytes, 0, bytes.size)
        return verifier.verifySignature(signature.hexToBytes())
    }
}
