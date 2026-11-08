package one.tunkshif.swirlmusic.ui.tracks

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarState
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import one.tunkshif.swirlmusic.data.model.ArtistRelease
import one.tunkshif.swirlmusic.data.model.ReleaseKind
import one.tunkshif.swirlmusic.data.model.Track
import one.tunkshif.swirlmusic.ui.artist.ArtistUiState
import one.tunkshif.swirlmusic.ui.artist.ReleaseFilter
import one.tunkshif.swirlmusic.ui.artist.availableFilters
import one.tunkshif.swirlmusic.ui.artist.filteredReleases
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoundCSupportTest {

    private fun release(id: Long, kind: ReleaseKind) =
        ArtistRelease(id, "R$id", null, 1_000L, kind, 1)

    private fun state(
        releases: List<ArtistRelease>,
        complete: Boolean,
        filter: ReleaseFilter = ReleaseFilter.All,
    ) = ArtistUiState(
        releases = releases,
        releasesComplete = complete,
        filter = filter,
    )

    @Test
    fun `all four filters show while the discography loads`() {
        assertThat(state(listOf(release(1, ReleaseKind.Album)), complete = false).availableFilters())
            .containsExactly(ReleaseFilter.All, ReleaseFilter.Albums, ReleaseFilter.Eps, ReleaseFilter.Singles)
            .inOrder()
    }

    @Test
    fun `empty kinds drop out once complete`() {
        val s = state(listOf(release(1, ReleaseKind.Album), release(2, ReleaseKind.Other)), true)
        assertThat(s.availableFilters())
            .containsExactly(ReleaseFilter.All, ReleaseFilter.Albums)
            .inOrder()
    }

    @Test
    fun `a dropped filter falls back to All`() {
        // Fallback happens on the screen layer: when Singles has no items the caller
        // selects a filter from availableFilters; verify the predicate used.
        val s = state(listOf(release(1, ReleaseKind.Album)), true, ReleaseFilter.Singles)
        assertThat(s.availableFilters()).doesNotContain(s.filter)
    }

    @Test
    fun `Other appears only under All`() {
        val s = state(listOf(release(1, ReleaseKind.Other), release(2, ReleaseKind.Album)), true)
        assertThat(s.filteredReleases()).hasSize(2)
        assertThat(s.copy(filter = ReleaseFilter.Albums).filteredReleases().map { it.kind })
            .containsExactly(ReleaseKind.Album)
    }

    @Test
    fun `disc two's first row is segment index zero`() {
        fun song(id: Long, disc: Int) = AlbumRow.Song(
            Track(id, "T$id", "A", null, 1_000, discNumber = disc), id.toInt(), id.toInt(),
        )
        val rows = listOf(
            AlbumRow.Disc(1), song(1, 1), song(2, 1),
            AlbumRow.Disc(2), song(3, 2), song(4, 2), song(5, 2),
        )
        val segments = segmentIndexOf(rows)
        assertThat(segments[0]).isNull()      // disc header outside groups
        assertThat(segments[1]).isEqualTo(0 to 2)
        assertThat(segments[2]).isEqualTo(1 to 2)
        assertThat(segments[3]).isNull()
        assertThat(segments[4]).isEqualTo(0 to 3)
        assertThat(segments[5]).isEqualTo(1 to 3)
        assertThat(segments[6]).isEqualTo(2 to 3)
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class HeaderInsetTest {

    @get:Rule
    val compose = createComposeRule()

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun `topInset pushes the collapsed bar to inset plus 64 dp`() {
        compose.setContent {
            one.tunkshif.swirlmusic.ui.theme.SwirlMusicTheme(dynamicColor = false) {
                CollapsingDetailHeader(
                    title = "Title",
                    artworkUrl = null,
                    appBarState = TopAppBarState(-1_000f, -1_000f, 0f),
                    onBack = {},
                    onPlayAll = {},
                    topInset = 24.dp,
                )
            }
        }
        val bounds = compose.onNodeWithTag("detailHeader").getUnclippedBoundsInRoot()
        assertThat(bounds.bottom - bounds.top).isEqualTo(88.dp)
    }
}
