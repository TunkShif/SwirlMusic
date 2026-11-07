package one.tunkshif.swirlmusic.ui.playing

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.google.common.truth.Truth.assertThat
import one.tunkshif.swirlmusic.R
import one.tunkshif.swirlmusic.data.model.ArtistRef
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
    private var openedArtist: Long? = null
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
                    onOpenArtist = { openedArtist = it },
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
    fun `a single artist opens it from the artist line`() {
        show(
            PlayingStatePlaying.copy(
                track = PlayingStatePlaying.track!!.copy(
                    artists = listOf(ArtistRef(501, "Kavinsky")),
                ),
            ),
        )

        compose.onNodeWithText("Kavinsky").performClick()

        assertThat(openedArtist).isEqualTo(501)
    }

    @Test
    fun `several artists open a menu that picks one`() {
        show(
            PlayingStatePlaying.copy(
                track = PlayingStatePlaying.track!!.copy(
                    artistLine = "M83 / Guest",
                    artists = listOf(ArtistRef(502, "M83"), ArtistRef(503, "Guest")),
                ),
            ),
        )

        compose.onNodeWithText("M83 / Guest").performClick()
        compose.onNodeWithText("M83").assertExists()
        compose.onNodeWithText("Guest").performClick()

        assertThat(openedArtist).isEqualTo(503)
        compose.onNodeWithText("M83").assertDoesNotExist()
    }

    @Test
    fun `no artists leaves the artist line unclickable`() {
        show(PlayingStatePlaying)

        compose.onNodeWithText("Kavinsky").assertHasNoClickAction()
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

    @Test
    fun `the lyrics toggle and cover tap call the toggle`() {
        var toggles = 0
        compose.setContent {
            SwirlMusicTheme(dynamicColor = false) {
                NowPlayingScreen(
                    state = PlayingStatePlaying,
                    onEvent = {},
                    onBack = {},
                    onOpenAlbum = {},
                    onOpenQueue = {},
                    onToggleLyrics = { toggles++ },
                )
            }
        }

        compose.onNodeWithContentDescription(context.getString(R.string.playing_lyrics)).performClick()
        assertThat(toggles).isEqualTo(1)

        compose.onNode(
            androidx.compose.ui.test.SemanticsMatcher("click label") { node ->
                runCatching {
                    node.config[androidx.compose.ui.semantics.SemanticsActions.OnClick].label
                }.getOrNull() == context.getString(R.string.playing_show_lyrics)
            },
        ).performClick()
        assertThat(toggles).isEqualTo(2)
    }

    @Test
    fun `showLyrics renders the pane instead of the cover`() {
        val lyrics = one.tunkshif.swirlmusic.data.model.Lyrics.Synced(
            lines = listOf(
                one.tunkshif.swirlmusic.data.model.LyricLine(0, Long.MAX_VALUE, "lyric line one"),
            ),
            credits = emptyList(),
        )
        compose.setContent {
            SwirlMusicTheme(dynamicColor = false) {
                NowPlayingScreen(
                    state = PlayingStatePlaying,
                    onEvent = {},
                    onBack = {},
                    onOpenAlbum = {},
                    onOpenQueue = {},
                    lyrics = LyricsUiState.Content(lyrics, one.tunkshif.swirlmusic.data.local.LyricsExtra.None, listOf(one.tunkshif.swirlmusic.data.local.LyricsExtra.None)),
                    showLyrics = true,
                )
            }
        }
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()

        compose.onNodeWithText("lyric line one").assertExists()
    }

    @Test
    fun `tapping loop shows the target mode toast`() {
        compose.setContent {
            SwirlMusicTheme(dynamicColor = false) {
                NowPlayingScreen(
                    state = PlayingStatePlaying,
                    onEvent = { events += it },
                    onBack = {},
                    onOpenAlbum = {},
                    onOpenQueue = {},
                )
            }
        }

        compose.onNodeWithContentDescription(description(R.string.playing_loop_list)).performClick()

        assertThat(events).contains(NowPlayingEvent.CycleLoopMode)
        compose.onNodeWithText(context.getString(R.string.playing_loop_shuffle)).assertExists()
    }
}
