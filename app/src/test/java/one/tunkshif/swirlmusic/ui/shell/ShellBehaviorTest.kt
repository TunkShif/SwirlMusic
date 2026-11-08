package one.tunkshif.swirlmusic.ui.shell

import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
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
import one.tunkshif.swirlmusic.ui.playing.QueueItemUi
import one.tunkshif.swirlmusic.ui.playing.NowPlayingCover
import one.tunkshif.swirlmusic.ui.playing.NowPlayingEvent
import one.tunkshif.swirlmusic.ui.playing.NowPlayingScreen
import one.tunkshif.swirlmusic.ui.playing.NowPlayingTrack
import one.tunkshif.swirlmusic.ui.playing.NowPlayingUiState
import one.tunkshif.swirlmusic.ui.playing.QueueUiState
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
                    homeContent = { _ -> TestHome() },
                    libraryContent = { _, _, _, _, _ -> androidx.compose.material3.Text("Library stub") },
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
                    homeContent = { _ -> TestHome() },
                    libraryContent = { _, _, _, _, _ -> androidx.compose.material3.Text("Library stub") },
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
                    homeContent = { _ -> TestHome() },
                    libraryContent = { _, _, _, _, _ -> androidx.compose.material3.Text("Library stub") },
                    playingContent = { onBack, _, _, _ -> PlayingStub(onBack) },
                )
            }
        }

        compose.onNodeWithTag(MINI_PLAYER_TAG).performClick()

        compose.onNodeWithText(context.getString(R.string.playing_empty_body)).assertExists()
        compose.onNodeWithText(context.getString(R.string.playing_title)).assertExists()
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
                    homeContent = { _ -> TestHome() },
                    libraryContent = { _, _, _, _, _ -> androidx.compose.material3.Text("Library stub") },
                    playingContent = { onBack, _, _, _ -> PlayingStub(onBack) },
                )
            }
        }

        compose.onAllNodesWithText(label(TopLevelDestination.Library)).onFirst().performClick()
        compose.onNodeWithTag(MINI_PLAYER_TAG).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.playing_collapse)).performClick()
        // The dock slides back in; let the animation settle, then both pieces are visible
        // and the previous tab is still selected.
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()

        compose.onNodeWithText("Library stub").assertExists()
        compose.onNodeWithTag(MINI_PLAYER_TAG).assertExists()
        compose.onNodeWithText(label(TopLevelDestination.Home)).assertExists()
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
                    homeContent = { _ -> TestHome() },
                    libraryContent = { _, _, _, _, _ -> androidx.compose.material3.Text("Library stub") },
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

    /** A fixed Now Playing screen so the shell test needs no Hilt or player. */
    @androidx.compose.runtime.Composable
    private fun PlayingStub(onBack: () -> Unit) {
        one.tunkshif.swirlmusic.ui.playing.NowPlayingScreen(
            state = one.tunkshif.swirlmusic.ui.playing.NowPlayingUiState(),
            onEvent = {},
            onBack = onBack,
            onOpenAlbum = {},
            onOpenQueue = {},
        )
    }

    @Test
    fun `the mini player queue button opens the queue sheet`() {
        compose.setContent {
            SwirlMusicTheme(dynamicColor = false) {
                SwirlApp(
                    profile = profile,
                    onSignOut = {},
                    miniPlayer = MiniPlayerUiState(title = "Nightcall", subtitle = "Kavinsky"),
                    onPlayPause = {},
                    onPlayTracks = { _, _, _ -> },
                    queue = QueueUiState(
                        items = listOf(
                            QueueItemUi(uid = 1, title = "Queue item one", artistLine = "a", artworkUrl = null),
                        ),
                    ),
                    onQueueEvent = {},
                    homeContent = { _ -> TestHome() },
                    libraryContent = { _, _, _, _, _ -> androidx.compose.material3.Text("Library stub") },
                    playingContent = { _, _, _, _ -> androidx.compose.material3.Text("Playing stub") },
                )
            }
        }

        compose.onNodeWithContentDescription(context.getString(R.string.playing_queue)).performClick()

        compose.onNodeWithText("Queue item one").assertExists()
    }

    @Test
    fun `the playing queue button opens the same sheet and an empty queue closes it`() {
        val queue = androidx.compose.runtime.mutableStateOf(
            QueueUiState(
                items = listOf(
                    QueueItemUi(uid = 1, title = "Queue item one", artistLine = "a", artworkUrl = null),
                ),
            ),
        )
        compose.setContent {
            SwirlMusicTheme(dynamicColor = false) {
                SwirlApp(
                    profile = profile,
                    onSignOut = {},
                    miniPlayer = MiniPlayerUiState(title = "Nightcall", subtitle = "Kavinsky"),
                    onPlayPause = {},
                    onPlayTracks = { _, _, _ -> },
                    queue = queue.value,
                    onQueueEvent = {},
                    homeContent = { _ -> TestHome() },
                    libraryContent = { _, _, _, _, _ -> androidx.compose.material3.Text("Library stub") },
                    playingContent = { _, _, _, onOpenQueue ->
                        androidx.compose.foundation.layout.Column {
                            androidx.compose.material3.Text("Playing stub")
                            androidx.compose.runtime.SideEffect { onOpenQueue() }
                        }
                    },
                )
            }
        }

        compose.onNodeWithTag(MINI_PLAYER_TAG).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Queue item one").assertExists()

        queue.value = QueueUiState()
        compose.waitForIdle()
        compose.onNodeWithText("Queue item one").assertDoesNotExist()
    }

    @Test
    fun `a long down swipe on the playing top row collapses it`() {
        compose.setContent {
            SwirlMusicTheme(dynamicColor = false) {
                SwirlApp(
                    profile = profile,
                    onSignOut = {},
                    miniPlayer = MiniPlayerUiState(title = "Nightcall", subtitle = "Kavinsky"),
                    onPlayPause = {},
                    onPlayTracks = { _, _, _ -> },
                    homeContent = { _ -> TestHome() },
                    libraryContent = { _, _, _, _, _ -> androidx.compose.material3.Text("Library stub") },
                    playingContent = { onBack, _, _, _ -> PlayingStub(onBack) },
                )
            }
        }

        compose.onNodeWithTag(MINI_PLAYER_TAG).performClick()
        compose.onNodeWithTag(one.tunkshif.swirlmusic.ui.playing.PLAYING_TOP_ROW_TAG)
            .assertExists()

        // > 25 % of the ROOT height: the gesture must pop the playing entry.
        val rootHeight = compose.onRoot().fetchSemanticsNode().size.height
        compose.onNodeWithTag(one.tunkshif.swirlmusic.ui.playing.PLAYING_TOP_ROW_TAG)
            .performTouchInput {
                down(center)
                moveBy(Offset(0f, rootHeight * 0.4f), delayMillis = 100)
                up()
            }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()

        compose.onNodeWithTag(one.tunkshif.swirlmusic.ui.playing.PLAYING_TOP_ROW_TAG)
            .assertDoesNotExist()
    }

    @Test
    fun `a short down swipe stays on the playing screen`() {
        compose.setContent {
            SwirlMusicTheme(dynamicColor = false) {
                SwirlApp(
                    profile = profile,
                    onSignOut = {},
                    miniPlayer = MiniPlayerUiState(title = "Nightcall", subtitle = "Kavinsky"),
                    onPlayPause = {},
                    onPlayTracks = { _, _, _ -> },
                    homeContent = { _ -> TestHome() },
                    libraryContent = { _, _, _, _, _ -> androidx.compose.material3.Text("Library stub") },
                    playingContent = { onBack, _, _, _ -> PlayingStub(onBack) },
                )
            }
        }

        compose.onNodeWithTag(MINI_PLAYER_TAG).performClick()
        compose.onNodeWithTag(one.tunkshif.swirlmusic.ui.playing.PLAYING_TOP_ROW_TAG)
            .assertExists()

        // ~10 % of the height, slow: cancelled, stays on Playing.
        compose.onNodeWithTag(one.tunkshif.swirlmusic.ui.playing.PLAYING_TOP_ROW_TAG)
            .performTouchInput {
                down(center)
                moveBy(Offset(0f, height * 0.10f), delayMillis = 500)
                up()
            }
        compose.waitForIdle()

        compose.onNodeWithTag(one.tunkshif.swirlmusic.ui.playing.PLAYING_TOP_ROW_TAG)
            .assertExists()
    }

    private val coverState = NowPlayingUiState(
        track = NowPlayingTrack("Nightcall", "Kavinsky", null, null, "u1", null),
        currentUid = 1,
        previous = NowPlayingCover(0, "u0", null),
        next = NowPlayingCover(2, "u2", null),
        isPlaying = true,
        durationMs = 60_000,
    )

    @Test
    fun `a down swipe on the cover collapses playing`() {
        compose.setContent {
            SwirlMusicTheme(dynamicColor = false) {
                SwirlApp(
                    profile = profile,
                    onSignOut = {},
                    miniPlayer = MiniPlayerUiState(title = "Nightcall", subtitle = "Kavinsky"),
                    onPlayPause = {},
                    onPlayTracks = { _, _, _ -> },
                    homeContent = { _ -> TestHome() },
                    libraryContent = { _, _, _, _, _ -> androidx.compose.material3.Text("Library stub") },
                    playingContent = { onBack, _, _, _ ->
                        NowPlayingScreen(
                            state = coverState,
                            onEvent = {},
                            onBack = onBack,
                            onOpenAlbum = {},
                            onOpenQueue = {},
                        )
                    },
                )
            }
        }

        compose.onNodeWithTag(MINI_PLAYER_TAG).performClick()
        compose.onNodeWithTag(one.tunkshif.swirlmusic.ui.playing.PLAYING_COVER_TAG)
            .assertExists()

        val rootHeight = compose.onRoot().fetchSemanticsNode().size.height
        compose.onNodeWithTag(one.tunkshif.swirlmusic.ui.playing.PLAYING_COVER_TAG)
            .performTouchInput {
                down(center)
                moveBy(Offset(0f, rootHeight * 0.4f), delayMillis = 100)
                up()
            }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()

        compose.onNodeWithTag(one.tunkshif.swirlmusic.ui.playing.PLAYING_COVER_TAG)
            .assertDoesNotExist()
    }

    @Test
    fun `a horizontal swipe on the cover skips without collapsing`() {
        val events = mutableListOf<Any>()
        compose.setContent {
            SwirlMusicTheme(dynamicColor = false) {
                SwirlApp(
                    profile = profile,
                    onSignOut = {},
                    miniPlayer = MiniPlayerUiState(title = "Nightcall", subtitle = "Kavinsky"),
                    onPlayPause = {},
                    onPlayTracks = { _, _, _ -> },
                    homeContent = { _ -> TestHome() },
                    libraryContent = { _, _, _, _, _ -> androidx.compose.material3.Text("Library stub") },
                    playingContent = { onBack, _, _, _ ->
                        NowPlayingScreen(
                            state = coverState,
                            onEvent = { events += it },
                            onBack = onBack,
                            onOpenAlbum = {},
                            onOpenQueue = {},
                        )
                    },
                )
            }
        }

        compose.onNodeWithTag(MINI_PLAYER_TAG).performClick()
        compose.onNodeWithTag(one.tunkshif.swirlmusic.ui.playing.PLAYING_COVER_TAG)
            .performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()

        assertThat(events).contains(NowPlayingEvent.SkipToCover(2L))
        // Still on Playing: the cover slot remains.
        compose.onNodeWithTag(one.tunkshif.swirlmusic.ui.playing.PLAYING_COVER_TAG)
            .assertExists()
    }
}
