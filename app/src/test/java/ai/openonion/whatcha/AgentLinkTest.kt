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
        var cancelled = false
        override fun send(text: String): Boolean { frames += text; return true }
        override fun send(bytes: ByteString) = true
        override fun close(code: Int, reason: String?) = true
        override fun cancel() { cancelled = true }
        override fun queueSize() = 0L
        override fun request(): Request = Request.Builder().url("https://relay.test").build()
    }

    private class Heard : AgentLink.Listener {
        val events = mutableListOf<String>()
        var history: List<Pair<Boolean, String>>? = null
        override fun onConnected(sessionId: String, running: Boolean, history: List<Pair<Boolean, String>>) {
            events += "connected $sessionId running=$running"
            this.history = history
        }
        override fun onProfile(name: String) {}
        override fun onReply(text: String) {}
        override fun onError(message: String) { events += "error" }
        override fun onRejected() { events += "rejected" }
        override fun onRefused() { events += "refused" }
        override fun onDisconnected(reason: String) { events += "disconnected" }
    }

    private val me = Identity(Ed25519PrivateKeyParameters(ByteArray(32) { it.toByte() }, 0))
    private val heard = Heard()
    private val link = AgentLink(Pairing(agent = "0xagent"), Wire(me, "0xagent", now = { 1L }), heard, sessionId = "saved")

    @Test
    fun aReplacedLinkCannotChangeTheNewPairingsState() {
        link.close()
        val socket = Sent()
        link.onMessage(socket, """{"type":"CONNECTED","session_id":"old"}""")
        link.onMessage(socket, """{"type":"ERROR","message":"Invalid invite code"}""")
        link.onFailure(socket, IllegalStateException("old socket"), null)
        link.onClosed(socket, 1000, "old socket")
        assertEquals(emptyList<String>(), heard.events)
    }

    @Test
    fun replacingALinkDiscardsEventsAlreadyWaitingForTheMainThread() {
        val pending = mutableListOf<() -> Unit>()
        val old = AgentLink(Pairing(agent = "old"), Wire(me, "old"), heard, dispatch = { pending.add(it) })
        val socket = Sent()
        old.onMessage(socket, """{"type":"CONNECTED","session_id":"old"}""")
        old.onMessage(socket, """{"type":"ERROR","message":"Invalid invite code"}""")
        old.onFailure(socket, IllegalStateException("old socket"), null)
        assertEquals(3, pending.size)
        old.close()
        pending.forEach { it() }
        assertEquals(emptyList<String>(), heard.events)
    }

    @Test
    fun aRelaunchedAppResumesTheSessionItSavedSoTheHostCanFinishTheReply() {
        val socket = Sent()
        link.onOpen(socket, okhttp3.Response.Builder().request(socket.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(101).message("").build())
        val connect = Json.parseToJsonElement(socket.frames.single()).jsonObject
        assertEquals("saved", connect["session_id"]!!.jsonPrimitive.content)
    }

    @Test
    fun connectedSaysWhichSessionAndWhetherTheAgentIsStillWorking() {
        link.onMessage(Sent(), """{"type":"CONNECTED","session_id":"s1","status":"running"}""")
        link.onMessage(Sent(), """{"type":"CONNECTED","session_id":"s1","status":"connected"}""")
        assertEquals(listOf("connected s1 running=true", "connected s1 running=false"), heard.events)
    }

    @Test
    fun aHostThatForgotUsGetsCONNECTAgainAndTheRejectedMessageIsReported() {
        val socket = Sent()
        link.onMessage(socket, """{"type":"ERROR","message":"authenticate first (send CONNECT)"}""")
        assertEquals(listOf("rejected"), heard.events)
        assertEquals("CONNECT", Json.parseToJsonElement(socket.frames.single()).jsonObject["type"]!!.jsonPrimitive.content)
    }

    // The host's exact text: connectonion 1.9.3rc41 network/trust/ws_admin.py:101 sends
    // {"type": "ERROR", "message": "Invalid invite code"} and prints "✗ Invalid invite code from 0x…" (:98).
    // The relay keeps the refused socket open (seen 80 s+), so the link drops it itself and the 5 s retry can run.
    @Test
    fun aRefusedInviteCodeIsReportedAsRefusedAndTheSocketDropped() {
        val socket = Sent()
        link.onMessage(socket, """{"type":"ERROR","message":"Invalid invite code"}""")
        assertEquals(listOf("refused"), heard.events)
        assertEquals(true, socket.cancelled)
    }

    @Test
    fun anAPKPackedWithoutAnInviteCodeIsRefusedWithoutSendingAnything() {
        val bare = AgentLink(Pairing(agent = "0xagent", invite = null), Wire(me, "0xagent", now = { 1L }), heard)
        val socket = Sent()
        bare.onMessage(socket, """{"type":"ONBOARD_REQUIRED"}""")
        assertEquals(listOf("refused"), heard.events)
        assertEquals(emptyList<String>(), socket.frames)
        assertEquals(true, socket.cancelled)
    }

    @Test
    fun anAPKPackedWithAnInviteCodePresentsIt() {
        val packed = AgentLink(Pairing(agent = "0xagent", invite = "whatcha-e2e-9c1d"), Wire(me, "0xagent", now = { 1L }), heard)
        val socket = Sent()
        packed.onMessage(socket, """{"type":"ONBOARD_REQUIRED"}""")
        val frame = Json.parseToJsonElement(socket.frames.single()).jsonObject
        assertEquals("ONBOARD_SUBMIT", frame["type"]!!.jsonPrimitive.content)
        assertEquals("whatcha-e2e-9c1d", frame["payload"]!!.jsonObject["invite_code"]!!.jsonPrimitive.content)
        assertEquals(emptyList<String>(), heard.events)
    }

    @Test
    fun anyOtherErrorStillReachesTheListener() {
        link.onMessage(Sent(), """{"type":"ERROR","message":"Agent completed without result"}""")
        assertEquals(listOf("error"), heard.events)
    }

    // connectonion establish_connection sends server_newer, session and chat_items when the stored session is newer.
    @Test
    fun connectedCarriesTheConversation() {
        link.onMessage(Sent(), """{"type":"CONNECTED","session_id":"s1","status":"connected","server_newer":true,"session":{},
            "chat_items":[{"id":"msg-0","type":"user","content":"q"},{"id":"t","type":"tool_call","name":"x"},{"id":"msg-2","type":"agent","content":"a"}]}""")
        assertEquals(listOf(true to "q", false to "a"), heard.history)
        link.onMessage(Sent(), """{"type":"CONNECTED","session_id":"s1","status":"connected"}""")
        assertEquals(emptyList<Pair<Boolean, String>>(), heard.history)
    }
}
