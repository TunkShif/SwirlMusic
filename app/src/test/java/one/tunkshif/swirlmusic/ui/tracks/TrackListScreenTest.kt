package one.tunkshif.swirlmusic.ui.tracks

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import one.tunkshif.swirlmusic.R
import one.tunkshif.swirlmusic.data.model.ArtistRef
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
@Config(sdk = [36])
class TrackListScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun res(id: Int) =
        androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
            .getString(id)

    private var toggled = false
    private var openedArtist: Long? = null

    private fun albumState(
        saved: Boolean? = null,
        artists: List<ArtistRef> = emptyList(),
    ) = TrackListUiState(
        title = "Back To Basics",
        subtitle = "Kavinsky",
        trackCount = 1,
        saved = saved,
        artists = artists,
        tracks = SectionState.Content(
            listOf(Track(1, "Song", "Someone Else", null, 180_000)),
        ),
    )

    private fun setAlbumContent(state: TrackListUiState) {
        toggled = false
        openedArtist = null
        compose.setContent {
            CompositionLocalProvider(LocalArtworkPainter provides ColorPainter(Color(0xFF9AA0C8))) {
                SwirlMusicTheme(dynamicColor = false) {
                    TrackListScreen(
                        state = state,
                        isAlbum = true,
                        onBack = {},
                        onRetry = {},
                        onLoadMore = {},
                        onToggleSave = { toggled = true },
                        onOpenArtist = { openedArtist = it },
                        onPlayTracks = { _, _ -> },
                    )
                }
            }
        }
    }

    @Test
    fun `an unknown saved state hides the button`() {
        setAlbumContent(albumState(saved = null))

        compose.onNodeWithContentDescription(res(R.string.track_list_save_album))
            .assertDoesNotExist()
        compose.onNodeWithContentDescription(res(R.string.track_list_remove_album))
            .assertDoesNotExist()
    }

    @Test
    fun `an unsaved album offers save and toggles`() {
        setAlbumContent(albumState(saved = false))

        compose.onNodeWithContentDescription(res(R.string.track_list_save_album))
            .assertIsDisplayed()
            .performClick()
        assertThat(toggled).isTrue()
    }

    @Test
    fun `a saved album offers removal`() {
        setAlbumContent(albumState(saved = true))

        compose.onNodeWithContentDescription(res(R.string.track_list_remove_album))
            .assertIsDisplayed()
    }

    @Test
    fun `one artist opens it directly`() {
        setAlbumContent(albumState(artists = listOf(ArtistRef(501, "Kavinsky"))))

        compose.onNode(hasText("Kavinsky") and hasClickAction()).performClick()

        assertThat(openedArtist).isEqualTo(501L)
    }

    @Test
    fun `several artists open the menu`() {
        setAlbumContent(
            albumState(
                artists = listOf(ArtistRef(501, "Kavinsky"), ArtistRef(502, "M83")),
            ),
        )

        compose.onNode(hasText("Kavinsky") and hasClickAction()).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("M83").assertIsDisplayed().performClick()
        assertThat(openedArtist).isEqualTo(502L)
    }
}
