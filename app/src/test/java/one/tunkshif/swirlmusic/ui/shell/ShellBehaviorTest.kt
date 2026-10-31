package one.tunkshif.swirlmusic.ui.shell

import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import one.tunkshif.swirlmusic.R
import one.tunkshif.swirlmusic.data.model.UserProfile
import one.tunkshif.swirlmusic.navigation.TopLevelDestination
import one.tunkshif.swirlmusic.ui.components.MINI_PLAYER_TAG
import one.tunkshif.swirlmusic.ui.home.HomeScreen
import one.tunkshif.swirlmusic.ui.player.MiniPlayerUiState
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

/**
 * Behaviour tests for the shell. Runs on the JVM: no device, no Hilt, no network.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class ShellBehaviorTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private val profile = UserProfile(userId = 1, nickname = "tester", avatarUrl = null, vipType = 0)

    @Test
    fun `starts on home and shows an empty state`() {
        compose.setContent {
            SwirlMusicTheme(dynamicColor = false) {
                SwirlApp(
                    profile = profile,
                    onSignOut = {},
                    miniPlayer = MiniPlayerUiState(
                        title = "Nightcall",
                        subtitle = "Kavinsky",
                        isPlaying = true,
                        progress = 0.35f,
                    ),
                    onPlayPause = {},
                    onPlayTracks = { _, _, _ -> },
                    homeContent = { TestHome() },
                    libraryContent = { _, _, _, _ -> androidx.compose.material3.Text("Library stub") },
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.home_daily_title)).assertExists()
    }

    @Test
    fun `tapping a tab replaces the screen`() {
        compose.setContent {
            SwirlMusicTheme(dynamicColor = false) {
                SwirlApp(
                    profile = profile,
                    onSignOut = {},
                    miniPlayer = MiniPlayerUiState(
                        title = "Nightcall",
                        subtitle = "Kavinsky",
                        isPlaying = true,
                        progress = 0.35f,
                    ),
                    onPlayPause = {},
                    onPlayTracks = { _, _, _ -> },
                    homeContent = { TestHome() },
                    libraryContent = { _, _, _, _ -> androidx.compose.material3.Text("Library stub") },
                )
            }
        }

        compose.onAllNodesWithText(label(TopLevelDestination.Library)).onFirst().performClick()
        compose.onNodeWithText("Library stub").assertExists()

        compose.onAllNodesWithText(label(TopLevelDestination.Profile)).onFirst().performClick()
        compose.onNodeWithText(profile.nickname).assertExists()
    }

    @Test
    fun `the mini player opens the playing screen and hides the chrome`() {
        compose.setContent {
            SwirlMusicTheme(dynamicColor = false) {
                SwirlApp(
                    profile = profile,
                    onSignOut = {},
                    miniPlayer = MiniPlayerUiState(
                        title = "Nightcall",
                        subtitle = "Kavinsky",
                        isPlaying = true,
                        progress = 0.35f,
                    ),
                    onPlayPause = {},
                    onPlayTracks = { _, _, _ -> },
                    homeContent = { TestHome() },
                    libraryContent = { _, _, _, _ -> androidx.compose.material3.Text("Library stub") },
                )
            }
        }

        compose.onNodeWithTag(MINI_PLAYER_TAG).performClick()

        compose.onNodeWithText(context.getString(R.string.playing_empty_body)).assertExists()
        // The bottom bar is gone, so no navigation label is on screen.
        assertThat(compose.onAllNodesWithText(label(TopLevelDestination.Home)).fetchSemanticsNodes())
            .isEmpty()
    }

    @Test
    fun `back from the playing screen returns to the tab`() {
        compose.setContent {
            SwirlMusicTheme(dynamicColor = false) {
                SwirlApp(
                    profile = profile,
                    onSignOut = {},
                    miniPlayer = MiniPlayerUiState(
                        title = "Nightcall",
                        subtitle = "Kavinsky",
                        isPlaying = true,
                        progress = 0.35f,
                    ),
                    onPlayPause = {},
                    onPlayTracks = { _, _, _ -> },
                    homeContent = { TestHome() },
                    libraryContent = { _, _, _, _ -> androidx.compose.material3.Text("Library stub") },
                )
            }
        }

        compose.onAllNodesWithText(label(TopLevelDestination.Library)).onFirst().performClick()
        compose.onNodeWithTag(MINI_PLAYER_TAG).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.playing_back)).performClick()

        compose.onNodeWithText("Library stub").assertExists()
    }

    @Test
    fun `the selected tab survives recreation, as after a rotation`() {
        val restorationTester = StateRestorationTester(compose)
        restorationTester.setContent {
            SwirlMusicTheme(dynamicColor = false) {
                SwirlApp(
                    profile = profile,
                    onSignOut = {},
                    miniPlayer = MiniPlayerUiState(
                        title = "Nightcall",
                        subtitle = "Kavinsky",
                        isPlaying = true,
                        progress = 0.35f,
                    ),
                    onPlayPause = {},
                    onPlayTracks = { _, _, _ -> },
                    homeContent = { TestHome() },
                    libraryContent = { _, _, _, _ -> androidx.compose.material3.Text("Library stub") },
                )
            }
        }

        compose.onAllNodesWithText(label(TopLevelDestination.Library)).onFirst().performClick()
        restorationTester.emulateSavedInstanceStateRestore()

        compose.onNodeWithText("Library stub").assertExists()
    }

    private fun label(destination: TopLevelDestination) = context.getString(destination.labelRes)

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
