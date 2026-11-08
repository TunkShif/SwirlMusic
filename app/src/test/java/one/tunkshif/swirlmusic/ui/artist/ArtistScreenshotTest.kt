package one.tunkshif.swirlmusic.ui.artist

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import one.tunkshif.swirlmusic.data.model.ArtistDescription
import one.tunkshif.swirlmusic.data.model.ArtistRelease
import one.tunkshif.swirlmusic.data.model.DescriptionSection
import one.tunkshif.swirlmusic.data.model.ReleaseKind
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
class ArtistScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun track(id: Long) = Track(id, "Song $id", "Jay", null, 180_000)

    private fun state() = ArtistUiState(
        name = "Jay Chou",
        briefDesc = "Taiwanese singer-songwriter, actor and director.",
        songCount = 387,
        albumCount = 48,
        topSongs = SectionState.Content((1..8).map { track(it.toLong()) }),
    )

    private fun releases() = listOf(
        ArtistRelease(1, "地表最強", null, 1_700_000_000_000L, ReleaseKind.Album, 22),
        ArtistRelease(2, "J III", null, 1_700_000_000_000L, ReleaseKind.Ep, 6),
        ArtistRelease(3, "即兴曲", null, 1_700_000_000_000L, ReleaseKind.Single, 1),
        ArtistRelease(4, "Other Release", null, null, ReleaseKind.Other, 12),
    )

    private fun show(state: ArtistUiState, dark: Boolean, page: Int = 0) {
        compose.setContent {
            CompositionLocalProvider(LocalArtworkPainter provides ColorPainter(Color(0xFF9AA0C8))) {
                SwirlMusicTheme(darkTheme = dark, dynamicColor = false) {
                    ArtistScreen(
                        state = state,
                        pagerState = androidx.compose.foundation.pager.rememberPagerState(
                            initialPage = page,
                            pageCount = { 2 },
                        ),
                        onBack = {},
                        onRetry = {},
                        onPlayTracks = { _, _ -> },
                    )
                }
            }
        }
    }

    @Test
    fun top_songs_light() {
        show(state(), dark = false)
        compose.onRoot().captureRoboImage("src/test/screenshots/artist/top-songs-light.png")
    }

    @Test
    fun top_songs_dark() {
        show(state(), dark = true)
        compose.onRoot().captureRoboImage("src/test/screenshots/artist/top-songs-dark.png")
    }

    @Test
    fun albums_light() {
        show(state().copy(releases = releases(), releasesComplete = true), dark = false, page = 1)
        compose.onRoot().captureRoboImage("src/test/screenshots/artist/albums-light.png")
    }

    @Test
    fun albums_dark() {
        show(state().copy(releases = releases(), releasesComplete = true), dark = true, page = 1)
        compose.onRoot().captureRoboImage("src/test/screenshots/artist/albums-dark.png")
    }

    @Test
    fun following_light() {
        show(state().copy(followState = one.tunkshif.swirlmusic.ui.components.CollectionSlotState.Ready, followed = true), dark = false)
        compose.onRoot().captureRoboImage("src/test/screenshots/artist/following-light.png")
    }

    @Test
    fun albums_filter_light() {
        show(
            state().copy(
                filter = ReleaseFilter.Eps,
                releases = releases(),
                releasesComplete = true,
            ),
            dark = false,
            page = 1,
        )
        compose.onRoot().captureRoboImage("src/test/screenshots/artist/albums-filter-light.png")
    }

    @Test
    fun albums_filter_dark() {
        show(
            state().copy(
                filter = ReleaseFilter.Eps,
                releases = releases(),
                releasesComplete = true,
            ),
            dark = true,
            page = 1,
        )
        compose.onRoot().captureRoboImage("src/test/screenshots/artist/albums-filter-dark.png")
    }

    /** Artwork colour over the artist screen (fixed orange seed). */
    @Test
    fun themed_light() {
        compose.setContent {
            CompositionLocalProvider(LocalArtworkPainter provides ColorPainter(Color(0xFFE07A1F))) {
                one.tunkshif.swirlmusic.ui.theme.AlbumColorTheme(seed = 0xFFE07A1F.toInt()) {
                    ArtistScreen(
                        state = state().copy(
                            followState = one.tunkshif.swirlmusic.ui.components.CollectionSlotState.Ready,
                        ),
                        onBack = {},
                        onRetry = {},
                        onPlayTracks = { _, _ -> },
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/artist/themed-light.png")
    }

    /** Edge to edge: header colour covers a 24 dp inset; rows run behind the dock. */
    @Test
    fun top_songs_edge_to_edge_light() {
        compose.setContent {
            CompositionLocalProvider(LocalArtworkPainter provides ColorPainter(Color(0xFF9AA0C8))) {
                SwirlMusicTheme(darkTheme = false, dynamicColor = false) {
                    ArtistScreen(
                        state = state(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            top = 24.dp,
                            bottom = 120.dp,
                        ),
                        onBack = {},
                        onRetry = {},
                        onPlayTracks = { _, _ -> },
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/artist/top-songs-edge-to-edge-light.png")
    }

    /** D50: the first-fetch artist header is all skeletons. */
    @Test
    fun header_loading_light() {
        compose.setContent {
            CompositionLocalProvider(LocalArtworkPainter provides ColorPainter(Color(0xFF9AA0C8))) {
                SwirlMusicTheme(darkTheme = false, dynamicColor = false) {
                    ArtistScreen(
                        state = ArtistUiState(),
                        onBack = {},
                        onRetry = {},
                        onPlayTracks = { _, _ -> },
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/artist/header-loading-light.png")
    }

    /** The description sheet, a popup: capture the whole screen. */
    @Test
    fun description_sheet_light() {
        compose.setContent {
            CompositionLocalProvider(LocalArtworkPainter provides ColorPainter(Color(0xFF9AA0C8))) {
                SwirlMusicTheme(darkTheme = false, dynamicColor = false) {
                    Column {
                        DescriptionSheetContent(
                            state().copy(
                                description = DescriptionState.Loaded(
                                    ArtistDescription(
                                        brief = "Bio",
                                        sections = listOf(
                                            DescriptionSection("Early life", "Born in 1979 in New Taipei."),
                                            DescriptionSection("Career", "Debuted in 2000."),
                                        ),
                                    ),
                                ),
                            ),
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/artist/description-sheet-light.png")
    }
}
