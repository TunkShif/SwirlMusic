package one.tunkshif.swirlmusic.ui.library

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import one.tunkshif.swirlmusic.data.model.Page
import one.tunkshif.swirlmusic.ui.common.PagedList
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
class LibraryScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun playlists_light() = capture("playlists-light", dark = false)

    @Test
    fun playlists_dark() = capture("playlists-dark", dark = true)

    @Test
    fun albums_light() {
        // The screen stubs onSelectTab, so pick the tab via state instead of a click.
        compose.setContent {
            CompositionLocalProvider(LocalArtworkPainter provides ColorPainter(Color(0xFF9AA0C8))) {
                SwirlMusicTheme(dynamicColor = false) {
                    LibraryScreenFor(state = state().copy(tab = LibraryTab.Albums))
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/library/albums-light.png")
    }

    @Test
    fun empty_playlists_light() {
        capture(
            "empty-light",
            dark = false,
            state = state().copy(playlists = PagedList(state = SectionState.Content(Unit))),
        )
    }

    private fun capture(name: String, dark: Boolean, state: LibraryUiState = state()) {
        compose.setContent {
            CompositionLocalProvider(LocalArtworkPainter provides ColorPainter(Color(0xFF9AA0C8))) {
                SwirlMusicTheme(darkTheme = dark, dynamicColor = false) {
                    LibraryScreenFor(state = state)
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/library/$name.png")
    }

    private fun state(): LibraryUiState = LibraryUiState(
        playlists = paged(
            listOf(
                playlist(1, liked = true),
                playlist(2),
                playlist(3),
                playlist(4, saved = true),
                playlist(5, saved = true),
            ),
        ),
        albums = paged(listOf(album(1), album(2))),
        artists = paged(listOf(artist(1))),
        recent = SectionState.Content(listOf(playedEntry())),
    )

    private fun <T> paged(items: List<T>) = PagedList(
        items = items,
        state = SectionState.Content(Unit),
        hasMore = false,
    )

    private fun playedEntry() = one.tunkshif.swirlmusic.data.model.PlayedEntry(
        track = track(1),
        playCount = 12,
    )
}

@androidx.compose.runtime.Composable
private fun LibraryScreenFor(state: LibraryUiState) {
    LibraryScreen(
        state = state,
        onSelectTab = {},
        onRetry = {},
        onLoadMore = {},
        onOpenSearch = {},
        onOpenPlaylist = {},
        onOpenAlbum = {},
        onOpenArtist = {},
        onPlayTracks = { _, _ -> },
    )
}
