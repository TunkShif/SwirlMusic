package one.tunkshif.swirlmusic.ui.playing

import com.google.common.truth.Truth.assertThat
import one.tunkshif.swirlmusic.data.local.LyricsExtra
import one.tunkshif.swirlmusic.data.model.LyricLine
import one.tunkshif.swirlmusic.data.model.Lyrics
import one.tunkshif.swirlmusic.data.model.Track
import one.tunkshif.swirlmusic.player.LoopMode
import one.tunkshif.swirlmusic.player.PlaybackState
import one.tunkshif.swirlmusic.player.QueueEntry
import app.cash.turbine.test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import one.tunkshif.swirlmusic.data.api.ApiResult
import one.tunkshif.swirlmusic.data.model.LyricWord
import org.junit.Test

class LyricsUiStateTest {

    private fun line(text: String, t: String? = null, r: String? = null) =
        LyricLine(0, 1, text, translation = t, romanization = r)

    @Test
    fun `choices hold only the extras the song has`() {
        val both = lyricsContentOf(
            Lyrics.Synced(listOf(line("a", "t", "r")), emptyList()),
            LyricsExtra.Translation,
        )
        assertThat(both.extraChoices)
            .containsExactly(LyricsExtra.None, LyricsExtra.Translation, LyricsExtra.Romanization)
            .inOrder()
        assertThat(both.extra).isEqualTo(LyricsExtra.Translation)

        val none = lyricsContentOf(
            Lyrics.Synced(listOf(line("a")), emptyList()),
            LyricsExtra.Translation,
        )
        assertThat(none.extraChoices).containsExactly(LyricsExtra.None)
        assertThat(none.extra).isEqualTo(LyricsExtra.None)
    }

    @Test
    fun `a preferred extra the song lacks falls back to none`() {
        val content = lyricsContentOf(
            Lyrics.Synced(listOf(line("a", t = "t")), emptyList()),
            LyricsExtra.Romanization,
        )
        assertThat(content.extra).isEqualTo(LyricsExtra.None)
    }

    @Test
    fun `non synced lyrics only offer none`() {
        assertThat(lyricsContentOf(Lyrics.None, LyricsExtra.Translation).extraChoices)
            .containsExactly(LyricsExtra.None)
        assertThat(
            lyricsContentOf(Lyrics.Instrumental(listOf("c")), LyricsExtra.Romanization).extra,
        ).isEqualTo(LyricsExtra.None)
    }

    @Test
    fun `next extra cycles and wraps`() {
        val choices = listOf(LyricsExtra.None, LyricsExtra.Translation, LyricsExtra.Romanization)
        assertThat(nextExtra(choices, LyricsExtra.None)).isEqualTo(LyricsExtra.Translation)
        assertThat(nextExtra(choices, LyricsExtra.Translation)).isEqualTo(LyricsExtra.Romanization)
        assertThat(nextExtra(choices, LyricsExtra.Romanization)).isEqualTo(LyricsExtra.None)
        // A current value outside the choices cycles from the start.
        assertThat(nextExtra(listOf(LyricsExtra.None), LyricsExtra.Translation))
            .isEqualTo(LyricsExtra.None)
    }

    private fun queueState(order: List<Int>? = null, currentIndex: Int = 0, size: Int = 3) =
        PlaybackState(
            queue = (0 until size).map { QueueEntry(it.toLong(), Track(it.toLong(), "t$it", "a", null, 0)) },
            currentIndex = currentIndex,
            playOrder = order ?: List(size) { it },
        )

    @Test
    fun `next track in play order`() {
        assertThat(nextTrackIdInPlayOrder(queueState(currentIndex = 0))).isEqualTo(1L)
        assertThat(nextTrackIdInPlayOrder(queueState(currentIndex = 2))).isEqualTo(0L)
        // Shuffled order: after index 1 comes index 2 in playOrder [0,2,1].
        assertThat(
            nextTrackIdInPlayOrder(queueState(order = listOf(0, 2, 1), currentIndex = 1)),
        ).isEqualTo(0L)
        assertThat(nextTrackIdInPlayOrder(queueState(size = 1))).isNull()
        assertThat(nextTrackIdInPlayOrder(queueState(size = 0))).isNull()
    }

    private fun word(start: Long, dur: Long, text: String) = LyricWord(start, dur, text)
    private val words = listOf(
        word(1_000, 500, "alpha"),
        word(2_000, 500, " beta"),
        word(3_000, 500, " gamma"),
    )

    @Test
    fun `word fill offset`() {
        // Before the first word, in a gap, mid-word, past the end.
        assertThat(wordFillOffset(words, 0, 0)).isEqualTo(0f)
        assertThat(wordFillOffset(words, 1_500, 0)).isEqualTo(5f)    // gap: end of "alpha"
        assertThat(wordFillOffset(words, 1_250, 0)).isEqualTo(2.5f)  // half of "alpha"
        assertThat(wordFillOffset(words, 2_500, 0)).isEqualTo(10f)   // all of " beta" (5+5)
        assertThat(wordFillOffset(words, 99_999, 0)).isEqualTo(Float.MAX_VALUE)
        // Leading whitespace on the first word is trimmed from the line text:
        // mid-word the fill counts only visible chars past the trimmed pad.
        assertThat(wordFillOffset(listOf(word(0, 100, "  abc")), 50, 2)).isEqualTo(0.5f)
        assertThat(wordFillOffset(listOf(word(0, 100, "  abc")), 100, 2)).isEqualTo(Float.MAX_VALUE)
        // A zero-duration word ends instantly: full fill, no division by zero.
        assertThat(wordFillOffset(listOf(word(0, 0, "a")), 0, 0)).isEqualTo(Float.MAX_VALUE)
    }

    @Test
    fun `lyrics flow reloads on track change and retry`() = runTest {
        val ids = MutableStateFlow<Long?>(1L)
        val retry = MutableStateFlow(0)
        val lyricsA = Lyrics.Unsynced(listOf("a"), emptyList())
        val lyricsB = Lyrics.Unsynced(listOf("b"), emptyList())
        var failures = false

        lyricsStateFlow(
            trackIds = ids,
            retry = retry,
            fetch = { id ->
                kotlinx.coroutines.delay(1)   // lets the Loading marker land first
                when {
                    failures -> ApiResult.NetworkError(IllegalStateException("x"))
                    id == 1L -> ApiResult.Success(lyricsA)
                    else -> ApiResult.Success(lyricsB)
                }
            },
            extra = flowOf(LyricsExtra.None),
        ).test {
            assertThat(awaitItem()).isEqualTo(LyricsUiState.Loading)
            assertThat((awaitItem() as LyricsUiState.Content).lyrics).isEqualTo(lyricsA)

            ids.value = 2L
            assertThat(awaitItem()).isEqualTo(LyricsUiState.Loading)
            assertThat((awaitItem() as LyricsUiState.Content).lyrics).isEqualTo(lyricsB)

            failures = true
            retry.value = 1
            assertThat(awaitItem()).isEqualTo(LyricsUiState.Loading)
            assertThat(awaitItem()).isEqualTo(LyricsUiState.Error)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
