package one.tunkshif.swirlmusic.ui.tracks

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import one.tunkshif.swirlmusic.data.model.Track
import one.tunkshif.swirlmusic.ui.common.SectionState
import one.tunkshif.swirlmusic.ui.components.LocalArtworkPainter
import one.tunkshif.swirlmusic.ui.theme.SwirlMusicTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TrackListScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun track(id: Long, disc: Int? = null, no: Int? = null) = Track(
        id, "Song $id", "Artist $id", null, 180_000, disc, no,
    )

    private val twoDiscTracks = (1..4).map { track(it.toLong(), 1, it) } +
        (5..8).map { track(it.toLong(), 2, it - 4) }

    private fun albumState() = TrackListUiState(
        title = "Back To Basics",
        subtitle = "Artist",
        trackCount = 22,
        description = "A line of description.\nAnother line.\nAnd one more that goes on and on.",
        dateMillis = 1155600000000L,
        dateKind = DateKind.Released,
        company = "Sony Music",
        albumType = "Studio",
        tracks = SectionState.Content(twoDiscTracks),
    )

    private fun playlistState() = TrackListUiState(
        title = "Late Night Driving",
        subtitle = "curator-name",
        trackCount = 120,
        description = "Songs for the road home.",
        dateMillis = 1700000000000L,
        dateKind = DateKind.Created,
        tracks = SectionState.Content((1..6).map { track(it.toLong()) }),
    )

    @Test
    fun album_two_discs_light() = capture("album-two-discs-light", albumState(), isAlbum = true)

    @Test
    fun playlist_description_light() = capture("playlist-description-light", playlistState())

    @Test
    fun playlist_description_dark() =
        capture("playlist-description-dark", playlistState(), dark = true)

    @Test
    fun details_sheet_light() {
        compose.setContent {
            CompositionLocalProvider(LocalArtworkPainter provides ColorPainter(Color(0xFF9AA0C8))) {
                SwirlMusicTheme(darkTheme = false, dynamicColor = false) {
                    Column {
                        DetailSheetContent(
                            state = playlistState().copy(
                                company = "Sony Music",
                                albumType = "Studio",
                                tags = listOf("pop", "rock"),
                                description = (1..20).joinToString("\n") { "Line $it of a long description." },
                            ),
                        )
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/tracklist/details-sheet-light.png")
    }

    private fun capture(
        name: String,
        state: TrackListUiState,
        dark: Boolean = false,
        isAlbum: Boolean = false,
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalArtworkPainter provides ColorPainter(Color(0xFF9AA0C8))) {
                SwirlMusicTheme(darkTheme = dark, dynamicColor = false) {
                    TrackListScreen(
                        state = state,
                        isAlbum = isAlbum,
                        onBack = {},
                        onRetry = {},
                        onLoadMore = {},
                        onPlayTracks = { _, _ -> },
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/tracklist/$name.png")
    }
}
