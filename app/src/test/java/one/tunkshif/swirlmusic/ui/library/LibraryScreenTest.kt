package one.tunkshif.swirlmusic.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.google.common.truth.Truth.assertThat
import one.tunkshif.swirlmusic.R
import one.tunkshif.swirlmusic.data.model.PlayedEntry
import one.tunkshif.swirlmusic.ui.common.PagedList
import one.tunkshif.swirlmusic.ui.common.SectionState
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
class LibraryScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun res(id: Int) =
        androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
            .getString(id)

    /** What the view model would hold; `onSelectTab` writes back like `selectTab` does. */
    private var uiState by mutableStateOf(LibraryUiState())

    private fun state(
        tab: LibraryTab = LibraryTab.Playlists,
        playlists: PagedList<one.tunkshif.swirlmusic.data.model.PlaylistSummary> = PagedList(
            items = List(30) { playlist((it + 1).toLong()) },
            state = SectionState.Content(Unit),
        ),
        albums: PagedList<one.tunkshif.swirlmusic.data.model.AlbumSummary> = PagedList(
            items = listOf(album(1)),
            state = SectionState.Content(Unit),
        ),
        artists: PagedList<one.tunkshif.swirlmusic.data.model.ArtistSummary> = PagedList(
            items = listOf(artist(1)),
            state = SectionState.Content(Unit),
        ),
        recent: SectionState<List<PlayedEntry>> = SectionState.Content(
            listOf(PlayedEntry(track(1), playCount = 7)),
        ),
    ) = LibraryUiState(
        tab = tab,
        playlists = playlists,
        albums = albums,
        artists = artists,
        recent = recent,
    )

    private fun content() {
        compose.setContent {
            SwirlMusicTheme(dynamicColor = false) {
                LibraryScreen(
                    state = uiState,
                    onSelectTab = { uiState = uiState.copy(tab = it) },
                    onRetry = {},
                    onLoadMore = {},
                    onOpenSearch = {},
                    onOpenPlaylist = {},
                    onOpenAlbum = {},
                    onOpenArtist = {},
                    onPlayTracks = { _, _ -> },
                )
            }
        }
    }

    @Test
    fun `tapping a pill selects its tab`() {
        uiState = state()
        content()

        compose.onNodeWithText(res(R.string.library_tab_artists)).performClick()
        compose.waitForIdle()

        assertThat(uiState.tab).isEqualTo(LibraryTab.Artists)
        compose.onNodeWithText("Artist 1").assertIsDisplayed()
    }

    @Test
    fun `swiping the sheet selects the next tab`() {
        uiState = state()
        content()

        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitForIdle()

        assertThat(uiState.tab).isEqualTo(LibraryTab.Albums)
        compose.onNode(isSelected() and hasText(res(R.string.library_tab_albums)))
            .assertIsDisplayed()
        compose.onNodeWithText("Album 1").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the selected page survives restoration`() {
        uiState = state()
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            SwirlMusicTheme(dynamicColor = false) {
                LibraryScreen(
                    state = uiState,
                    onSelectTab = { uiState = uiState.copy(tab = it) },
                    onRetry = {},
                    onLoadMore = {},
                    onOpenSearch = {},
                    onOpenPlaylist = {},
                    onOpenAlbum = {},
                    onOpenArtist = {},
                    onPlayTracks = { _, _ -> },
                )
            }
        }
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("Album 1").assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        compose.onNodeWithText("Album 1").assertIsDisplayed()
        compose.onNode(isSelected() and hasText(res(R.string.library_tab_albums)))
            .assertIsDisplayed()
    }

    @Test
    fun `a tab keeps its scroll position after swiping away and back`() {
        uiState = state()
        content()

        // The pager also carries scroll semantics; the playlists page is the vertical one.
        compose.onNode(
            hasScrollToIndexAction() and
                hasAnyDescendant(hasText("Playlist 3")) and
                SemanticsMatcher("vertical scroll axis") {
                    SemanticsProperties.VerticalScrollAxisRange in it.config
                },
        ).performScrollToIndex(25)
        compose.waitForIdle()
        compose.onNodeWithText("Playlist 26").assertIsDisplayed()

        repeat(3) {
            compose.onRoot().performTouchInput { swipeLeft() }
            compose.waitForIdle()
        }
        repeat(3) {
            compose.onRoot().performTouchInput { swipeRight() }
            compose.waitForIdle()
        }

        assertThat(uiState.tab).isEqualTo(LibraryTab.Playlists)
        compose.onNodeWithText("Playlist 26").assertIsDisplayed()
    }

    @Test
    fun `the load-more footer is centred in the grid`() {
        uiState = state(
            tab = LibraryTab.Albums,
            albums = PagedList(
                items = listOf(album(1)),
                state = SectionState.Content(Unit),
                hasMore = true,
            ),
        )
        content()

        val progress = compose.onNode(
            hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate),
        )
        val rootCenter = compose.onRoot().fetchSemanticsNode().boundsInRoot.center.x
        val footerCenter = progress.fetchSemanticsNode().boundsInRoot.center.x
        assertThat(footerCenter.toDouble()).isWithin(1.0).of(rootCenter.toDouble())
    }
}
