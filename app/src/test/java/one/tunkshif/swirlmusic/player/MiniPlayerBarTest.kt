package one.tunkshif.swirlmusic.player

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import com.google.common.truth.Truth.assertThat
import one.tunkshif.swirlmusic.R
import one.tunkshif.swirlmusic.ui.components.MINI_PLAYER_TAG
import one.tunkshif.swirlmusic.ui.components.MiniPlayerBar
import one.tunkshif.swirlmusic.ui.player.MiniPlayerUiState
import one.tunkshif.swirlmusic.ui.theme.SwirlMusicTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class MiniPlayerBarTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `a playing track shows its title, artist, and a pause action`() {
        setContent(
            MiniPlayerUiState(
                title = "Nightcall",
                subtitle = "Kavinsky",
                isPlaying = true,
                progress = 0.4f,
            ),
        )

        compose.onNodeWithText("Nightcall").assertExists()
        compose.onNodeWithText("Kavinsky").assertExists()
        compose.onNodeWithContentDescription(text(R.string.mini_player_pause)).assertExists()
    }

    @Test
    fun `a resolving track shows its title and keeps the pause action`() {
        setContent(
            MiniPlayerUiState(
                title = "Nightcall",
                subtitle = "Kavinsky",
                isPlaying = true,
                isLoading = true,
            ),
        )

        // Tapping showed the track at once; the ring is indeterminate until audio starts.
        compose.onNodeWithText("Nightcall").assertExists()
        compose.onNodeWithContentDescription(text(R.string.mini_player_pause)).assertExists()
    }

    @Test
    fun `a trial replaces the artist line with the preview note`() {
        setContent(
            MiniPlayerUiState(
                title = "Premiums only",
                subtitle = "Artist",
                isPlaying = true,
                isTrial = true,
            ),
        )

        compose.onNodeWithText(text(R.string.mini_player_preview)).assertExists()
        compose.onNodeWithText("Artist").assertDoesNotExist()
    }

    @Test
    fun `the transport button reports the click`() {
        var toggled = false
        setContent(MiniPlayerUiState(title = "Nightcall", isPlaying = true), onPlayPause = { toggled = true })

        compose.onNodeWithContentDescription(text(R.string.mini_player_pause)).performClick()

        assertThat(toggled).isTrue()
    }

    @Test
    fun `tapping the bar opens the player`() {
        var opened = false
        setContent(MiniPlayerUiState(title = "Nightcall"), onOpen = { opened = true })

        compose.onNodeWithText("Nightcall").performClick()

        assertThat(opened).isTrue()
    }

    private fun setContent(
        state: MiniPlayerUiState,
        onPlayPause: () -> Unit = {},
        onOpen: () -> Unit = {},
        onNext: () -> Unit = {},
        onPrevious: () -> Unit = {},
    ) {
        compose.setContent {
            SwirlMusicTheme(dynamicColor = false) {
                MiniPlayerBar(
                    state = state,
                    onPlayPause = onPlayPause,
                    onOpen = onOpen,
                    onNext = onNext,
                    onPrevious = onPrevious,
                )
            }
        }
    }

    private fun text(resId: Int): String = context.getString(resId)

    @Test
    fun `swiping up past the lift threshold opens the player`() {
        var opens = 0
        compose.setContent {
            SwirlMusicTheme(dynamicColor = false) {
                MiniPlayerBar(
                    state = MiniPlayerUiState(title = "Nightcall", subtitle = "Kavinsky"),
                    onPlayPause = {},
                    onOpen = { opens++ },
                )
            }
        }
        compose.onNodeWithText("Nightcall").performTouchInput {
            swipeUp(endY = centerY - 120f)
        }
        compose.waitForIdle()
        assertThat(opens).isEqualTo(1)
    }

    @Test
    fun `a small swipe up or a swipe down does not open`() {
        var opens = 0
        compose.setContent {
            SwirlMusicTheme(dynamicColor = false) {
                MiniPlayerBar(
                    state = MiniPlayerUiState(title = "Nightcall", subtitle = "Kavinsky"),
                    onPlayPause = {},
                    onOpen = { opens++ },
                )
            }
        }
        compose.onNodeWithText("Nightcall").performTouchInput {
            swipeUp(endY = centerY - 10f)
        }
        compose.onNodeWithText("Nightcall").performTouchInput { swipeDown() }
        compose.waitForIdle()
        assertThat(opens).isEqualTo(0)
    }

    @Test
    fun `swiping the title left skips to the next track`() {
        var nexts = 0
        var previouss = 0
        setContent(
            MiniPlayerUiState(title = "Nightcall", trackKey = 1, hasNext = true, hasPrevious = true),
            onNext = { nexts++ },
            onPrevious = { previouss++ },
        )

        // A long drag inside the text column (the middle of the bar) commits the skip.
        compose.onNodeWithTag(MINI_PLAYER_TAG).performTouchInput {
            swipe(Offset(centerX, centerY), Offset(left, centerY))
        }
        compose.waitForIdle()

        assertThat(nexts).isEqualTo(1)
        assertThat(previouss).isEqualTo(0)
    }

    @Test
    fun `swiping the title right skips to the previous track`() {
        var nexts = 0
        var previouss = 0
        setContent(
            MiniPlayerUiState(title = "Nightcall", trackKey = 1, hasNext = true, hasPrevious = true),
            onNext = { nexts++ },
            onPrevious = { previouss++ },
        )

        compose.onNodeWithTag(MINI_PLAYER_TAG).performTouchInput {
            swipe(Offset(centerX, centerY), Offset(right - 96f, centerY))
        }
        compose.waitForIdle()

        assertThat(nexts).isEqualTo(0)
        assertThat(previouss).isEqualTo(1)
    }

    @Test
    fun `a short swipe does not skip`() {
        var nexts = 0
        var previouss = 0
        setContent(
            MiniPlayerUiState(title = "Nightcall", trackKey = 1, hasNext = true, hasPrevious = true),
            onNext = { nexts++ },
            onPrevious = { previouss++ },
        )

        compose.onNodeWithText("Nightcall").performTouchInput {
            swipe(Offset(centerX, centerY), Offset(centerX - 30f, centerY))
        }
        compose.waitForIdle()

        assertThat(nexts).isEqualTo(0)
        assertThat(previouss).isEqualTo(0)
    }

    @Test
    fun `swiping the artwork does not skip`() {
        var nexts = 0
        var previouss = 0
        setContent(
            MiniPlayerUiState(title = "Nightcall", trackKey = 1, hasNext = true, hasPrevious = true),
            onNext = { nexts++ },
            onPrevious = { previouss++ },
        )

        // The artwork sits at the start of the bar; the drag zone is the text only.
        compose.onNodeWithTag(MINI_PLAYER_TAG).performTouchInput {
            swipe(Offset(left + 32f, centerY), Offset(left + 300f, centerY))
        }
        compose.waitForIdle()

        assertThat(nexts).isEqualTo(0)
        assertThat(previouss).isEqualTo(0)
    }

    @Test
    fun `a left swipe without a next track does not skip`() {
        var nexts = 0
        setContent(
            MiniPlayerUiState(title = "Nightcall", trackKey = 1, hasNext = false),
            onNext = { nexts++ },
        )

        compose.onNodeWithTag(MINI_PLAYER_TAG).performTouchInput {
            swipe(Offset(centerX, centerY), Offset(left, centerY))
        }
        compose.waitForIdle()

        assertThat(nexts).isEqualTo(0)
    }
}
