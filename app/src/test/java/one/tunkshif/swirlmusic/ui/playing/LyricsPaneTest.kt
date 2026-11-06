package one.tunkshif.swirlmusic.ui.playing

import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import one.tunkshif.swirlmusic.R
import one.tunkshif.swirlmusic.data.local.LyricsExtra
import one.tunkshif.swirlmusic.data.model.LyricLine
import one.tunkshif.swirlmusic.data.model.Lyrics
import one.tunkshif.swirlmusic.ui.theme.SwirlMusicTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Behaviour tests for the lyrics pane. JVM, no player. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class LyricsPaneTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()

    private val seeks = mutableListOf<Long>()
    private var retries = 0
    private val cycles = mutableListOf<LyricsExtra>()
    private var positionMs by mutableLongStateOf(0L)
    private var contentSet = false
    private var uiState by mutableStateOf<LyricsUiState>(LyricsUiState.Loading)

    private fun line(start: Long, text: String, t: String? = null, r: String? = null) =
        LyricLine(start, Long.MAX_VALUE, text, translation = t, romanization = r)

    private fun synced(count: Int, t: String? = "translated", r: String? = "romanized") =
        Lyrics.Synced(
            lines = (0 until count).map { i ->
                line(i * 4_000L, "line $i", t?.let { "$it $i" }, r?.let { "$it $i" })
            },
            credits = emptyList(),
        )

    // One content install; later calls just swap the state.
    private fun show(state: LyricsUiState) {
        if (contentSet) {
            uiState = state
            compose.waitForIdle()
            return
        }
        uiState = state
        contentSet = true
        compose.setContent {
            SwirlMusicTheme(dynamicColor = false) {
                Surface {
                    LyricsPane(
                        state = uiState,
                        position = { positionMs },
                        onSeek = { seeks += it },
                        onRetry = { retries++ },
                        onCycleExtra = { cycles += it },
                    )
                }
            }
        }
    }

    private fun content(lyrics: Lyrics, extra: LyricsExtra, choices: List<LyricsExtra>) =
        LyricsUiState.Content(lyrics, extra, choices)

    private fun isSelected() = SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)

    @Test
    fun `position inside a line selects and displays it`() {
        show(content(synced(40), LyricsExtra.None, listOf(LyricsExtra.None)))

        // Before the first line: nothing selected.
        positionMs = -1
        compose.waitForIdle()
        compose.onAllNodes(isSelected()).assertCountEquals(0)

        // Line 30 starts at 120 s; follow must scroll it into view.
        positionMs = 120_500
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()

        compose.onNodeWithText("line 30").assertIsSelected()
        compose.onNodeWithText("line 30").assertExists()
    }

    @Test
    fun `tapping a line seeks to its start`() {
        show(content(synced(5), LyricsExtra.None, listOf(LyricsExtra.None)))

        compose.onNodeWithText("line 2").performClick()

        assertThat(seeks).containsExactly(8_000L)
    }

    @Test
    fun `the extra line follows the mode`() {
        show(content(synced(2), LyricsExtra.Translation, listOf(LyricsExtra.None, LyricsExtra.Translation)))

        compose.onNodeWithText("translated 0").assertExists()
        compose.onAllNodes(androidx.compose.ui.test.hasText("romanized 0")).assertCountEquals(0)

        show(content(synced(2), LyricsExtra.Romanization, listOf(LyricsExtra.None, LyricsExtra.Romanization)))
        compose.onNodeWithText("romanized 0").assertExists()
        compose.onAllNodes(androidx.compose.ui.test.hasText("translated 0")).assertCountEquals(0)

        show(content(synced(2), LyricsExtra.None, listOf(LyricsExtra.None, LyricsExtra.Translation)))
        compose.onAllNodes(androidx.compose.ui.test.hasText("translated 0")).assertCountEquals(0)
    }

    @Test
    fun `the extra button shows only with choices and cycles`() {
        show(
            content(
                synced(2),
                LyricsExtra.Translation,
                listOf(LyricsExtra.None, LyricsExtra.Translation, LyricsExtra.Romanization),
            ),
        )
        compose.onNodeWithContentDescription(
            context.getString(R.string.playing_lyrics_extra_fmt, "Translation"),
        ).performClick()
        assertThat(cycles).containsExactly(LyricsExtra.Romanization)

        // A song without extras hides the button.
        show(content(synced(2, t = null, r = null), LyricsExtra.None, listOf(LyricsExtra.None)))
        compose.onNodeWithContentDescription(
            context.getString(R.string.playing_lyrics_extra_fmt, "Translation"),
        ).assertDoesNotExist()
    }

    @Test
    fun `error retries`() {
        show(LyricsUiState.Error)

        compose.onNodeWithText(context.getString(R.string.playing_lyrics_retry)).performClick()

        assertThat(retries).isEqualTo(1)
    }

    @Test
    fun `empty states show their messages`() {
        show(content(Lyrics.None, LyricsExtra.None, listOf(LyricsExtra.None)))
        compose.onNodeWithText(context.getString(R.string.playing_lyrics_none)).assertExists()

        show(
            content(
                Lyrics.Instrumental(listOf("composer: synth")),
                LyricsExtra.None,
                listOf(LyricsExtra.None),
            ),
        )
        compose.onNodeWithText(context.getString(R.string.playing_lyrics_instrumental)).assertExists()
        compose.onNodeWithText("composer: synth").assertExists()

        show(
            content(
                Lyrics.Unsynced(listOf("first", "second"), emptyList()),
                LyricsExtra.None,
                listOf(LyricsExtra.None),
            ),
        )
        compose.onNodeWithText("first").assertExists()
        compose.onNodeWithText("second").assertExists()
    }
}
