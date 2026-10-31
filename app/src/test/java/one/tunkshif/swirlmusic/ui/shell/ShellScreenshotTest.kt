package one.tunkshif.swirlmusic.ui.shell

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import one.tunkshif.swirlmusic.data.model.UserProfile
import one.tunkshif.swirlmusic.navigation.TopLevelDestination
import one.tunkshif.swirlmusic.ui.home.HomeScreen
import one.tunkshif.swirlmusic.ui.home.HomeTestData
import one.tunkshif.swirlmusic.ui.theme.SwirlMusicTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import one.tunkshif.swirlmusic.ui.components.LocalArtworkPainter
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screen-level goldens for the shell: every tab in both themes, plus two wider windows.
 *
 * `dynamicColor = false`: a golden must not follow the machine's wallpaper.
 * Record with `./gradlew :app:recordRoborazziDebug`, verify with `:app:verifyRoborazziDebug`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class ShellScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private val profile = UserProfile(userId = 1, nickname = "tester", avatarUrl = null, vipType = 0)

    @Test
    fun home_light() = capture("home-light", dark = false)

    @Test
    fun home_dark() = capture("home-dark", dark = true)

    @Test
    fun library_light() = capture("library-light", dark = false, tab = TopLevelDestination.Library)

    @Test
    fun library_dark() = capture("library-dark", dark = true, tab = TopLevelDestination.Library)

    @Test
    fun profile_light() = capture("profile-light", dark = false, tab = TopLevelDestination.Profile)

    @Test
    fun profile_dark() = capture("profile-dark", dark = true, tab = TopLevelDestination.Profile)

    @Test
    fun home_light_medium_width() {
        RuntimeEnvironment.setQualifiers("+w610dp")
        capture("home-light-w610", dark = false)
    }

    @Test
    fun home_light_expanded_width() {
        RuntimeEnvironment.setQualifiers("+w900dp")
        capture("home-light-w900", dark = false)
    }

    @Test
    fun playing_light() {
        compose.setContent {
            SwirlMusicTheme(dynamicColor = false) { SwirlApp(profile = profile, onSignOut = {}, homeContent = { TestHome() }) }
        }
        compose.onNodeWithText(context.getString(one.tunkshif.swirlmusic.R.string.mini_player_idle_title))
            .performClick()
        compose.onRoot().captureRoboImage("src/test/screenshots/shell/playing-light.png")
    }

    private fun capture(
        name: String,
        dark: Boolean,
        tab: TopLevelDestination? = null,
    ) {
        compose.setContent {
            SwirlMusicTheme(darkTheme = dark, dynamicColor = false) { SwirlApp(profile = profile, onSignOut = {}, homeContent = { TestHome() }) }
        }
        if (tab != null) {
            compose.onAllNodesWithText(context.getString(tab.labelRes)).onFirst().performClick()
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/shell/$name.png")
    }

    @androidx.compose.runtime.Composable
    private fun TestHome() {
        CompositionLocalProvider(LocalArtworkPainter provides ColorPainter(Color(0xFF9AA0C8))) {
            HomeScreen(
                nickname = profile.nickname,
                state = HomeTestData.content,
                onRefresh = {},
                onRetry = {},
                now = HomeTestData.evening,
            )
        }
    }
}
