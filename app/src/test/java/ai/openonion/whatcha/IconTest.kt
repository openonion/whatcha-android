package ai.openonion.whatcha

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Parents find an app on the home screen by its picture, so the launcher must not fall back to Android's default.
class IconTest {
    private val manifest = File("src/main/AndroidManifest.xml").readText()

    @Test
    fun theAppNamesItsOwnIcon() {
        assertTrue(manifest.contains("""android:icon="@mipmap/ic_launcher""""))
        assertTrue(manifest.contains("""android:roundIcon="@mipmap/ic_launcher""""))
    }

    @Test
    fun theIconIsAnAdaptiveIconThatExists() {
        val icon = File("src/main/res/mipmap-anydpi/ic_launcher.xml")
        assertTrue(icon.exists() && icon.readText().contains("<adaptive-icon"))
    }
}
