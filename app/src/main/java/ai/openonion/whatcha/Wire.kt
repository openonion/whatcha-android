package ai.openonion.whatcha

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Base64
import java.util.UUID

/**
 * Builds the frames this app sends to a ConnectOnion Host (OIP 0.1).
 *
 * CONNECT asks for signed commands, so every later command carries its own
 * signed payload with a type and a nonce. The Host verifies signatures over
 * the payload serialised the way Python's json.dumps(sort_keys=True,
 * separators=(",", ":")) does; [canonical] produces those bytes.
 */
class Wire(private val me: Identity, private val agent: String, private val now: () -> Long = ::epochSeconds) {

    fun connect(sessionId: String?): String {
        val payload = buildJsonObject {
            put("to", agent)
            put("timestamp", now())
            put("signed_commands", 1)
        }
        return buildJsonObject {
            put("type", "CONNECT")
            put("timestamp", now())
            put("to", agent)
            if (sessionId != null) put("session_id", sessionId)
            put("payload", payload)
            put("from", me.address)
            put("signature", sign(payload))
        }.toString()
    }

    fun input(prompt: String, voice: Voice? = null, photo: ByteArray? = null): String = command(buildJsonObject {
        put("type", "INPUT")
        put("input_id", UUID.randomUUID().toString())
        put("prompt", prompt)
        // The host saves each file under .co/uploads/ and tells the agent its path.
        if (voice != null) put("files", buildJsonArray { add(voice.file()) })
        // An image goes into the agent's own eyes: the host puts it beside the prompt in the same message.
        if (photo != null) put("images", buildJsonArray { add("data:image/jpeg;base64," + Base64.getEncoder().encodeToString(photo)) })
    })

    fun onboard(inviteCode: String): String {
        val payload = buildJsonObject {
            put("timestamp", now())
            put("invite_code", inviteCode)
            put("to", agent)
        }
        return buildJsonObject {
            put("type", "ONBOARD_SUBMIT")
            put("payload", payload)
            put("from", me.address)
            put("signature", sign(payload))
        }.toString()
    }

    fun pong(): String = """{"type":"PONG"}"""

    private fun command(message: JsonObject): String {
        val signed = JsonObject(message + mapOf(
            "timestamp" to JsonPrimitive(now()),
            "nonce" to JsonPrimitive(UUID.randomUUID().toString()),
            "to" to JsonPrimitive(agent),
        ))
        return JsonObject(signed + mapOf(
            "payload" to signed,
            "from" to JsonPrimitive(me.address),
            "signature" to JsonPrimitive(sign(signed)),
        )).toString()
    }

    private fun sign(payload: JsonObject): String = me.sign(canonical(payload).toByteArray())
}

/** A recorded voice message as the host takes a file: a name, and the bytes as a base64 data URL. */
class Voice(val name: String, val sound: ByteArray) {
    fun file(): JsonObject = buildJsonObject {
        put("name", name)
        put("data", "data:audio/mp4;base64," + Base64.getEncoder().encodeToString(sound))
    }
}

fun canonical(element: JsonElement): String = sortKeys(element).toString()

private fun sortKeys(element: JsonElement): JsonElement = when (element) {
    is JsonObject -> JsonObject(element.entries.sortedBy { it.key }.associate { it.key to sortKeys(it.value) })
    is JsonArray -> JsonArray(element.map(::sortKeys))
    else -> element
}

fun epochSeconds(): Long = System.currentTimeMillis() / 1000
