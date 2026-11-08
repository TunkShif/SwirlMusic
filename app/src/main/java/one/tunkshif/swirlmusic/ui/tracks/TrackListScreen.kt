package one.tunkshif.swirlmusic.ui.tracks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import one.tunkshif.swirlmusic.R
import one.tunkshif.swirlmusic.data.model.Track
import one.tunkshif.swirlmusic.player.PlayerController
import one.tunkshif.swirlmusic.ui.common.LoadError
import one.tunkshif.swirlmusic.ui.common.LoadingRows
import one.tunkshif.swirlmusic.ui.common.SectionError
import one.tunkshif.swirlmusic.ui.common.SectionState
import one.tunkshif.swirlmusic.ui.components.ArtistLine
import one.tunkshif.swirlmusic.ui.components.ToastPillHost
import one.tunkshif.swirlmusic.ui.components.TrackRow
import one.tunkshif.swirlmusic.ui.components.ToastPillState
import one.tunkshif.swirlmusic.ui.components.rememberToastPillState
import one.tunkshif.swirlmusic.ui.theme.Spacing

@Composable
fun TrackListRoute(
    source: TrackListSource,
    onBack: () -> Unit,
    onPlayTracks: (List<Track>, Int, PlayerController.TrackPager?) -> Unit,
    modifier: Modifier = Modifier,
    onOpenArtist: (Long) -> Unit = {},
    viewModel: TrackListViewModel = hiltViewModel(),
) {
    LaunchedEffect(source) { viewModel.load(source) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toastState = rememberToastPillState()

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            when (message) {
                TrackListMessage.Saved ->
                    toastState.show(R.drawable.ic_library_add_check, R.string.track_list_saved_toast)
                TrackListMessage.Removed ->
                    toastState.show(R.drawable.ic_library_add, R.string.track_list_removed_toast)
                TrackListMessage.AlbumLocked ->
                    toastState.show(R.drawable.ic_error, R.string.track_list_save_refused_toast)
                TrackListMessage.SaveFailed ->
                    toastState.show(R.drawable.ic_error, R.string.track_list_save_failed_toast)
            }
        }
    }

    TrackListScreen(
        state = state,
        isAlbum = source is TrackListSource.Album,
        onBack = onBack,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
        onToggleSave = viewModel::toggleSave,
        onOpenArtist = onOpenArtist,
        onPlayTracks = { tracks, index ->
            onPlayTracks(tracks, index, viewModel.playPager())
        },
        toastState = toastState,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackListScreen(
    state: TrackListUiState,
    modifier: Modifier = Modifier,
    isAlbum: Boolean = false,
    appBarState: TopAppBarState = rememberTopAppBarState(),
    toastState: ToastPillState = rememberToastPillState(),
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onToggleSave: () -> Unit = {},
    onOpenArtist: (Long) -> Unit = {},
    onPlayTracks: (List<Track>, Int) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        state = appBarState,
        snapAnimationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        flingAnimationSpec = rememberSplineBasedDecay(),
    )
    var showSheet by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CollapsingDetailHeader(
                title = state.title,
                artworkUrl = state.artworkUrl,
                appBarState = appBarState,
                onBack = onBack,
                onPlayAll = {
                    (state.tracks as? SectionState.Content)?.let { onPlayTracks(it.value, 0) }
                },
                subtitle = state.subtitle?.let { line ->
                    {
                        if (isAlbum) {
                            ArtistLine(
                                text = line,
                                artists = state.artists,
                                avatars = state.artistAvatars,
                                onOpenArtist = onOpenArtist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                        } else {
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                meta = headerMeta(state),
                description = state.description,
                onDescriptionClick = { showSheet = true },
                actions = {
                    if (isAlbum && state.saved != null) {
                        SaveAlbumButton(saved = state.saved, onToggle = onToggleSave)
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = rememberLazyListState().also { listState ->
                    val tracks = (state.tracks as? SectionState.Content)?.value
                    if (tracks != null) {
                        LaunchedEffect(listState, tracks.size) {
                            snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                                .collect { last ->
                                    if (state.canLoadMore && last != null && last >= tracks.size - 20) {
                                        onLoadMore()
                                    }
                                }
                        }
                    }
                },
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + Spacing.xxl,
                ),
            ) {
                when (val tracks = state.tracks) {
                    is SectionState.Loading -> item { LoadingRows() }
                    is SectionState.Failed -> item { SectionError(error = tracks.error, onRetry = onRetry) }
                    is SectionState.Content -> {
                        if (tracks.value.isEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.track_list_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(Spacing.xl),
                                )
                            }
                        } else {
                            val list = tracks.value
                            val rows = if (isAlbum) albumRows(list) else list.mapIndexed { i, t -> AlbumRow.Song(t, i, t.trackNumber ?: (i + 1)) }
                            itemsIndexed(rows, key = { i, row -> (row as? AlbumRow.Song)?.track?.id ?: -i.toLong() }) { _, row ->
                                when (row) {
                                    is AlbumRow.Disc -> Row(
                                        modifier = Modifier.padding(start = Spacing.lg, top = Spacing.sm, bottom = Spacing.xs),
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                    ) {
                                        Icon(painterResource(R.drawable.ic_album), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        Text(stringResource(R.string.track_list_disc_fmt, row.number), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                    is AlbumRow.Song -> TrackRow(
                                        track = row.track,
                                        onClick = { onPlayTracks(list, row.index) },
                                        leading = if (isAlbum) {
                                            { Text(row.number.toString(), style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.width(40.dp)) }
                                        } else null,
                                    )
                                }
                            }
                            if (state.canLoadMore || state.loadingMore || state.moreError != null) {
                                item {
                                    MoreFooter(
                                        loading = state.loadingMore,
                                        error = state.moreError,
                                        onRetry = onLoadMore,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            ToastPillHost(
                state = toastState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = Spacing.lg),
            )
        }
    }
    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            DetailSheetContent(state)
        }
    }
}

/** The "N tracks · Released …" line; null until either part is known. */
@Composable
private fun headerMeta(state: TrackListUiState): String? {
    val metaParts = listOfNotNull(
        state.trackCount?.let {
            pluralStringResource(R.plurals.library_track_count, it, it)
        },
        state.dateMillis?.let { dateMillis ->
            stringResource(
                if (state.dateKind == DateKind.Released) R.string.track_list_released_fmt
                else R.string.track_list_created_fmt,
                formatDetailDate(requireNotNull(state.dateKind), dateMillis, locale = LocalConfiguration.current.locales[0]),
            )
        },
    )
    return metaParts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** Round tonal toggle in the header's action row; both states carry explicit colours. */
@Composable
private fun SaveAlbumButton(saved: Boolean, onToggle: () -> Unit) {
    FilledTonalIconToggleButton(
        checked = saved,
        onCheckedChange = { onToggle() },
        shape = CircleShape,
        colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        modifier = Modifier.size(40.dp),
    ) {
        Icon(
            painter = painterResource(
                if (saved) R.drawable.ic_library_add_check else R.drawable.ic_library_add,
            ),
            contentDescription = stringResource(
                if (saved) R.string.track_list_remove_album else R.string.track_list_save_album,
            ),
        )
    }
}

@Composable
internal fun DetailSheetContent(state: TrackListUiState) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = Spacing.xl, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(state.title, style = MaterialTheme.typography.headlineSmall)
                            state.dateMillis?.let {
                                SheetRow(
                                    stringResource(
                                        if (state.dateKind == DateKind.Released) R.string.track_list_released_label
                                        else R.string.track_list_created_label,
                                    ),
                                    formatDetailDate(requireNotNull(state.dateKind), it, locale = LocalConfiguration.current.locales[0]),
                                )
                            }
                            state.company?.let {
                                SheetRow(stringResource(R.string.track_list_label), it)
                            }
                            state.albumType?.let {
                                SheetRow(stringResource(R.string.track_list_type), it)
                            }
                            if (state.tags.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                    state.tags.take(4).forEach {
                                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                            Text(it, style = MaterialTheme.typography.labelMedium,
                                                modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs))
                                        }
                                    }
                                }
                            }
        SelectionContainer {
            state.description?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MoreFooter(loading: Boolean, error: LoadError?, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.md),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            loading -> LoadingIndicator(modifier = Modifier.size(32.dp))
            error != null -> {
                Text(
                    text = stringResource(
                        when (error) {
                            LoadError.Network -> R.string.error_network
                            LoadError.Server -> R.string.error_server
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.action_retry))
                }
            }
        }
    }
}

@Composable
private fun SheetRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
