package one.tunkshif.swirlmusic.ui.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import one.tunkshif.swirlmusic.R
import one.tunkshif.swirlmusic.navigation.HomeKey
import one.tunkshif.swirlmusic.navigation.LibraryKey
import one.tunkshif.swirlmusic.navigation.PlayingKey
import one.tunkshif.swirlmusic.navigation.ProfileKey
import one.tunkshif.swirlmusic.navigation.TopLevelDestination
import one.tunkshif.swirlmusic.data.model.Track
import one.tunkshif.swirlmusic.ui.components.MiniPlayerBar
import one.tunkshif.swirlmusic.ui.player.MiniPlayerUiState
import one.tunkshif.swirlmusic.data.model.UserProfile
import one.tunkshif.swirlmusic.ui.home.HomeRoute
import one.tunkshif.swirlmusic.ui.library.LibraryScreen
import one.tunkshif.swirlmusic.ui.playing.PlayingScreen
import one.tunkshif.swirlmusic.ui.profile.ProfileScreen

/**
 * The app shell: navigation, bottom bar, and mini player.
 *
 * One back stack, owned by [rememberNavBackStack], so it survives rotation and process death.
 * Tapping a tab replaces the stack with that tab's root. Per-tab history arrives when a tab gets
 * its first child destination, which is Phase 3.
 */
@Composable
fun SwirlApp(
    profile: UserProfile?,
    onSignOut: () -> Unit,
    miniPlayer: MiniPlayerUiState,
    onPlayPause: () -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * The home destination. A parameter, not a hard reference, so shell tests can render the shell
     * without Hilt or a network. Production uses the default.
     */
    homeContent: @Composable () -> Unit = {
        HomeRoute(nickname = profile?.nickname, onPlayTracks = onPlayTracks)
    },
) {
    val backStack = rememberNavBackStack(HomeKey)
    val currentKey = backStack.lastOrNull()
    val showChrome = currentKey != PlayingKey

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (showChrome) {
                Column {
                    MiniPlayerBar(
                        state = miniPlayer,
                        onPlayPause = onPlayPause,
                        onOpen = { backStack.add(PlayingKey) },
                    )
                    NavigationBar {
                        TopLevelDestination.entries.forEach { destination ->
                            NavigationBarItem(
                                selected = currentKey == destination.key,
                                onClick = {
                                    if (currentKey != destination.key) {
                                        backStack.clear()
                                        backStack.add(destination.key)
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = destination.icon,
                                        contentDescription = null,
                                    )
                                },
                                label = { Text(stringResource(destination.labelRes)) },
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.padding(innerPadding),
            entryProvider = entryProvider {
                entry<HomeKey> { homeContent() }
                entry<LibraryKey> { LibraryScreen() }
                entry<ProfileKey> { ProfileScreen(profile = profile, onSignOut = onSignOut) }
                entry<PlayingKey> { PlayingScreen(onBack = { backStack.removeLastOrNull() }) }
            },
        )
    }
}
