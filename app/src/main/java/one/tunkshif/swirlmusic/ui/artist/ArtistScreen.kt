package one.tunkshif.swirlmusic.ui.artist

import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.launch
import one.tunkshif.swirlmusic.R
import one.tunkshif.swirlmusic.data.model.ArtistRelease
import one.tunkshif.swirlmusic.data.model.ReleaseKind
import one.tunkshif.swirlmusic.data.model.Track
import one.tunkshif.swirlmusic.ui.common.LoadError
import one.tunkshif.swirlmusic.ui.common.LoadingRows
import one.tunkshif.swirlmusic.ui.common.SectionError
import one.tunkshif.swirlmusic.ui.common.SectionState
import one.tunkshif.swirlmusic.ui.components.CollectionAction
import one.tunkshif.swirlmusic.ui.components.CollectionToggle
import one.tunkshif.swirlmusic.ui.components.GridCard
import one.tunkshif.swirlmusic.ui.components.GridCardSkeleton
import one.tunkshif.swirlmusic.ui.components.ToastPillHost
import one.tunkshif.swirlmusic.ui.components.ToastPillState
import one.tunkshif.swirlmusic.ui.components.TrackRow
import one.tunkshif.swirlmusic.ui.components.rememberToastPillState
import one.tunkshif.swirlmusic.ui.theme.Spacing
import one.tunkshif.swirlmusic.ui.theme.AlbumColorTheme
import one.tunkshif.swirlmusic.ui.tracks.CollapsingDetailHeader

@Composable
fun ArtistRoute(
    id: Long,
    onBack: () -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    LaunchedEffect(id) { viewModel.load(id) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val seed by viewModel.seed.collectAsStateWithLifecycle()
    val nowPlayingId by viewModel.nowPlayingId.collectAsStateWithLifecycle()
    val toastState = rememberToastPillState()

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            when (message) {
                ArtistMessage.Followed ->
                    toastState.show(R.drawable.ic_check, R.string.artist_following)
                ArtistMessage.Unfollowed ->
                    toastState.show(R.drawable.ic_person_add, R.string.artist_unfollowed_toast)
                ArtistMessage.FollowFailed ->
                    toastState.show(R.drawable.ic_error, R.string.track_list_save_failed_toast)
            }
        }
    }

    AlbumColorTheme(seed = seed) {
    ArtistScreen(
        state = state,
        seed = seed,
        contentPadding = contentPadding,
        onBack = onBack,
        onRetry = viewModel::retry,
        onToggleFollow = viewModel::toggleFollow,
        onShowReleases = viewModel::showReleases,
        onRetryReleases = viewModel::retryReleases,
        onOpenAlbum = onOpenAlbum,
        onPlayTracks = onPlayTracks,
        onOpenDescription = viewModel::loadDescription,
        toastState = toastState,
        nowPlayingId = nowPlayingId,
        onSelectFilter = viewModel::selectFilter,
        modifier = modifier,
    )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    state: ArtistUiState,
    modifier: Modifier = Modifier,
    seed: Int? = null,
    contentPadding: PaddingValues = PaddingValues(),
    appBarState: TopAppBarState = rememberTopAppBarState(),
    pagerState: PagerState = rememberPagerState(pageCount = { 2 }),
    toastState: ToastPillState = rememberToastPillState(),
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onToggleFollow: () -> Unit = {},
    onShowReleases: () -> Unit = {},
    onRetryReleases: () -> Unit = {},
    onOpenAlbum: (Long) -> Unit = {},
    onOpenDescription: () -> Unit = {},
    nowPlayingId: Long? = null,
    onSelectFilter: (ReleaseFilter) -> Unit = {},
    onPlayTracks: (List<Track>, Int) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        state = appBarState,
        snapAnimationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        flingAnimationSpec = rememberSplineBasedDecay(),
    )
    var showSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val tabSurface = MaterialTheme.colorScheme.surface
    val tabSurfaceContainer = MaterialTheme.colorScheme.surfaceContainer

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                CollapsingDetailHeader(
                    title = state.name,
                    artworkUrl = state.avatarUrl,
                    appBarState = appBarState,
                    onBack = onBack,
                    onPlayAll = {
                        (state.topSongs as? SectionState.Content)?.let {
                            onPlayTracks(it.value, 0)
                        }
                    },
                    circularArtwork = true,
                    topInset = contentPadding.calculateTopPadding(),
                    loading = state.name.isBlank() && state.topSongs is SectionState.Loading,
                    hasCollectionAction = true,
                    meta = artistMeta(state),
                    description = state.briefDesc,
                    onDescriptionClick = {
                        showSheet = true
                        onOpenDescription()
                    },
                    leadingActions = {
                        CollectionAction(state = state.followState) {
                            CollectionToggle(
                                checked = state.followed,
                                onCheckedChange = { onToggleFollow() },
                                checkedIcon = R.drawable.ic_check,
                                uncheckedIcon = R.drawable.ic_person_add,
                                checkedLabel = R.string.artist_following,
                                uncheckedLabel = R.string.artist_follow,
                            )
                        }
                    },
                )
                // Same surface lerp as the header behind the tab row.
                PrimaryTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    modifier = Modifier.drawBehind {
                        drawRect(lerp(tabSurface, tabSurfaceContainer, appBarState.collapsedFraction))
                    },
                ) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                        text = { Text(stringResource(R.string.artist_tab_top_songs)) },
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                        text = { Text(stringResource(R.string.artist_tab_albums)) },
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
                    end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
                ),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    0 -> TopSongsPage(
                        state = state.topSongs,
                        contentPadding = innerPadding,
                        nowPlayingId = nowPlayingId,
                        outerPadding = contentPadding,
                        onRetry = onRetry,
                        onPlayTracks = onPlayTracks,
                    )
                    else -> AlbumsPage(
                        state = state,
                        contentPadding = innerPadding,
                        outerPadding = contentPadding,
                        onSelectFilter = onSelectFilter,
                        onShowReleases = onShowReleases,
                        onRetryReleases = onRetryReleases,
                        onOpenAlbum = onOpenAlbum,
                    )
                }
            }
            ToastPillHost(
                state = toastState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = contentPadding.calculateBottomPadding() + Spacing.lg),
            )
        }
    }
    }
    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            DescriptionSheetContent(state, onRetryDescription = onOpenDescription)
        }
    }
}

@Composable
private fun artistMeta(state: ArtistUiState): String? {
    val parts = listOfNotNull(
        state.songCount?.let { pluralStringResource(R.plurals.artist_song_count, it, it) },
        state.albumCount?.let { pluralStringResource(R.plurals.artist_album_count, it, it) },
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}


@Composable
private fun TopSongsPage(
    state: SectionState<List<Track>>,
    contentPadding: PaddingValues,
    nowPlayingId: Long? = null,
    outerPadding: PaddingValues = PaddingValues(),
    onRetry: () -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = rememberLazyListState(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + Spacing.md,
            bottom = contentPadding.calculateBottomPadding() + outerPadding.calculateBottomPadding() + Spacing.xxl,
            start = Spacing.md,
            end = Spacing.md,
        ),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        when (state) {
            is SectionState.Loading -> item { LoadingRows() }
            is SectionState.Failed -> item { SectionError(error = state.error, onRetry = onRetry) }
            is SectionState.Content -> {
                itemsIndexed(state.value, key = { _, t -> t.id }) { index, track ->
                    TrackRow(
                        track = track,
                        index = index,
                        count = state.value.size,
                        selected = track.id == nowPlayingId,
                        onClick = { onPlayTracks(state.value, index) },
                        supportingText = track.albumName?.takeIf { it.isNotBlank() }
                            ?: track.artistLine,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AlbumsPage(
    state: ArtistUiState,
    contentPadding: PaddingValues,
    outerPadding: PaddingValues = PaddingValues(),
    onShowReleases: () -> Unit,
    onSelectFilter: (ReleaseFilter) -> Unit = {},
    onRetryReleases: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
) {
    LaunchedEffect(Unit) { onShowReleases() }
    val filtered = state.filteredReleases()
    Column(modifier = Modifier.fillMaxSize()) {
        // Pinned single-select filter row (0011-D49), above the scrolling grid.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = contentPadding.calculateTopPadding())
                .horizontalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            state.availableFilters().forEach { f ->
                FilterChip(
                    selected = state.filter == f,
                    onClick = { onSelectFilter(f) },
                    shape = CircleShape,
                    label = {
                        Text(
                            stringResource(
                                when (f) {
                                    ReleaseFilter.All -> R.string.artist_filter_all
                                    ReleaseFilter.Albums -> R.string.artist_filter_albums
                                    ReleaseFilter.Eps -> R.string.artist_filter_eps
                                    ReleaseFilter.Singles -> R.string.artist_filter_singles
                                },
                            ),
                        )
                    },
                    leadingIcon = if (state.filter == f) {
                        { Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                )
            }
        }
        when {
        state.releases.isEmpty() && state.releasesLoading -> ReleasesSkeleton(
            contentPadding = contentPadding,
            outerPadding = outerPadding,
        )
        state.releases.isEmpty() && state.releasesError != null -> Column(
            modifier = Modifier.padding(bottom = outerPadding.calculateBottomPadding()),
        ) {
            SectionError(error = state.releasesError, onRetry = onRetryReleases)
        }
        filtered.isEmpty() && state.releasesLoading -> Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.xl),
            horizontalArrangement = Arrangement.Center,
        ) { LoadingIndicator(modifier = Modifier.size(32.dp)) }
        filtered.isEmpty() && state.releasesComplete -> Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = outerPadding.calculateBottomPadding())
                .padding(Spacing.xl),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.artist_empty_filtered),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        state.releases.isEmpty() && state.releasesComplete -> Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = outerPadding.calculateBottomPadding())
                .padding(Spacing.xl),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.artist_empty_albums),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 152.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            // Top inset is already carried by the pinned filter row above.
            contentPadding = PaddingValues(
                top = Spacing.md,
                bottom = contentPadding.calculateBottomPadding() + outerPadding.calculateBottomPadding() + Spacing.md,
                start = Spacing.md,
                end = Spacing.md,
            ),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(filtered, key = { it.id }) { release ->
                GridCard(
                    title = release.name,
                    subtitle = releaseSubtitle(release),
                    artworkUrl = release.artworkUrl,
                    circularArtwork = false,
                    onClick = { onOpenAlbum(release.id) },
                )
            }
            if (state.releasesError != null || state.releasesLoading) {
                // One cell would park the indicator at the start; span the whole row.
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.md),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (state.releasesLoading) {
                            LoadingIndicator(modifier = Modifier.size(32.dp))
                        } else {
                            Text(
                                text = stringResource(
                                    when (state.releasesError) {
                                        LoadError.Network -> R.string.error_network
                                        else -> R.string.error_server
                                    },
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = onRetryReleases) {
                                Text(stringResource(R.string.action_retry))
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

/** "year · kind": UTC year; Other shows the year only; no year shows the kind only. */
@Composable
private fun releaseSubtitle(release: ArtistRelease): String? {
    val year = release.publishedAt
        ?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).year.toString() }
    val kind = when (release.kind) {
        ReleaseKind.Album -> stringResource(R.string.artist_kind_album)
        ReleaseKind.Ep -> stringResource(R.string.artist_kind_ep)
        ReleaseKind.Single -> stringResource(R.string.artist_kind_single)
        ReleaseKind.Other -> null
    }
    return listOfNotNull(year, kind).takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/**
 * The releases-grid skeleton: a real [LazyVerticalGrid] with the same cells and insets as the
 * loaded grid, so the column count matches. Scroll is off; it is a placeholder.
 */
@Composable
private fun ColumnScope.ReleasesSkeleton(
    contentPadding: PaddingValues,
    outerPadding: PaddingValues,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 152.dp),
        modifier = Modifier.weight(1f).fillMaxWidth(),
        userScrollEnabled = false,
        contentPadding = PaddingValues(
            top = Spacing.md,
            bottom = contentPadding.calculateBottomPadding() + outerPadding.calculateBottomPadding() + Spacing.md,
            start = Spacing.md,
            end = Spacing.md,
        ),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(6) { GridCardSkeleton(circularArtwork = false) }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DescriptionSheetContent(state: ArtistUiState, onRetryDescription: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = Spacing.xl, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(state.name, style = MaterialTheme.typography.headlineSmall)
        when (val description = state.description) {
            is DescriptionState.Loaded -> {
                if (description.value.sections.isNotEmpty()) {
                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            description.value.sections.forEach { section ->
                                if (section.title.isNotBlank()) {
                                    Text(section.title, style = MaterialTheme.typography.titleMedium)
                                }
                                Text(section.body, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                } else {
                    state.briefDesc?.let {
                        Text(it, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            is DescriptionState.Loading -> {
                state.briefDesc?.let {
                    Text(it, style = MaterialTheme.typography.bodyLarge)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    LoadingIndicator(modifier = Modifier.size(32.dp))
                }
            }
            is DescriptionState.Failed -> {
                state.briefDesc?.let {
                    Text(it, style = MaterialTheme.typography.bodyLarge)
                }
                SectionError(error = description.error, onRetry = onRetryDescription)
            }
            DescriptionState.Idle -> {
                state.briefDesc?.let {
                    Text(it, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
