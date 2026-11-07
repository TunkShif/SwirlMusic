package one.tunkshif.swirlmusic.ui.playing

import com.google.common.truth.Truth.assertThat
import one.tunkshif.swirlmusic.data.model.ArtistRef
import one.tunkshif.swirlmusic.data.model.Track
import one.tunkshif.swirlmusic.player.LoopMode
import one.tunkshif.swirlmusic.player.PlaybackState
import one.tunkshif.swirlmusic.player.QueueEntry
import org.junit.Test

class NowPlayingUiStateTest {

    private val track = Track(
        id = 1,
        title = "Nightcall",
        artistLine = "Kavinsky",
        artworkUrl = "http://p1.example/cover.jpg?param=300y300",
        durationMs = 258_000,
        albumId = 601,
        albumName = "OutRun",
    )

    private fun state(vararg pairs: Pair<Long, Track>, currentIndex: Int = 0) = PlaybackState(
        queue = pairs.map { (uid, t) -> QueueEntry(uid, t) },
        currentIndex = currentIndex,
    )

    @Test
    fun `the track carries a resized cover and the list url as placeholder`() {
        val ui = state(0L to track).toNowPlayingUiState()

        assertThat(ui.track!!.artworkUrl)
            .isEqualTo("https://p1.example/cover.jpg?param=1080y1080")
        assertThat(ui.track.placeholderArtworkUrl)
            .isEqualTo("http://p1.example/cover.jpg?param=300y300")
        assertThat(ui.track.albumId).isEqualTo(601)
        assertThat(ui.track.albumName).isEqualTo("OutRun")
    }

    @Test
    fun `a pending track shows the play intent, not the player flag`() {
        val pending = track.copy(id = 2, title = "Pending")
        val ui = state(0L to track).copy(
            pending = pending,
            playIntent = true,
            isPlaying = false,
        ).toNowPlayingUiState()

        assertThat(ui.track!!.title).isEqualTo("Pending")
        assertThat(ui.isPlaying).isTrue()
        assertThat(ui.isLoading).isTrue()
    }

    @Test
    fun `duration falls back to the track when the player reports zero`() {
        val ui = state(0L to track).copy(durationMs = 0).toNowPlayingUiState()

        assertThat(ui.durationMs).isEqualTo(258_000)
    }

    @Test
    fun `the track drops unusable artists and dedups by id`() {
        val withArtists = track.copy(
            artists = listOf(
                ArtistRef(0, "Zero"),
                ArtistRef(-3, "Negative"),
                ArtistRef(501, "  "),
                ArtistRef(502, "M83"),
                ArtistRef(501, "Kavinsky"),
                ArtistRef(502, "Duplicate"),
            ),
        )

        val ui = state(0L to withArtists).toNowPlayingUiState()

        assertThat(ui.track!!.artists)
            .containsExactly(ArtistRef(502, "M83"), ArtistRef(501, "Kavinsky"))
            .inOrder()
    }

    @Test
    fun `an empty queue maps to a null track`() {
        val ui = PlaybackState(loopMode = LoopMode.Shuffle).toNowPlayingUiState()

        assertThat(ui.track).isNull()
        assertThat(ui.loopMode).isEqualTo(LoopMode.Shuffle)
        assertThat(ui.hasNext).isFalse()
    }
}
