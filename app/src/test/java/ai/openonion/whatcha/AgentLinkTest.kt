package ai.openonion.whatcha

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import okhttp3.WebSocket
import okio.ByteString
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentLinkTest {

    private class Sent : WebSocket {
        val frames = mutableListOf<String>()
        override fun send(text: String): Boolean { frames += text; return true }
        override fun send(bytes: ByteString) = true
        override fun close(code: Int, reason: String?) = true
        override fun cancel() {}
        override fun queueSize() = 0L
        override fun request(): Request = Request.Builder().url("https://relay.test").build()
    }

    private class Heard : AgentLink.Listener {
        val events = mutableListOf<String>()
        override fun onConnected() { events += "connected" }
        override fun onProfile(name: String) {}
        override fun onReply(text: String) {}
        override fun onError(message: String) { events += "error" }
        override fun onRejected() { events += "rejected" }
        override fun onDisconnected(reason: String) {}
    }

    private val me = Identity(Ed25519PrivateKeyParameters(ByteArray(32) { it.toByte() }, 0))
    private val heard = Heard()
    private val link = AgentLink(Pairing(agent = "0xagent"), Wire(me, "0xagent", now = { 1L }), heard)

    @Test
    fun aHostThatForgotUsGetsCONNECTAgainAndTheRejectedMessageIsReported() {
        val socket = Sent()
        link.onMessage(socket, """{"type":"ERROR","message":"authenticate first (send CONNECT)"}""")
        assertEquals(listOf("rejected"), heard.events)
        assertEquals("CONNECT", Json.parseToJsonElement(socket.frames.single()).jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun anyOtherErrorStillReachesTheListener() {
        link.onMessage(Sent(), """{"type":"ERROR","message":"Agent completed without result"}""")
        assertEquals(listOf("error"), heard.events)
    }
}
