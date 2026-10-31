package one.tunkshif.swirlmusic.ui.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import one.tunkshif.swirlmusic.R
import one.tunkshif.swirlmusic.data.model.PlayedEntry
import one.tunkshif.swirlmusic.data.model.Track
import one.tunkshif.swirlmusic.ui.common.PagedList
import one.tunkshif.swirlmusic.ui.common.SectionError
import one.tunkshif.swirlmusic.ui.common.SectionState
import one.tunkshif.swirlmusic.ui.components.Artwork
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.xl, end = Spacing.xl, top = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.library_title),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.weight(1f),
            )
            FilledTonalIconButton(
                onClick = onOpenSearch,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = stringResource(R.string.library_search_hint),
                )
            }
        }

        TabPills(
            selected = state.tab,
            onSelect = onSelectTab,
            modifier = Modifier.padding(vertical = Spacing.md),
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            when (state.tab) {
                LibraryTab.Playlists -> PlaylistList(
                    paged = state.playlists,
                    onOpen = onOpenPlaylist,
                    onRetry = { onRetry(LibraryTab.Playlists) },
                    onLoadMore = { onLoadMore(LibraryTab.Playlists) },
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
                    circularArtwork = false,
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
                    circularArtwork = true,
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TabPills(
    selected: LibraryTab,
    onSelect: (LibraryTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        LibraryTab.entries.forEach { tab ->
            val isSelected = selected == tab
            val container by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "tabContainer",
            )
            val content = if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    onClick = { onSelect(tab) },
                    shape = CircleShape,
                    color = container,
                    contentColor = content,
                    modifier = Modifier.semantics {
                        role = Role.Tab
                        this.selected = isSelected
                    },
                ) {
                    Box(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .padding(horizontal = Spacing.lg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(tab.labelRes()),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .padding(top = Spacing.xs)
                        .width(16.dp)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else Color.Transparent,
                        ),
                )
            }
        }
    }
}

/** One row in a list tab: card with artwork, title, subtitle, and a trailing slot. */
@Composable
private fun ListCard(
    title: String,
    subtitle: String?,
    artworkUrl: String?,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Artwork(
                url = artworkUrl,
                contentDescription = null,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.size(56.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailing?.invoke()
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlaylistList(
    paged: PagedList<one.tunkshif.swirlmusic.data.model.PlaylistSummary>,
    onOpen: (Long) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    when {
        paged.isFirstLoad -> ListSkeleton()

        paged.state is SectionState.Failed -> CenteredState {
            SectionError(error = (paged.state as SectionState.Failed).error, onRetry = onRetry)
        }

        paged.items.isEmpty() -> CenteredState {
            EmptyText(R.string.library_empty_playlists)
        }

        else -> {
            val sections = playlistSections(paged.items, complete = !paged.hasMore)
            LazyColumn(
                contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                sections.liked?.let { liked ->
                    item(key = "liked") { LikedCard(liked, onOpen) }
                }
                if (sections.created.isNotEmpty()) {
                    item(key = "h-created") {
                        SectionHeader(stringResource(R.string.library_section_created), sections.createdCount)
                    }
                    itemsIndexed(sections.created, key = { _, it -> it.id }) { _, playlist ->
                        PlaylistCard(playlist, onOpen)
                    }
                }
                if (sections.saved.isNotEmpty()) {
                    item(key = "h-saved") {
                        SectionHeader(stringResource(R.string.library_section_saved), sections.savedCount)
                    }
                    itemsIndexed(sections.saved, key = { _, it -> it.id }) { _, playlist ->
                        ListCard(
                            title = playlist.name,
                            subtitle = listOfNotNull(
                                playlist.creatorName?.let {
                                    stringResource(R.string.library_by_creator, it)
                                },
                                pluralStringResource(
                                    R.plurals.library_track_count,
                                    playlist.trackCount,
                                    playlist.trackCount,
                                ),
                            ).joinToString(" · "),
                            artworkUrl = playlist.artworkUrl,
                            onClick = { onOpen(playlist.id) },
                        )
                    }
                }
                if (paged.hasMore) {
                item {
                    LaunchedEffect(Unit) { onLoadMore() }
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        LoadingIndicator(modifier = Modifier.padding(Spacing.md))
                    }
                }
            }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> CardGrid(
    paged: PagedList<T>,
    cards: List<LibraryCard>,
    onOpen: (Long) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    emptyRes: Int,
    circularArtwork: Boolean,
) {
    when {
        paged.isFirstLoad -> GridSkeleton(circular = circularArtwork)

        paged.state is SectionState.Failed -> CenteredState {
            SectionError(error = (paged.state as SectionState.Failed).error, onRetry = onRetry)
        }

        cards.isEmpty() -> CenteredState { EmptyText(emptyRes) }

        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 152.dp),
            contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(cards, key = { it.id }) { card ->
                GridCard(card = card, circularArtwork = circularArtwork, onClick = { onOpen(card.id) })
            }
            if (paged.hasMore) {
                item {
                    LaunchedEffect(Unit) { onLoadMore() }
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        LoadingIndicator(modifier = Modifier.padding(Spacing.md))
                    }
                }
            }
        }
    }
}

@Composable
private fun GridCard(
    card: LibraryCard,
    circularArtwork: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            horizontalAlignment = if (circularArtwork) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            Artwork(
                url = card.artworkUrl,
                contentDescription = null,
                shape = if (circularArtwork) CircleShape else MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
            Text(
                text = card.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (circularArtwork) TextAlign.Center else null,
                modifier = Modifier.padding(top = Spacing.sm),
            )
            if (card.subtitle != null) {
                Text(
                    text = card.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = if (circularArtwork) TextAlign.Center else null,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RecentList(
    state: SectionState<List<PlayedEntry>>,
    onRetry: () -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
) {
    when (state) {
        is SectionState.Loading -> ListSkeleton()

        is SectionState.Failed -> CenteredState {
            SectionError(error = state.error, onRetry = onRetry)
        }

        is SectionState.Content -> {
            if (state.value.isEmpty()) {
                CenteredState { EmptyText(R.string.library_empty_recent) }
            } else {
                val entries = state.value
                val tracks = entries.map { it.track }
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    item {
                        Button(
                            onClick = { onPlayTracks(tracks, 0) },
                            colors = ButtonDefaults.filledTonalButtonColors(),
                            shape = CircleShape,
                            modifier = Modifier.padding(bottom = Spacing.xs),
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = stringResource(R.string.library_play_all),
                                modifier = Modifier.padding(start = Spacing.sm),
                            )
                        }
                    }
                    itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                        ListCard(
                            title = track.title,
                            subtitle = track.artistLine,
                            artworkUrl = track.artworkUrl,
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

@Composable
private fun CenteredState(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun EmptyText(emptyRes: Int) {
    Text(
        text = stringResource(emptyRes),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ListSkeleton() {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        repeat(4) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                )
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Box(
                        modifier = Modifier
                            .width(140.dp)
                            .height(16.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    )
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(12.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    )
                }
            }
        }
    }
}

@Composable
private fun GridSkeleton(circular: Boolean) {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                repeat(2) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(Spacing.md),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(if (circular) CircleShape else MaterialTheme.shapes.large)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        )
                        Box(
                            modifier = Modifier
                                .padding(top = Spacing.sm)
                                .width(90.dp)
                                .height(14.dp)
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
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

@Composable
private fun PlaylistCard(
    playlist: one.tunkshif.swirlmusic.data.model.PlaylistSummary,
    onOpen: (Long) -> Unit,
) {
    ListCard(
        title = playlist.name,
        subtitle = pluralStringResource(
            R.plurals.library_track_count,
            playlist.trackCount,
            playlist.trackCount,
        ),
        artworkUrl = playlist.artworkUrl,
        onClick = { onOpen(playlist.id) },
    )
}

@Composable
private fun SectionHeader(title: String, count: Int?) {
    Text(
        text = count?.let { stringResource(R.string.library_section_count_fmt, title, it) } ?: title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.lg, top = Spacing.xs),
    )
}

@Composable
private fun LikedCard(
    playlist: one.tunkshif.swirlmusic.data.model.PlaylistSummary,
    onOpen: (Long) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable { onOpen(playlist.id) },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Box {
                Artwork(
                    url = playlist.artworkUrl,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                )
                Icon(
                    painterResource(R.drawable.ic_favorite),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.BottomEnd),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(
                    playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.library_track_count,
                        playlist.trackCount,
                        playlist.trackCount,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                )
            }
        }
    }
}
