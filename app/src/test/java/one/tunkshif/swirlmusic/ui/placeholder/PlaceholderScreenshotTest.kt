package one.tunkshif.swirlmusic.ui.placeholder

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import one.tunkshif.swirlmusic.ui.theme.SwirlMusicTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config

/**
 * Proves the screenshot pipeline works before there is any real UI.
 *
 * Goldens live in `app/src/test/screenshots/`. Record with
 * `./gradlew :app:recordRoborazziDebug`, verify with `:app:verifyRoborazziDebug`.
 *
 * `dynamicColor = false` on purpose: a golden must not follow the machine's wallpaper.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class PlaceholderScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun light() = capture(darkTheme = false, path = "src/test/screenshots/placeholder/light.png")

    @Test
    fun dark() = capture(darkTheme = true, path = "src/test/screenshots/placeholder/dark.png")

    private fun capture(darkTheme: Boolean, path: String) {
        compose.setContent {
            SwirlMusicTheme(darkTheme = darkTheme, dynamicColor = false) {
                PlaceholderScreen(state = ApiCheckState.Idle, onCheckApi = {})
            }
        }
        compose.onRoot().captureRoboImage(path)
    }
}
