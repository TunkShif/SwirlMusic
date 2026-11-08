package one.tunkshif.swirlmusic.ui.artist

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.google.common.truth.Truth.assertThat
import one.tunkshif.swirlmusic.R
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
@Config(sdk = [36])
class ArtistScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun res(id: Int) =
        androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
            .getString(id)

    private fun track(id: Long) = Track(id, "Song $id", "Jay", null, 180_000)

    private fun state(
        followed: Boolean? = null,
        releases: List<ArtistRelease> = emptyList(),
        description: DescriptionState = DescriptionState.Idle,
    ) = ArtistUiState(
        name = "Jay Chou",
        briefDesc = "Singer-songwriter.",
        songCount = 387,
        albumCount = 48,
        topSongs = SectionState.Content(listOf(track(1), track(2), track(3))),
        followState = if (followed == null) one.tunkshif.swirlmusic.ui.components.CollectionSlotState.Loading else one.tunkshif.swirlmusic.ui.components.CollectionSlotState.Ready,
        followed = followed ?: false,
        releases = releases,
        releasesComplete = releases.isNotEmpty(),
        description = description,
    )

    private fun release(id: Long, kind: ReleaseKind = ReleaseKind.Album) =
        ArtistRelease(id, "Album $id", null, 1_600_000_000_000L, kind, 10)

    private var playedIndex: Int? = null
    private var openedAlbum: Long? = null
    private var toggled = false
    private var descOpened = false

    @androidx.compose.runtime.Composable
    private fun Content(state: ArtistUiState) {
        CompositionLocalProvider(LocalArtworkPainter provides ColorPainter(Color(0xFF9AA0C8))) {
            SwirlMusicTheme(dynamicColor = false) {
                ArtistScreen(
                    state = state,
                    onBack = {},
                    onRetry = {},
                    onToggleFollow = { toggled = true },
                    onOpenDescription = { descOpened = true },
                    onOpenAlbum = { openedAlbum = it },
                    onPlayTracks = { _, i -> playedIndex = i },
                )
            }
        }
    }

    private fun setContent(state: ArtistUiState) {
        toggled = false
        openedAlbum = null
        playedIndex = null
        descOpened = false
        compose.setContent { Content(state) }
    }

    @Test
    fun `tapping the Albums tab switches pages`() {
        setContent(state())
        compose.onNodeWithText(res(R.string.artist_tab_albums)).performClick()
        compose.waitForIdle()
        // Swiping back to top songs by tapping the tab.
        compose.onNodeWithText(res(R.string.artist_tab_top_songs)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Song 1").assertIsDisplayed()
    }

    @Test
    fun `swiping switches to the albums page`() {
        setContent(state(releases = listOf(release(9))))
        compose.waitForIdle()
        compose.onNodeWithText("Song 1").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("Album 9").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the selected page survives restoration`() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Content(state(releases = listOf(release(9)))) }
        compose.onNodeWithText(res(R.string.artist_tab_albums)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Album 9").assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        compose.onNodeWithText("Album 9").assertIsDisplayed()
    }

    @Test
    fun `a null followed state hides the button`() {
        setContent(state(followed = null))
        compose.onNodeWithText(res(R.string.artist_follow)).assertDoesNotExist()
        compose.onNodeWithText(res(R.string.artist_following)).assertDoesNotExist()
    }

    @Test
    fun `a failed follow read hides the toggle`() {
        setContent(state().copy(
            followState = one.tunkshif.swirlmusic.ui.components.CollectionSlotState.Failed,
        ))
        compose.onNodeWithText(res(R.string.artist_follow)).assertDoesNotExist()
        compose.onNodeWithText(res(R.string.artist_following)).assertDoesNotExist()
    }

    @Test
    fun `not followed offers Follow and toggles`() {
        setContent(state(followed = false))
        compose.onNodeWithText(res(R.string.artist_follow)).assertIsDisplayed().performClick()
        assertThat(toggled).isTrue()
    }

    @Test
    fun `followed shows Following`() {
        setContent(state(followed = true))
        compose.onNodeWithText(res(R.string.artist_following)).assertIsDisplayed()
    }

    @Test
    fun `the description sheet opens and shows sections`() {
        setContent(
            state(
                description = DescriptionState.Loaded(
                    ArtistDescription(
                        brief = "Bio",
                        sections = listOf(DescriptionSection("Early life", "Born in 1979.")),
                    ),
                ),
            ),
        )

        compose.onNodeWithText("Singer-songwriter.").performClick()
        compose.waitForIdle()

        assertThat(descOpened).isTrue()
        compose.onNodeWithText("Early life").assertIsDisplayed()
        compose.onNodeWithText("Born in 1979.").assertIsDisplayed()
    }

    @Test
    fun `an album card opens the album`() {
        setContent(state(releases = listOf(release(9))))
        compose.onNodeWithText(res(R.string.artist_tab_albums)).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Album 9").performClick()
        assertThat(openedAlbum).isEqualTo(9L)
    }

    @Test
    fun `a top song tap plays at that index`() {
        setContent(state())
        compose.onNodeWithText("Song 2").performClick()
        assertThat(playedIndex).isEqualTo(1)
    }
}
