package ai.openonion.whatcha

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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

    private fun verifies(message: String, signature: String): Boolean {
        val verifier = Ed25519Signer()
        verifier.init(false, Ed25519PublicKeyParameters(me.address.removePrefix("0x").hexToBytes(), 0))
        val bytes = message.toByteArray()
        verifier.update(bytes, 0, bytes.size)
        return verifier.verifySignature(signature.hexToBytes())
    }
}
