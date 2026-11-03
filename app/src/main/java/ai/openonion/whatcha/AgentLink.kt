package ai.openonion.whatcha

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
) : WebSocketListener() {

    interface Listener {
        fun onConnected()
        fun onProfile(name: String)
        fun onReply(text: String)
        fun onError(message: String)
        fun onDisconnected(reason: String)
    }

    private val client = OkHttpClient.Builder().pingInterval(30, TimeUnit.SECONDS).build()
    private var socket: WebSocket? = null
    private var sessionId: String? = null

    fun open() {
        socket = client.newWebSocket(Request.Builder().url("${pairing.relay}/ws/input").build(), this)
    }

    /** False when there is no open socket; the caller keeps the message and retries after reconnecting. */
    fun send(prompt: String): Boolean = socket?.send(wire.input(prompt)) ?: false

    override fun onOpen(webSocket: WebSocket, response: Response) {
        webSocket.send(wire.connect(sessionId))
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        val frame = Json.parseToJsonElement(text).jsonObject
        when (frame.string("type")) {
            "CONNECTED" -> {
                sessionId = frame.string("session_id") ?: sessionId
                listener.onConnected()
            }
            // The agent has not met this phone yet: present the invite code packed into the APK.
            "ONBOARD_REQUIRED" -> webSocket.send(wire.onboard(pairing.invite!!))
            "AGENT_PROFILE" -> frame.string("name")?.let(listener::onProfile)
            "PING" -> webSocket.send(wire.pong())
            "OUTPUT" -> listener.onReply(frame.string("result").orEmpty())
            "ERROR" -> listener.onError(frame.string("message").orEmpty())
        }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        socket = null
        listener.onDisconnected(t.toString())
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        socket = null
        listener.onDisconnected("closed $code $reason")
    }
}

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.content
