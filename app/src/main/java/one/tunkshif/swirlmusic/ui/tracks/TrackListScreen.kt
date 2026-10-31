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
import androidx.compose.ui.res.stringResource
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
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    modifier: Modifier = Modifier,
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
                            itemsIndexed(list, key = { _, track -> track.id }) { index, track ->
                                TrackRow(
                                    track = track,
                                    onClick = { onPlayTracks(list, index) },
                                )
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl, vertical = Spacing.md),
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
            val subtitleParts = listOfNotNull(
                state.subtitle,
                state.trackCount?.let {
                    pluralStringResource(R.plurals.library_track_count, it, it)
                },
            )
            if (subtitleParts.isNotEmpty()) {
                Text(
                    text = subtitleParts.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
