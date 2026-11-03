package ai.openonion.whatcha

import android.content.Context
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.SecureRandom

/**
 * The phone's Ed25519 identity. Generated silently on first launch; the parent
 * never sees it. The address is the public key, which is what the agent's Host
 * verifies every CONNECT against.
 */
class Identity(private val key: Ed25519PrivateKeyParameters) {

    val address: String = "0x" + key.generatePublicKey().encoded.toHex()

    fun sign(message: ByteArray): String {
        val signer = Ed25519Signer()
        signer.init(true, key)
        signer.update(message, 0, message.size)
        return signer.generateSignature().toHex()
    }

    companion object {
        fun load(context: Context): Identity {
            val prefs = context.getSharedPreferences("identity", Context.MODE_PRIVATE)
            val seed = prefs.getString("seed", null)
            if (seed != null) return Identity(Ed25519PrivateKeyParameters(seed.hexToBytes(), 0))
            val key = Ed25519PrivateKeyParameters(SecureRandom())
            prefs.edit().putString("seed", key.encoded.toHex()).apply()
            return Identity(key)
        }
    }
}

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
