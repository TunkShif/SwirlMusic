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
    fun collapsed_light() = captureCollapsed("collapsed-light", playlistState(), fraction = 1f)

    @Test
    fun collapsed_dark() = captureCollapsed("collapsed-dark", playlistState(), fraction = 1f, dark = true)

    @Test
    fun mid_collapse_light() = captureCollapsed("mid-collapse-light", playlistState(), fraction = 0.5f)

    private fun captureCollapsed(
        name: String,
        state: TrackListUiState,
        fraction: Float,
        dark: Boolean = false,
    ) {
        val appBarState = androidx.compose.material3.TopAppBarState(-1f, 0f, 0f)
        compose.setContent {
            CompositionLocalProvider(LocalArtworkPainter provides ColorPainter(Color(0xFF9AA0C8))) {
                SwirlMusicTheme(darkTheme = dark, dynamicColor = false) {
                    TrackListScreen(
                        state = state,
                        appBarState = appBarState,
                        onBack = {},
                        onRetry = {},
                        onLoadMore = {},
                        onPlayTracks = { _, _ -> },
                    )
                }
            }
        }
        // One layout pass computes the limit, then snap to the requested fraction.
        compose.runOnIdle {
            appBarState.heightOffset = appBarState.heightOffsetLimit * fraction
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/tracklist/$name.png")
    }

    @Test
    fun long_text_light() {
        capture(
            "long-text-light",
            playlistState().copy(
                title = "Tragic Kingdom (Deluxe Anniversary Edition Remastered)",
                description = "一场漫长的噩梦终于迎来了终结，所有人都将在这场无尽的狂欢中走向自己的归宿，" +
                    "没有人能够逃离这片被诅咒的土地，直到最后一颗星辰熄灭。",
            ),
        )
    }

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
