package one.tunkshif.swirlmusic.ui.tracks

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import one.tunkshif.swirlmusic.data.model.ArtistRef
import one.tunkshif.swirlmusic.data.model.Track
import one.tunkshif.swirlmusic.ui.common.SectionState
import one.tunkshif.swirlmusic.ui.components.LocalArtworkPainter
import one.tunkshif.swirlmusic.ui.components.LocalArtworkPainterResolver
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
    fun album_two_discs_light() = capture("album-two-discs-light", albumState().copy(saveState = one.tunkshif.swirlmusic.ui.components.CollectionSlotState.Ready, saved = false), isAlbum = true)

    @Test
    fun album_saved_light() =
        capture("album-saved-light", albumState().copy(saveState = one.tunkshif.swirlmusic.ui.components.CollectionSlotState.Ready, saved = true), isAlbum = true)

    @Test
    fun album_saved_dark() =
        capture("album-saved-dark", albumState().copy(saveState = one.tunkshif.swirlmusic.ui.components.CollectionSlotState.Ready, saved = true), dark = true, isAlbum = true)

    /** The album subtitle's choose-artist menu, open (popup, so capture the whole screen). */
    @Test
    fun album_artist_menu_light() {
        compose.setContent {
            CompositionLocalProvider(
                LocalArtworkPainterResolver provides { url ->
                    when (url) {
                        "avatar-501" -> ColorPainter(Color(0xFFE0662B))
                        else -> ColorPainter(Color(0xFF9AA0C8))
                    }
                },
            ) {
                SwirlMusicTheme(darkTheme = false, dynamicColor = false) {
                    TrackListScreen(
                        state = albumState().copy(
                            subtitle = "Kavinsky / M83",
                            artists = listOf(
                                ArtistRef(501, "Kavinsky"),
                                ArtistRef(502, "M83"),
                            ),
                            artistAvatars = mapOf(501L to "avatar-501"),
                        ),
                        isAlbum = true,
                        onBack = {},
                        onRetry = {},
                        onLoadMore = {},
                        onPlayTracks = { _, _ -> },
                    )
                }
            }
        }
        compose.onNodeWithText("Kavinsky / M83").performClick()
        compose.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/tracklist/album-artist-menu-light.png")
    }

    /** The action slot mid-read: a skeleton stadium, not the pill. */
    @Test
    fun album_loading_action_light() =
        capture("album-loading-action-light", albumState().copy(saveState = one.tunkshif.swirlmusic.ui.components.CollectionSlotState.Loading), isAlbum = true)

    /** Artwork colour over the whole screen (fixed saturated-orange seed). */
    @Test
    fun album_themed_light() {
        themedCapture("album-themed-light", seed = 0xFFE07A1F.toInt())
    }

    @Test
    fun album_themed_dark() {
        themedCapture("album-themed-dark", seed = 0xFFE07A1F.toInt(), dark = true)
    }

    /** Edge to edge: the bar colour covers a 24 dp status-bar inset; dock padding bottom. */
    @Test
    fun playlist_edge_to_edge_light() {
        compose.setContent {
            CompositionLocalProvider(LocalArtworkPainter provides ColorPainter(Color(0xFF9AA0C8))) {
                SwirlMusicTheme(darkTheme = false, dynamicColor = false) {
                    TrackListScreen(
                        state = playlistState(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            top = 24.dp,
                            bottom = 120.dp,
                        ),
                        onBack = {},
                        onRetry = {},
                        onLoadMore = {},
                        onPlayTracks = { _, _ -> },
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/tracklist/playlist-edge-to-edge-light.png")
    }

    private fun themedCapture(name: String, seed: Int, dark: Boolean = false) {
        compose.setContent {
            CompositionLocalProvider(LocalArtworkPainter provides ColorPainter(Color(seed))) {
                one.tunkshif.swirlmusic.ui.theme.AlbumColorTheme(seed = seed) {
                    TrackListScreen(
                        state = albumState().copy(saveState = one.tunkshif.swirlmusic.ui.components.CollectionSlotState.Ready, saved = true),
                        isAlbum = true,
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

    /** D50: the first-fetch header is all skeletons — no Play all. */
    @Test
    fun album_header_loading_light() =
        capture("album-header-loading-light", TrackListUiState(), isAlbum = true)

    /** The track-list skeleton rows match the real `TrackRow` height. */
    @Test
    fun loading_rows_light() {
        compose.setContent {
            SwirlMusicTheme(darkTheme = false, dynamicColor = false) {
                one.tunkshif.swirlmusic.ui.common.LoadingRows()
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/tracklist/loading-rows-light.png")
    }

    /** D52: the playing row takes the fully-rounded selected segment. */
    @Test
    fun album_now_playing_light() {
        compose.setContent {
            CompositionLocalProvider(LocalArtworkPainter provides ColorPainter(Color(0xFF9AA0C8))) {
                SwirlMusicTheme(darkTheme = false, dynamicColor = false) {
                    TrackListScreen(
                        state = albumState().copy(saveState = one.tunkshif.swirlmusic.ui.components.CollectionSlotState.Ready, saved = true),
                        isAlbum = true,
                        nowPlayingId = 2L,
                        onBack = {},
                        onRetry = {},
                        onLoadMore = {},
                        onPlayTracks = { _, _ -> },
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/tracklist/album-now-playing-light.png")
    }

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
