package ai.openonion.whatcha

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotoTest {
    @Test
    fun aPhonePhotoIsDecodedAtAQuarterSoItsLongestSideFitsIn1280() {
        assertEquals(4, sampleSize(4000, 3000))
        assertEquals(4, sampleSize(3000, 4000))
    }

    @Test
    fun aSmallPictureIsDecodedAsItIs() {
        assertEquals(1, sampleSize(800, 600))
    }

    @Test
    fun theAgentIsToldItIsAPhoto() {
        assertEquals("[照片]", photoPrompt())
    }

    @Test
    fun aConversationSavedBeforePhotosExistedStillLoads() {
        val lines = Json.decodeFromString<List<Line>>("""[{"fromParent":true,"text":"吃了吗","at":1}]""")
        assertNull(lines.single().photo)
    }
}
