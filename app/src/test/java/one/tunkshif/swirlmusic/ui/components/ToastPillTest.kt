package one.tunkshif.swirlmusic.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.google.common.truth.Truth.assertThat
import one.tunkshif.swirlmusic.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ToastPillTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()

    private fun show(state: ToastPillState) {
        compose.setContent {
            ToastPillHost(state)
        }
    }

    private val textA = context.getString(R.string.playing_lyrics_showing_translation)
    private val textB = context.getString(R.string.playing_lyrics_showing_romanization)

    @Test
    fun `shows then auto-dismisses`() {
        val state = ToastPillState()
        show(state)

        state.show(R.drawable.ic_translate, R.string.playing_lyrics_showing_translation)
        compose.waitForIdle()
        compose.onNodeWithText(textA).assertExists()

        compose.mainClock.advanceTimeBy(1_500 + 500)
        compose.waitForIdle()
        compose.onNodeWithText(textA).assertDoesNotExist()
    }

    @Test
    fun `a second show replaces and restarts the timer`() {
        val state = ToastPillState()
        show(state)

        state.show(R.drawable.ic_translate, R.string.playing_lyrics_showing_translation)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        state.show(R.drawable.ic_abc, R.string.playing_lyrics_showing_romanization)
        compose.waitForIdle()

        compose.onNodeWithText(textA).assertDoesNotExist()
        compose.onNodeWithText(textB).assertExists()

        // B lives a full 1.5 s from its own show.
        compose.mainClock.advanceTimeBy(1_200)
        compose.onNodeWithText(textB).assertExists()
        compose.mainClock.advanceTimeBy(800)
        compose.waitForIdle()
        compose.onNodeWithText(textB).assertDoesNotExist()
    }

    @Test
    fun `same text twice gets a new id and stays displayed`() {
        val state = ToastPillState()
        show(state)

        state.show(R.drawable.ic_translate, R.string.playing_lyrics_showing_translation)
        compose.waitForIdle()
        val firstId = state.current?.id

        // Re-showing the same text produces a new message (the timer key changes).
        state.show(R.drawable.ic_translate, R.string.playing_lyrics_showing_translation)
        compose.waitForIdle()
        assertThat(state.current?.id).isNotEqualTo(firstId)
        compose.onNodeWithText(textA).assertExists()

        // And the full timeout still applies from the second show.
        compose.mainClock.advanceTimeBy(1_500 + 500)
        compose.waitForIdle()
        compose.onNodeWithText(textA).assertDoesNotExist()
    }
}
