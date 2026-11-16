package one.tunkshif.swirlmusic.ui.home

import java.time.LocalTime
import one.tunkshif.swirlmusic.data.model.AlbumSummary
import one.tunkshif.swirlmusic.data.model.RecentAlbum
import one.tunkshif.swirlmusic.ui.common.LoadError
import one.tunkshif.swirlmusic.ui.common.SectionState
import one.tunkshif.swirlmusic.data.model.Track

/**
 * Fixed data for Compose tests and goldens. No network, no clock, no randomness.
 */
object HomeTestData {

    val evening: LocalTime = LocalTime.of(20, 0)

    val tracks = listOf(
        Track(1, "Nightcall", "Kavinsky", null, 258_000),
        Track(2, "Midnight City", "M83", null, 244_000),
        Track(3, "Digital Love", "Daft Punk", null, 301_000),
    )

    val recents = listOf(
        RecentAlbum(
            album = AlbumSummary(30, "OutRun", "Kavinsky", null, trackCount = 10),
            playedAtMillis = 1_000L,
        ),
        RecentAlbum(
            album = AlbumSummary(31, "Fantasy", "M83 / Guest", null, trackCount = 12),
            playedAtMillis = 2_000L,
        ),
    )

    val content = HomeUiState(
        daily = SectionState.Content(tracks),
        recent = SectionState.Content(recents),
        likedIds = listOf(1L, 2L, 3L),
    )

    val loading = HomeUiState()

    val error = HomeUiState(
        daily = SectionState.Failed(LoadError.Network),
        recent = SectionState.Failed(LoadError.Server),
        likedIds = listOf(1L),
    )

    val empty = HomeUiState(
        daily = SectionState.Content(emptyList()),
        recent = SectionState.Content(emptyList()),
    )
}
