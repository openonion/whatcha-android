package ai.openonion.whatcha

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/** One WebSocket to the agent through the relay. Reports what happened; the caller decides what to show. */
class AgentLink(
    private val pairing: Pairing,
    private val wire: Wire,
    private val listener: Listener,
    // Saved by the caller, so a relaunched app picks up the session its unanswered message went into.
    private var sessionId: String? = null,
    private val dispatch: (() -> Unit) -> Unit = { it() },
) : WebSocketListener() {

    interface Listener {
        /** [running]: the agent is still working on this session, so its reply is still coming. */
        fun onConnected(sessionId: String, running: Boolean, history: List<Pair<Boolean, String>>)
        fun onProfile(name: String)
        fun onReply(text: String)
        fun onError(message: String)
        /** The host no longer knows this phone (it restarted); CONNECT has been sent again. */
        fun onRejected()
        /** The host will not take this phone's invite code (wrong, expired, used, or absent); nothing more is sent. */
        fun onRefused()
        fun onDisconnected(reason: String)
    }

    private val client = OkHttpClient.Builder().pingInterval(30, TimeUnit.SECONDS).build()
    private var socket: WebSocket? = null
    @Volatile private var closed = false

    fun open() {
        socket = client.newWebSocket(Request.Builder().url("${pairing.relay}/ws/input").build(), this)
    }

    /** Gives up on the socket now; OkHttp then calls onFailure, so the caller hears onDisconnected as for any failure. */
    fun drop() {
        socket?.cancel()
    }

    /** A replaced link must not report a late refusal or disconnect to the new pairing. */
    fun close() {
        closed = true
        socket?.cancel()
    }

    private fun report(event: Listener.() -> Unit) = dispatch { if (!closed) listener.event() }

    /** False when there is no open socket; the caller keeps the message and retries after reconnecting. */
    fun send(prompt: String, voice: Voice?, photo: ByteArray?): Boolean = socket?.send(wire.input(prompt, voice, photo)) ?: false

    override fun onOpen(webSocket: WebSocket, response: Response) {
        if (closed) return
        webSocket.send(wire.connect(sessionId))
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        if (closed) return
        val frame = Json.parseToJsonElement(text).jsonObject
        when (frame.string("type")) {
            "CONNECTED" -> {
                val connectedSession = frame.string("session_id") ?: sessionId!!
                sessionId = connectedSession
                report { onConnected(connectedSession, frame.string("status") == "running", frame.history()) }
            }
            "ONBOARD_REQUIRED" -> onboard(webSocket)
            "AGENT_PROFILE" -> frame.string("name")?.let { report { onProfile(it) } }
            "PING" -> webSocket.send(wire.pong())
            "OUTPUT" -> report { onReply(frame.string("result").orEmpty()) }
            "ERROR" -> onError(webSocket, frame.string("message").orEmpty())
        }
    }

    // The agent has not met this phone yet: present the copied ticket token or legacy packed invite.
    private fun onboard(webSocket: WebSocket) {
        val invite = pairing.invite ?: return refused(webSocket)
        webSocket.send(wire.onboard(invite))
    }

    // The relay keeps a refused socket open, so drop it here: onFailure then schedules the retry that picks up a corrected host.
    private fun refused(webSocket: WebSocket) {
        report { onRefused() }
        webSocket.cancel()
    }

    private fun onError(webSocket: WebSocket, message: String) {
        if (message.startsWith(INVITE_REFUSED)) return refused(webSocket)
        if (!message.startsWith(HOST_FORGOT_US)) return report { onError(message) }
        webSocket.send(wire.connect(sessionId))
        report { onRejected() }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        if (closed) return
        socket = null
        report { onDisconnected(t.toString()) }
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        if (closed) return
        socket = null
        report { onDisconnected("closed $code $reason") }
    }
}

// What a Host answers to INPUT on a connection that has not sent CONNECT, e.g. after the Host restarted.
private const val HOST_FORGOT_US = "authenticate first"

// What a Host answers to ONBOARD_SUBMIT with a code it does not accept (connectonion network/trust/ws_admin.py).
private const val INVITE_REFUSED = "Invalid invite code"

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.content

// The parent's questions and 小乐's answers from CONNECTED's chat_items, in order; tool and thinking items are left out.
// A question with a picture has a list for content, which reads as "" and so never matches a waiting line.
private fun JsonObject.history(): List<Pair<Boolean, String>> =
    this["chat_items"]?.jsonArray.orEmpty().map { it.jsonObject }
        .filter { it.string("type") == "user" || it.string("type") == "agent" }
        .map { (it.string("type") == "user") to (it["content"] as? JsonPrimitive)?.content.orEmpty() }
