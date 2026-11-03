package ai.openonion.whatcha

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PairingTest {

    @Test
    fun findsOurEntryAmongOtherSigningBlockPairs() {
        val apk = fakeApk(listOf(0x7109871a to ByteArray(40) { 7 }, PAIRING_BLOCK_ID to "hello".toByteArray()))
        assertArrayEquals("hello".toByteArray(), readSigningBlockEntry(apk, PAIRING_BLOCK_ID))
    }

    @Test
    fun anApkThatWasNeverPackedHasNoPairing() {
        val apk = fakeApk(listOf(0x7109871a to ByteArray(40) { 7 }))
        assertNull(readSigningBlockEntry(apk, PAIRING_BLOCK_ID))
    }

    /** Entries, then a signing block holding [pairs], then a one-byte central directory, then the EOCD. */
    private fun fakeApk(pairs: List<Pair<Int, ByteArray>>): ByteArray {
        val entries = ByteArray(100)
        val body = pairs.sumOf { 12 + it.second.size }
        val blockSize = body + 24L
        val centralDirectory = entries.size + 8 + body + 24
        val buf = ByteBuffer.allocate(centralDirectory + 1 + 22).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(entries).putLong(blockSize)
        pairs.forEach { (id, value) -> buf.putLong(value.size + 4L).putInt(id).put(value) }
        buf.putLong(blockSize).put("APK Sig Block 42".toByteArray())
        buf.put(0)
        buf.putInt(0x06054b50).putInt(0).putShort(0).putShort(0).putInt(1).putInt(centralDirectory).putShort(0)
        return buf.array()
    }
}
