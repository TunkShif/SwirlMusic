package one.tunkshif.swirlmusic.ui.home

import java.time.LocalTime
import one.tunkshif.swirlmusic.data.model.ChartSummary
import one.tunkshif.swirlmusic.data.model.PlaylistSummary
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

    val playlists = listOf(
        PlaylistSummary(10, "Late night drive", null, 1_234_567, 42),
        PlaylistSummary(11, "Deep focus", null, 987_000, 30),
        PlaylistSummary(12, "Morning coffee", null, 45_600, 18),
    )

    val charts = listOf(
        ChartSummary(
            id = 20,
            name = "Soaring",
            artworkUrl = null,
            updateFrequency = "Updated daily",
            previewLines = listOf("Song A — Artist A", "Song B — Artist B"),
        ),
        ChartSummary(
            id = 21,
            name = "New songs",
            artworkUrl = null,
            updateFrequency = "Updated weekly",
            previewLines = listOf("Song C — Artist C"),
        ),
        ChartSummary(
            id = 22,
            name = "Original",
            artworkUrl = null,
            updateFrequency = "Updated weekly",
            previewLines = listOf("Song D — Artist D"),
        ),
    )

    val content = HomeUiState(
        daily = SectionState.Content(tracks),
        playlists = SectionState.Content(playlists),
        charts = SectionState.Content(charts),
    )

    val loading = HomeUiState()

    val error = HomeUiState(
        daily = SectionState.Failed(LoadError.Network),
        playlists = SectionState.Content(playlists),
        charts = SectionState.Failed(LoadError.Server),
    )

    val empty = HomeUiState(
        daily = SectionState.Content(emptyList()),
        playlists = SectionState.Content(emptyList()),
        charts = SectionState.Content(emptyList()),
    )
}
