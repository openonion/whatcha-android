package ai.openonion.whatcha

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

/** Which agent this phone talks to; permanent once the host has accepted this phone. */
@Serializable
data class Pairing(
    val agent: String,
    val relay: String = "wss://oo.openonion.ai",
    val invite: String? = null,
    val accepted: Boolean = false,
)

internal fun canPastePairing(current: Pairing?, status: Status): Boolean =
    current?.accepted != true

internal fun pairingAfterPaste(current: Pairing?, status: Status, text: String): Pairing? =
    if (canPastePairing(current, status)) parsePairing(text) ?: current else current

internal fun pairingPasteHint(current: Pairing?, status: Status, text: String, fromButton: Boolean): String? = when {
    !fromButton || !canPastePairing(current, status) -> null
    pairingAfterPaste(current, status, text) != current -> null
    current != null && status == Status.REFUSED && parsePairing(text) == current -> "这条口令用过了，请孩子重新发一条"
    else -> "没找到口令。回微信长按孩子发的那条，点「复制」，再回来"
}

internal fun Pairing.afterConnected(): Pairing = copy(accepted = true)

/** Shared WC1 format: a 32-byte agent address followed by a 12-byte ticket token. */
fun parsePairing(text: String): Pairing? {
    val payload = Regex("#WC1\\.([A-Za-z0-9_-]{59})#").find(text)?.groupValues?.get(1) ?: return null
    val bytes = Base64.getUrlDecoder().decode(payload)
    return Pairing(
        agent = "0x" + bytes.take(32).joinToString("") { "%02x".format(it) },
        invite = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.copyOfRange(32, 44)),
    )
}

fun savePairing(context: Context, pairing: Pairing) {
    context.getSharedPreferences("pairing", Context.MODE_PRIVATE).edit()
        .putString("pairing", Json.encodeToString(pairing)).apply()
}

/** ID of our entry in the APK Signing Block. ASCII "what". Must match tools/pack.py. */
const val PAIRING_BLOCK_ID = 0x77686174

private const val SIGNING_BLOCK_MAGIC = "APK Sig Block 42"
private const val EOCD_SIGNATURE = 0x06054b50

internal fun migratedPairing(savedJson: String, linesJson: String?): Pairing {
    val pairing = Json.decodeFromString<Pairing>(savedJson)
    return if ("accepted" !in Json.parseToJsonElement(savedJson).jsonObject &&
        Json.decodeFromString<List<Line>>(linesJson ?: "[]").isNotEmpty()
    ) pairing.afterConnected() else pairing
}

fun loadPairing(context: Context): Pairing? {
    val prefs = context.getSharedPreferences("pairing", Context.MODE_PRIVATE)
    val saved = prefs.getString("pairing", null)
    if (saved != null) {
        val pairing = migratedPairing(
            saved,
            context.getSharedPreferences("chat", Context.MODE_PRIVATE).getString("lines", null),
        )
        if (pairing != Json.decodeFromString<Pairing>(saved)) {
            savePairing(context, pairing)
        }
        return pairing
    }
    val packed = readSigningBlockEntry(File(context.applicationInfo.sourceDir).readBytes(), PAIRING_BLOCK_ID)
        ?: return null
    val json = packed.decodeToString()
    prefs.edit().putString("pairing", json).apply()
    return Json.decodeFromString<Pairing>(json)
}

/**
 * Returns the value stored under [id] in the APK Signing Block, or null.
 *
 * Layout, from the end of the file: End of Central Directory (points at the
 * Central Directory) ← Central Directory ← signing block, which ends with
 * its size and a 16-byte magic. The block's ID-value pairs are not covered
 * by the APK signature, which is why an entry can be added without re-signing.
 */
fun readSigningBlockEntry(apk: ByteArray, id: Int): ByteArray? {
    val buf = ByteBuffer.wrap(apk).order(ByteOrder.LITTLE_ENDIAN)
    val eocd = (apk.size - 22 downTo 0).first { buf.getInt(it) == EOCD_SIGNATURE }
    val centralDirectory = buf.getInt(eocd + 16).toLong() and 0xffffffffL
    val magicStart = (centralDirectory - 16).toInt()
    if (magicStart < 8) return null
    if (String(apk, magicStart, 16, Charsets.US_ASCII) != SIGNING_BLOCK_MAGIC) return null

    val blockSize = buf.getLong(magicStart - 8)
    var pos = (centralDirectory - blockSize).toInt()  // first pair, just after the leading size field
    val end = magicStart - 8
    while (pos < end) {
        val length = buf.getLong(pos).toInt()
        if (buf.getInt(pos + 8) == id) return apk.copyOfRange(pos + 12, pos + 8 + length)
        pos += 8 + length
    }
    return null
}
