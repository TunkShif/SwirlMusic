package one.tunkshif.swirlmusic.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEvent
import one.tunkshif.swirlmusic.R
import one.tunkshif.swirlmusic.navigation.AlbumKey
import one.tunkshif.swirlmusic.navigation.ArtistKey
import one.tunkshif.swirlmusic.navigation.HomeKey
import one.tunkshif.swirlmusic.navigation.LibraryKey
import one.tunkshif.swirlmusic.navigation.PlayingKey
import one.tunkshif.swirlmusic.navigation.PlaylistKey
import one.tunkshif.swirlmusic.navigation.SearchKey
import one.tunkshif.swirlmusic.navigation.ProfileKey
import one.tunkshif.swirlmusic.navigation.TopLevelDestination
import one.tunkshif.swirlmusic.data.model.Track
import one.tunkshif.swirlmusic.ui.components.LocalSharedTransitionScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import one.tunkshif.swirlmusic.ui.components.MiniPlayerBar
import one.tunkshif.swirlmusic.ui.player.QueueEvent
import one.tunkshif.swirlmusic.ui.playing.QueueSheet
import one.tunkshif.swirlmusic.ui.playing.QueueUiState
import one.tunkshif.swirlmusic.ui.player.MiniPlayerUiState
import one.tunkshif.swirlmusic.player.PlayerController
import one.tunkshif.swirlmusic.data.model.UserProfile
import one.tunkshif.swirlmusic.ui.home.HomeRoute
import one.tunkshif.swirlmusic.ui.home.HomeViewModel
import one.tunkshif.swirlmusic.ui.library.LibraryRoute
import one.tunkshif.swirlmusic.ui.library.LibraryViewModel
import one.tunkshif.swirlmusic.ui.search.SearchRoute
import one.tunkshif.swirlmusic.ui.artist.ArtistRoute
import one.tunkshif.swirlmusic.ui.tracks.TrackListRoute
import one.tunkshif.swirlmusic.ui.theme.Spacing
import one.tunkshif.swirlmusic.ui.tracks.TrackListSource
import one.tunkshif.swirlmusic.ui.playing.NowPlayingRoute
import one.tunkshif.swirlmusic.ui.theme.AlbumColorTheme
import one.tunkshif.swirlmusic.ui.profile.ProfileScreen

/** Every screen shows the dock except the full-screen player. */
internal fun showsDock(key: NavKey?): Boolean = key !is PlayingKey

/**
 * The tab that owns the current stack: the stack's root. A playlist opened from Library keeps
 * Library highlighted; an empty stack selects nothing.
 */
internal fun selectedTab(backStack: List<NavKey>): TopLevelDestination? =
    TopLevelDestination.entries.firstOrNull { it.key == backStack.firstOrNull() }

/**
 * The app shell: navigation, bottom bar, and mini player.
 *
 * One back stack, owned by [rememberNavBackStack], so it survives rotation and process death.
 * Tapping a tab resets the stack to that tab's root — including tapping the selected tab while
 * on a detail screen, which pops back to the root.
 */
@Composable
fun SwirlApp(
    profile: UserProfile?,
    onSignOut: () -> Unit,
    miniPlayer: MiniPlayerUiState,
    onPlayPause: () -> Unit,
    onPlayTracks: (List<Track>, Int, PlayerController.TrackPager?) -> Unit,
    modifier: Modifier = Modifier,
    /** Artwork colour seed re-theming the mini player and Now Playing; null keeps the app theme. */
    albumSeed: Int? = null,
    /**
     * Activity-scoped tab ViewModels, obtained in SwirlRoot. They must NOT be created inside a
     * NavEntry: a tab switch clears the back stack, so entry-scoped stores would drop them,
     * refetching and losing scroll on every tab change.
     */
    homeViewModel: HomeViewModel? = null,
    libraryViewModel: LibraryViewModel? = null,
    /**
     * The home destination. A parameter, not a hard reference, so shell tests can render the shell
     * without Hilt or a network. Production uses the default.
     */
    homeContent: @Composable () -> Unit = {
        HomeRoute(
            nickname = profile?.nickname,
            onPlayTracks = { t, i -> onPlayTracks(t, i, null) },
            viewModel = homeViewModel ?: hiltViewModel(),
        )
    },
    libraryContent: @Composable (openPlaylist: (Long) -> Unit, openAlbum: (Long) -> Unit, openArtist: (Long) -> Unit, openSearch: () -> Unit) -> Unit = { openPlaylist, openAlbum, openArtist, openSearch ->
        LibraryRoute(
            onOpenSearch = openSearch,
            onOpenPlaylist = openPlaylist,
            onOpenAlbum = openAlbum,
            onOpenArtist = openArtist,
            onPlayTracks = { t, i -> onPlayTracks(t, i, null) },
            viewModel = libraryViewModel ?: hiltViewModel(),
        )
    },
    /**
     * The full-screen player. A parameter, like [homeContent], so shell tests can render the
     * screen with a fixed state and no Hilt.
     */
    queue: QueueUiState = QueueUiState(),
    onQueueEvent: (QueueEvent) -> Unit = {},
    playingContent: @Composable (
        onBack: () -> Unit,
        onOpenAlbum: (Long) -> Unit,
        onOpenArtist: (Long) -> Unit,
        onOpenQueue: () -> Unit,
    ) -> Unit =
        { onBack, onOpenAlbum, onOpenArtist, onOpenQueue ->
            NowPlayingRoute(
                onBack = onBack,
                onOpenAlbum = onOpenAlbum,
                onOpenArtist = onOpenArtist,
                onOpenQueue = onOpenQueue,
            )
        },
) {
    var showQueue by rememberSaveable { mutableStateOf(false) }
    // An empty queue means playback ended; the sheet has nothing to show.
    LaunchedEffect(queue.items.isEmpty()) {
        if (queue.items.isEmpty()) showQueue = false
    }

    val backStack = rememberNavBackStack(HomeKey)
    val currentKey = backStack.lastOrNull()
    val selectedDestination = selectedTab(backStack)
    val showChrome = showsDock(currentKey)

    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            Scaffold(
                modifier = modifier.fillMaxSize(),
                bottomBar = {
                    // Two floating pieces: the mini player is its own coloured pill above the
                    // rounded navigation bar, the way the reference does it.
                    AnimatedVisibility(
                        visible = showChrome,
                        enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it } +
                            fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                        exit = slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it } +
                            fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    ) {
                        Column(
                            modifier = Modifier
                                .navigationBarsPadding()
                                .padding(horizontal = Spacing.md)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            // The dock's scope, not the bar's inner one: the shared artwork
                            // rides the dock sliding away, not the bar appearing.
                            val dockScope = this@AnimatedVisibility
                            AnimatedVisibility(
                                visible = miniPlayer.hasTrack,
                                enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                                    fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                                exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) +
                                    fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
                            ) {
                                AlbumColorTheme(albumSeed) {
                                    MiniPlayerBar(
                                        state = miniPlayer,
                                        onPlayPause = onPlayPause,
                                        onOpen = { backStack.add(PlayingKey) },
                                        onOpenQueue = { showQueue = true },
                                        animatedVisibilityScope = dockScope,
                                    )
                                }
                            }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 3.dp,
                    ) {
                        NavigationBar(containerColor = Color.Transparent) {
                            TopLevelDestination.entries.forEach { destination ->
                                NavigationBarItem(
                                    selected = selectedDestination == destination,
                                    onClick = {
                                        if (backStack != listOf(destination.key)) {
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
            }
        },
    ) { innerPadding ->
        // Per-entry insets: Playing draws edge to edge, everything else stays inside the bars.
        @Composable
        fun padded(content: @Composable () -> Unit) {
            Box(modifier = Modifier.padding(innerPadding)) { content() }
        }
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            // Per-entry saved state and ViewModel stores, so a detail screen never shows the
            // previous list's ViewModel. Tab ViewModels are hoisted to SwirlRoot on purpose —
            // a tab switch clears the stack and must not refetch or lose scroll.
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<HomeKey> { padded { homeContent() } }
                entry<LibraryKey> {
                    padded {
                        libraryContent(
                            { id -> backStack.add(PlaylistKey(id)) },
                            { id -> backStack.add(AlbumKey(id)) },
                            { id -> backStack.add(ArtistKey(id)) },
                            { backStack.add(SearchKey) },
                        )
                    }
                }
                entry<PlaylistKey> { key ->
                    padded {
                        TrackListRoute(
                            source = TrackListSource.Playlist(key.id),
                            onBack = { backStack.removeLastOrNull() },
                            onPlayTracks = onPlayTracks,
                        )
                    }
                }
                entry<AlbumKey> { key ->
                    padded {
                        TrackListRoute(
                            source = TrackListSource.Album(key.id),
                            onBack = { backStack.removeLastOrNull() },
                            onPlayTracks = onPlayTracks,
                            onOpenArtist = { id -> backStack.add(ArtistKey(id)) },
                        )
                    }
                }
                entry<ArtistKey> { key ->
                    padded {
                        ArtistRoute(
                            id = key.id,
                            onBack = { backStack.removeLastOrNull() },
                            onPlayTracks = { tracks, index -> onPlayTracks(tracks, index, null) },
                            onOpenAlbum = { id -> backStack.add(AlbumKey(id)) },
                        )
                    }
                }
                entry<SearchKey> {
                    padded {
                        SearchRoute(
                            onBack = { backStack.removeLastOrNull() },
                            onOpenPlaylist = { id -> backStack.add(PlaylistKey(id)) },
                            onOpenAlbum = { id -> backStack.add(AlbumKey(id)) },
                            onOpenArtist = { id -> backStack.add(ArtistKey(id)) },
                            onPlayTracks = { t, i -> onPlayTracks(t, i, null) },
                        )
                    }
                }
                entry<ProfileKey> {
                    padded { ProfileScreen(profile = profile, onSignOut = onSignOut) }
                }
                entry<PlayingKey>(
                    // Not a composable context, so the default easing specs are used.
                    metadata = NavDisplay.transitionSpec {
                        // The screen underneath stays put so the dock and the artwork can travel.
                        (fadeIn() + slideInVertically { it / 6 }) togetherWith
                            ExitTransition.KeepUntilTransitionsFinished
                    } + NavDisplay.popTransitionSpec {
                        EnterTransition.None togetherWith
                            (fadeOut() + slideOutVertically { it / 6 })
                    } + NavDisplay.predictivePopTransitionSpec { edge ->
                        // Our drag-down collapse feeds EDGE_NONE events: the player slides
                        // off with the finger; system edge-back keeps the fade.
                        if (edge == NavigationEvent.EDGE_NONE) {
                            EnterTransition.None togetherWith slideOutVertically { it }
                        } else {
                            EnterTransition.None togetherWith fadeOut()
                        }
                    },
                ) {
                    AlbumColorTheme(albumSeed) {
                        playingContent(
                            { backStack.removeLastOrNull() },
                            { id ->
                                backStack.removeLastOrNull()
                                backStack.add(AlbumKey(id))
                            },
                            { id ->
                                backStack.removeLastOrNull()
                                backStack.add(ArtistKey(id))
                            },
                            { showQueue = true },
                        )
                    }
                }
            },
        )
        }
        }
    }

    if (showQueue) {
        QueueSheet(
            queue = queue,
            onEvent = onQueueEvent,
            onDismiss = { showQueue = false },
        )
    }
}
