package one.tunkshif.swirlmusic.ui.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import one.tunkshif.swirlmusic.ui.components.LocalArtworkPainter
import one.tunkshif.swirlmusic.ui.theme.SwirlMusicTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Home goldens. `LocalArtworkPainter` replaces Coil so the images are a flat colour: a golden must
 * not depend on a network response or on Coil's timing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class HomeScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun content_light() = capture("content-light", HomeTestData.content, dark = false)

    @Test
    fun content_dark() = capture("content-dark", HomeTestData.content, dark = true)

    @Test
    fun loading_light() = capture("loading-light", HomeTestData.loading, dark = false)

    @Test
    fun error_light() = capture("error-light", HomeTestData.error, dark = false)

    @Test
    fun empty_light() = capture("empty-light", HomeTestData.empty, dark = false)

    private fun capture(name: String, state: HomeUiState, dark: Boolean) {
        compose.setContent {
            CompositionLocalProvider(LocalArtworkPainter provides ColorPainter(Color(0xFF9AA0C8))) {
                SwirlMusicTheme(darkTheme = dark, dynamicColor = false) {
                    HomeScreen(
                        nickname = "tunkshif",
                        state = state,
                        onRefresh = {},
                        onRetry = {},
                        now = HomeTestData.evening,
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/home/$name.png")
    }
}
