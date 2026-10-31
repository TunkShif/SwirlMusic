package one.tunkshif.swirlmusic.ui.tracks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import java.util.Locale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import one.tunkshif.swirlmusic.R
import one.tunkshif.swirlmusic.data.model.Track
import one.tunkshif.swirlmusic.ui.common.LoadError
import one.tunkshif.swirlmusic.ui.common.LoadingRows
import one.tunkshif.swirlmusic.ui.common.SectionError
import one.tunkshif.swirlmusic.player.PlayerController
import one.tunkshif.swirlmusic.ui.common.SectionState
import one.tunkshif.swirlmusic.ui.components.Artwork
import one.tunkshif.swirlmusic.ui.components.TrackRow
import one.tunkshif.swirlmusic.ui.theme.Spacing

@Composable
fun TrackListRoute(
    source: TrackListSource,
    onBack: () -> Unit,
    onPlayTracks: (List<Track>, Int, PlayerController.TrackPager?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrackListViewModel = hiltViewModel(),
) {
    LaunchedEffect(source) { viewModel.load(source) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    TrackListScreen(
        state = state,
        isAlbum = source is TrackListSource.Album,
        onBack = onBack,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
        onPlayTracks = { tracks, index ->
            onPlayTracks(tracks, index, viewModel.playPager())
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackListScreen(
    state: TrackListUiState,
    modifier: Modifier = Modifier,
    isAlbum: Boolean = false,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.title.ifBlank { stringResource(R.string.track_list_title) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Header(state)

            when (val tracks = state.tracks) {
                is SectionState.Loading -> LoadingRows()

                is SectionState.Failed -> SectionError(error = tracks.error, onRetry = onRetry)

                is SectionState.Content -> {
                    if (tracks.value.isEmpty()) {
                        Text(
                            text = stringResource(R.string.track_list_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(Spacing.xl),
                        )
                    } else {
                        val list = tracks.value
                        Button(
                            onClick = { onPlayTracks(list, 0) },
                            shape = MaterialTheme.shapes.extraLarge,
                            modifier = Modifier.padding(horizontal = Spacing.xl),
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Text(
                                text = stringResource(R.string.track_list_play_all),
                                modifier = Modifier.padding(start = Spacing.sm),
                            )
                        }
                        val listState = rememberLazyListState()
                        LaunchedEffect(listState, list.size) {
                            snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                                .collect { last ->
                                    if (state.canLoadMore && last != null && last >= list.size - 20) {
                                        onLoadMore()
                                    }
                                }
                        }
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(top = Spacing.md, bottom = Spacing.xxl),
                        ) {
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
private fun Header(state: TrackListUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(
                url = state.artworkUrl,
                contentDescription = null,
                modifier = Modifier.size(112.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                state.subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
                if (metaParts.isNotEmpty()) {
                    Text(
                        text = metaParts.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        @OptIn(ExperimentalMaterial3Api::class)
        state.description?.let { description ->
            var showSheet by remember { mutableStateOf(false) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.track_list_show_details),
                    ) { showSheet = true },
            ) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.track_list_more),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        painterResource(R.drawable.ic_chevron_right),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            if (showSheet) {
                ModalBottomSheet(onDismissRequest = { showSheet = false }) {
                    DetailSheetContent(state)
                }
            }
        }
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

@Composable
private fun SheetRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
