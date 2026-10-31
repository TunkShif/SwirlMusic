package one.tunkshif.swirlmusic.ui.shell

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import one.tunkshif.swirlmusic.navigation.AlbumKey
import one.tunkshif.swirlmusic.navigation.ArtistKey
import one.tunkshif.swirlmusic.navigation.HomeKey
import one.tunkshif.swirlmusic.navigation.LibraryKey
import androidx.navigation3.runtime.NavKey
import one.tunkshif.swirlmusic.navigation.PlayingKey
import one.tunkshif.swirlmusic.navigation.PlaylistKey
import one.tunkshif.swirlmusic.navigation.ProfileKey
import one.tunkshif.swirlmusic.navigation.SearchKey
import one.tunkshif.swirlmusic.navigation.TopLevelDestination
import org.junit.Test

class ShellNavigationTest {

    @Test
    fun `every key except playing shows the dock`() {
        val docked: List<NavKey?> = listOf(
            HomeKey, LibraryKey, ProfileKey, PlaylistKey(1), AlbumKey(1), ArtistKey(1),
            SearchKey, null,
        )
        docked.forEach { key ->
            assertWithMessage("$key shows dock").that(showsDock(key)).isTrue()
        }
        assertThat(showsDock(PlayingKey)).isFalse()
    }

    @Test
    fun `the selected tab is the stack root`() {
        assertThat(selectedTab(listOf(HomeKey))).isEqualTo(TopLevelDestination.Home)
        assertThat(selectedTab(listOf(LibraryKey))).isEqualTo(TopLevelDestination.Library)
        assertThat(selectedTab(listOf(ProfileKey))).isEqualTo(TopLevelDestination.Profile)
    }

    @Test
    fun `a detail screen keeps its parent tab selected`() {
        assertThat(selectedTab(listOf(LibraryKey, PlaylistKey(7))))
            .isEqualTo(TopLevelDestination.Library)
        assertThat(selectedTab(listOf(HomeKey, AlbumKey(3))))
            .isEqualTo(TopLevelDestination.Home)
        assertThat(selectedTab(listOf(LibraryKey, PlaylistKey(7), PlayingKey)))
            .isEqualTo(TopLevelDestination.Library)
    }

    @Test
    fun `an empty stack selects nothing`() {
        assertThat(selectedTab(emptyList())).isNull()
    }
}
