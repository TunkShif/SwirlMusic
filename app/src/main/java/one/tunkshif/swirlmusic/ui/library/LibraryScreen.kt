package one.tunkshif.swirlmusic.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import one.tunkshif.swirlmusic.R
import one.tunkshif.swirlmusic.data.model.PlayedEntry
import one.tunkshif.swirlmusic.data.model.Track
import one.tunkshif.swirlmusic.ui.common.LoadingCards
import one.tunkshif.swirlmusic.ui.common.LoadingRows
import one.tunkshif.swirlmusic.ui.common.PagedList
import one.tunkshif.swirlmusic.ui.common.SectionError
import one.tunkshif.swirlmusic.ui.common.SectionState
import one.tunkshif.swirlmusic.ui.components.MediaCard
import one.tunkshif.swirlmusic.ui.components.TrackRow
import one.tunkshif.swirlmusic.ui.format.formatCount
import one.tunkshif.swirlmusic.ui.theme.Spacing

/** Everything a grid cell needs, so one grid serves playlists, albums, and artists. */
private data class LibraryCard(
    val id: Long,
    val title: String,
    val subtitle: String?,
    val artworkUrl: String?,
)

@Composable
fun LibraryRoute(
    onOpenSearch: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LibraryScreen(
        state = state,
        onSelectTab = viewModel::selectTab,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
        onOpenSearch = onOpenSearch,
        onOpenPlaylist = onOpenPlaylist,
        onOpenAlbum = onOpenAlbum,
        onOpenArtist = onOpenArtist,
        onPlayTracks = onPlayTracks,
        modifier = modifier,
    )
}

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onSelectTab: (LibraryTab) -> Unit,
    onRetry: (LibraryTab) -> Unit,
    onLoadMore: (LibraryTab) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LibraryContent(
            state = state,
            onSelectTab = onSelectTab,
            onRetry = onRetry,
            onLoadMore = onLoadMore,
            onOpenSearch = onOpenSearch,
            onOpenPlaylist = onOpenPlaylist,
            onOpenAlbum = onOpenAlbum,
            onOpenArtist = onOpenArtist,
            onPlayTracks = onPlayTracks,
        )
    }
}

@Composable
private fun LibraryContent(
    state: LibraryUiState,
    onSelectTab: (LibraryTab) -> Unit,
    onRetry: (LibraryTab) -> Unit,
    onLoadMore: (LibraryTab) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.library_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = Spacing.xl, end = Spacing.xl, top = Spacing.lg),
        )

        SearchEntry(
            onClick = onOpenSearch,
            modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.md),
        )

        val tabs = LibraryTab.entries
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl),
        ) {
            tabs.forEachIndexed { index, tab ->
                SegmentedButton(
                    selected = state.tab == tab,
                    onClick = { onSelectTab(tab) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                    label = { Text(stringResource(tab.labelRes())) },
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when (state.tab) {
                LibraryTab.Playlists -> CardGrid(
                    paged = state.playlists,
                    cards = state.playlists.items.map {
                        LibraryCard(
                            id = it.id,
                            title = it.name,
                            subtitle = null,
                            artworkUrl = it.artworkUrl,
                        )
                    },
                    onOpen = onOpenPlaylist,
                    onRetry = { onRetry(LibraryTab.Playlists) },
                    onLoadMore = { onLoadMore(LibraryTab.Playlists) },
                    emptyRes = R.string.library_empty_playlists,
                )

                LibraryTab.Albums -> CardGrid(
                    paged = state.albums,
                    cards = state.albums.items.map {
                        LibraryCard(it.id, it.name, it.artistLine, it.artworkUrl)
                    },
                    onOpen = onOpenAlbum,
                    onRetry = { onRetry(LibraryTab.Albums) },
                    onLoadMore = { onLoadMore(LibraryTab.Albums) },
                    emptyRes = R.string.library_empty_albums,
                )

                LibraryTab.Artists -> CardGrid(
                    paged = state.artists,
                    cards = state.artists.items.map {
                        LibraryCard(it.id, it.name, null, it.artworkUrl)
                    },
                    onOpen = onOpenArtist,
                    onRetry = { onRetry(LibraryTab.Artists) },
                    onLoadMore = { onLoadMore(LibraryTab.Artists) },
                    emptyRes = R.string.library_empty_artists,
                )

                LibraryTab.Recent -> RecentList(
                    state = state.recent,
                    onRetry = { onRetry(LibraryTab.Recent) },
                    onPlayTracks = onPlayTracks,
                )
            }
        }
    }
}

@Composable
private fun SearchEntry(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text = stringResource(R.string.library_search_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
        )
    }
}

@Composable
private fun <T> CardGrid(
    paged: PagedList<T>,
    cards: List<LibraryCard>,
    onOpen: (Long) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    emptyRes: Int,
) {
    when {
        paged.isFirstLoad -> LoadingCards()

        paged.state is SectionState.Failed -> SectionError(
            error = (paged.state as SectionState.Failed).error,
            onRetry = onRetry,
        )

        cards.isEmpty() -> Text(
            text = stringResource(emptyRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(Spacing.xl),
        )

        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 108.dp),
            contentPadding = PaddingValues(Spacing.xl),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            items(cards, key = { it.id }) { card ->
                MediaCard(
                    title = card.title,
                    subtitle = card.subtitle,
                    artworkUrl = card.artworkUrl,
                    width = 108.dp,
                    onClick = { onOpen(card.id) },
                )
            }
            if (paged.hasMore) {
                item {
                    LaunchedEffect(Unit) { onLoadMore() }
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.padding(Spacing.md))
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentList(
    state: SectionState<List<PlayedEntry>>,
    onRetry: () -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
) {
    when (state) {
        is SectionState.Loading -> LoadingRows()

        is SectionState.Failed -> SectionError(error = state.error, onRetry = onRetry)

        is SectionState.Content -> {
            if (state.value.isEmpty()) {
                Text(
                    text = stringResource(R.string.library_empty_recent),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.xl),
                )
            } else {
                val entries = state.value
                val tracks = entries.map { it.track }
                LazyColumn(contentPadding = PaddingValues(bottom = Spacing.xxl)) {
                    itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                        TrackRow(
                            track = track,
                            onClick = { onPlayTracks(tracks, index) },
                            trailing = {
                                Text(
                                    text = stringResource(
                                        R.string.library_play_count,
                                        formatCount(entries[index].playCount.toLong()),
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun LibraryTab.labelRes(): Int = when (this) {
    LibraryTab.Playlists -> R.string.library_tab_playlists
    LibraryTab.Albums -> R.string.library_tab_albums
    LibraryTab.Artists -> R.string.library_tab_artists
    LibraryTab.Recent -> R.string.library_tab_recent
}
