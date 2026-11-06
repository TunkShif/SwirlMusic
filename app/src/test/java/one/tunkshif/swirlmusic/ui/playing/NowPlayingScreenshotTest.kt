package one.tunkshif.swirlmusic.ui.playing

import one.tunkshif.swirlmusic.R

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import one.tunkshif.swirlmusic.ui.components.LocalArtworkPainter
import one.tunkshif.swirlmusic.ui.theme.SwirlMusicTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens for the Now Playing screen. `autoAdvance` stays off so the infinite wave animation and
 * the marquee freeze at frame zero; `LocalArtworkPainter` pins the cover.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class NowPlayingScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun playing_light() = capture("playing-light", PlayingStatePlaying, dark = false)

    @Test
    fun playing_dark() = capture("playing-dark", PlayingStatePlaying, dark = true)

    @Test
    fun paused_light() = capture("playing-paused", PlayingStatePaused, dark = false)

    @Test
    fun loading_light() = capture("playing-loading", PlayingStateLoading, dark = false)

    @Test
    fun trial_error_light() = capture("playing-trial-error", PlayingStateTrialError, dark = false)

    @Test
    fun empty_light() = capture("playing-empty", PlayingStateEmpty, dark = false)

    @Test
    fun playing_short_phone() {
        RuntimeEnvironment.setQualifiers("+w360dp-h640dp")
        capture("playing-short", PlayingStatePlaying, dark = false)
    }

    @Test
    fun queue_light() = captureQueue("queue-light", QueueFixture, dark = false)

    @Test
    fun queue_dark() = captureQueue("queue-dark", QueueFixture, dark = true)

    @Test
    fun queue_shuffle() = captureQueue("queue-shuffle", QueueFixture.copy(canReorder = false), dark = false)

    private fun captureQueue(name: String, queue: QueueUiState, dark: Boolean) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SwirlMusicTheme(darkTheme = dark, dynamicColor = false) {
                CompositionLocalProvider(
                    LocalArtworkPainter provides ColorPainter(Color(0xFF9AA0C8)),
                    LocalPositionClock provides { 0L },
                ) {
                    androidx.compose.material3.Surface {
                        QueueSheetContent(queue = queue, onEvent = {})
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onRoot().captureRoboImage("src/test/screenshots/playing/$name.png")
    }

    private fun capture(
        name: String,
        state: NowPlayingUiState,
        dark: Boolean,
        lyrics: LyricsUiState = LyricsUiState.Loading,
        showLyrics: Boolean = false,
        toast: PlayerToastState? = null,
    ) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SwirlMusicTheme(darkTheme = dark, dynamicColor = false) {
                CompositionLocalProvider(
                    LocalArtworkPainter provides ColorPainter(Color(0xFF9AA0C8)),
                    LocalPositionClock provides { 0L },
                ) {
                    NowPlayingScreen(
                        state = state,
                        onEvent = {},
                        onBack = {},
                        onOpenAlbum = {},
                        onOpenQueue = {},
                        lyrics = lyrics,
                        showLyrics = showLyrics,
                        toastState = toast ?: rememberPlayerToastState(),
                    )
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onRoot().captureRoboImage(
            "src/test/screenshots/playing/$name.png",
            roborazziOptions = WAVY_TOLERANCE,
        )
    }


    @Test
    fun lyrics_synced_light() = capture("lyrics-synced-light", PlayingStatePlaying, dark = false, lyrics = LyricsSynced, showLyrics = true)

    @Test
    fun lyrics_synced_dark() = capture("lyrics-synced-dark", PlayingStatePlaying, dark = true, lyrics = LyricsSynced, showLyrics = true)

    @Test
    fun lyrics_word_timed_light() = capture("lyrics-word-timed-light", PlayingStatePlaying, dark = false, lyrics = LyricsWordTimed, showLyrics = true)

    @Test
    fun lyrics_romanization_light() = capture("lyrics-romanization-light", PlayingStatePlaying, dark = false, lyrics = LyricsRomanized, showLyrics = true)

    @Test
    fun lyrics_error_light() = capture("lyrics-error-light", PlayingStatePlaying, dark = false, lyrics = LyricsUiState.Error, showLyrics = true)

    @Test
    fun lyrics_instrumental_light() = capture("lyrics-instrumental-light", PlayingStatePlaying, dark = false, lyrics = LyricsInstrumental, showLyrics = true)


    @Test
    fun playing_toast_light() = capture(
        "playing-toast-light", PlayingStatePlaying, dark = false,
        toast = PlayerToastState().apply {
            show(R.drawable.ic_shuffle, R.string.playing_loop_shuffle)
        },
    )

    @Test
    fun lyrics_toast_dark() = capture(
        "lyrics-toast-dark", PlayingStatePlaying, dark = true,
        lyrics = LyricsSynced, showLyrics = true,
        toast = PlayerToastState().apply {
            show(R.drawable.ic_translate, R.string.playing_lyrics_showing_translation)
        },
    )

    private companion object {
        /**
         * The wavy indicator's stroke edge carries a few pixels of antialiasing noise between
         * runs; everything else is bit-exact.
         */
        val WAVY_TOLERANCE = RoborazziOptions(
            compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.002f),
        )
    }
}
