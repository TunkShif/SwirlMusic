package one.tunkshif.swirlmusic.ui.playing

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.google.common.truth.Truth.assertThat
import one.tunkshif.swirlmusic.R
import one.tunkshif.swirlmusic.player.LoopMode
import one.tunkshif.swirlmusic.ui.theme.SwirlMusicTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Behaviour tests for the Now Playing screen. JVM, no Hilt, no player. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class NowPlayingScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()

    private val events = mutableListOf<Any>()
    private var backPressed = 0
    private var openedAlbum: Long? = null
    private var queueOpened = 0

    private fun show(state: NowPlayingUiState): androidx.compose.runtime.MutableState<NowPlayingUiState> {
        val uiState = androidx.compose.runtime.mutableStateOf(state)
        compose.setContent {
            SwirlMusicTheme(dynamicColor = false) {
                NowPlayingScreen(
                    state = uiState.value,
                    onEvent = { events += it },
                    onBack = { backPressed++ },
                    onOpenAlbum = { openedAlbum = it },
                    onOpenQueue = { queueOpened++ },
                )
            }
        }
        return uiState
    }

    private fun description(res: Int) = context.getString(res)

    @Test
    fun `each transport button sends its event`() {
        show(PlayingStatePaused)

        compose.onNodeWithContentDescription(description(R.string.playing_play))
            .performClick()
        compose.onNodeWithContentDescription(description(R.string.playing_next))
            .performClick()
        compose.onNodeWithContentDescription(description(R.string.playing_previous))
            .performClick()
        compose.onNodeWithContentDescription(description(R.string.playing_loop_list))
            .performClick()
        compose.onNodeWithContentDescription(description(R.string.playing_queue))
            .performClick()

        assertThat(events).containsExactly(
            NowPlayingEvent.PlayPause,
            NowPlayingEvent.Next,
            NowPlayingEvent.Previous,
            NowPlayingEvent.CycleLoopMode,
        ).inOrder()
        assertThat(queueOpened).isEqualTo(1)
    }

    @Test
    fun `the pause button sends play-pause too`() {
        show(PlayingStatePlaying)

        // isPlaying = true, so the morph shows the pause icon/description.
        compose.onNodeWithContentDescription(description(R.string.playing_pause)).performClick()

        assertThat(events).containsExactly(NowPlayingEvent.PlayPause)
    }

    @Test
    fun `the loop button describes each mode`() {
        val uiState = show(PlayingStatePlaying)
        compose.onNodeWithContentDescription(description(R.string.playing_loop_list)).assertExists()

        uiState.value = PlayingStatePlaying.copy(loopMode = LoopMode.Shuffle)
        compose.onNodeWithContentDescription(description(R.string.playing_loop_shuffle)).assertExists()

        uiState.value = PlayingStatePlaying.copy(loopMode = LoopMode.RepeatOne)
        compose.onNodeWithContentDescription(description(R.string.playing_loop_one)).assertExists()
    }

    @Test
    fun `the album line opens the album only when it has an id`() {
        val uiState = show(PlayingStatePlaying)
        compose.onNodeWithText("OutRun").performClick()
        assertThat(openedAlbum).isEqualTo(601)

        openedAlbum = null
        uiState.value = PlayingStatePlaying.copy(
            track = PlayingStatePlaying.track!!.copy(albumId = null),
        )
        compose.onNodeWithText("OutRun")
            .assertExists()
            .assert(
                SemanticsMatcher("has no click action") {
                    !it.config.contains(SemanticsActions.OnClick)
                },
            )
        assertThat(openedAlbum).isNull()
    }

    @Test
    fun `collapse calls back`() {
        show(PlayingStatePlaying)

        compose.onNodeWithContentDescription(description(R.string.playing_collapse)).performClick()

        assertThat(backPressed).isEqualTo(1)
    }

    @Test
    fun `next is disabled without a following track`() {
        val uiState = show(PlayingStatePlaying.copy(hasNext = false))

        compose.onNodeWithContentDescription(description(R.string.playing_next)).assertIsNotEnabled()

        uiState.value = PlayingStatePlaying
        compose.onNodeWithContentDescription(description(R.string.playing_next)).assertIsEnabled()
    }

    @Test
    fun `previous is enabled whenever a track shows`() {
        show(PlayingStatePlaying)

        compose.onNodeWithContentDescription(description(R.string.playing_previous)).assertIsEnabled()
    }

    @Test
    fun `seeking sends the target position`() {
        show(PlayingStatePlaying)

        compose.onNodeWithContentDescription(description(R.string.playing_seek))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(129_000f) }

        val seek = events.filterIsInstance<NowPlayingEvent.SeekTo>().single()
        // Half of a 258 s track; allow the slider's own rounding.
        assertThat(seek.positionMs).isAtLeast(120_000)
        assertThat(seek.positionMs).isAtMost(140_000)
    }

    @Test
    fun `the empty state shows without a track`() {
        show(PlayingStateEmpty)

        compose.onNodeWithText(context.getString(R.string.playing_empty_body)).assertExists()
    }
}
