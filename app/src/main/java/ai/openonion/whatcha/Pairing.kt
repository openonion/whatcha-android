package ai.openonion.whatcha

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Which agent this phone talks to. The parent never types it: `tools/pack.py`
 * writes it into the APK's signing block, and the first launch reads it from
 * the installed APK and keeps it.
 */
@Serializable
data class Pairing(
    val agent: String,
    val relay: String = "wss://oo.openonion.ai",
    val invite: String? = null,
)

/** ID of our entry in the APK Signing Block. ASCII "what". Must match tools/pack.py. */
const val PAIRING_BLOCK_ID = 0x77686174

private const val SIGNING_BLOCK_MAGIC = "APK Sig Block 42"
private const val EOCD_SIGNATURE = 0x06054b50

fun loadPairing(context: Context): Pairing? {
    val prefs = context.getSharedPreferences("pairing", Context.MODE_PRIVATE)
    val saved = prefs.getString("pairing", null)
    if (saved != null) return Json.decodeFromString<Pairing>(saved)
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
