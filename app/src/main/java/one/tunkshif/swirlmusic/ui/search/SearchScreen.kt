package one.tunkshif.swirlmusic.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import one.tunkshif.swirlmusic.R
import one.tunkshif.swirlmusic.data.model.SearchResults
import one.tunkshif.swirlmusic.data.model.SearchType
import one.tunkshif.swirlmusic.data.model.Track
import one.tunkshif.swirlmusic.ui.common.EmptyMessage
import one.tunkshif.swirlmusic.ui.common.LoadingRows
import one.tunkshif.swirlmusic.ui.common.SectionError
import one.tunkshif.swirlmusic.ui.common.SectionState
import one.tunkshif.swirlmusic.ui.components.MediaCard
import one.tunkshif.swirlmusic.ui.components.TrackRow
import one.tunkshif.swirlmusic.ui.theme.Spacing

@Composable
fun SearchRoute(
    onBack: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SearchScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onSubmit = viewModel::submit,
        onSelectType = viewModel::selectType,
        onSelectHistory = viewModel::selectHistory,
        onClearHistory = viewModel::clearHistory,
        onBack = onBack,
        onOpenPlaylist = onOpenPlaylist,
        onOpenAlbum = onOpenAlbum,
        onOpenArtist = onOpenArtist,
        onPlayTracks = onPlayTracks,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onSelectType: (SearchType) -> Unit,
    onSelectHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onBack: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.search_placeholder)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                        modifier = Modifier.fillMaxWidth(),
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
            val types = SearchType.entries
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xl, vertical = Spacing.sm),
            ) {
                types.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = state.type == type,
                        onClick = { onSelectType(type) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = types.size),
                        label = { Text(stringResource(type.labelRes())) },
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when {
                    state.searching -> LoadingRows()

                    state.results is SectionState.Failed -> SectionError(
                        error = (state.results as SectionState.Failed).error,
                        onRetry = onSubmit,
                    )

                    !state.submitted -> History(
                        history = state.history,
                        onSelect = onSelectHistory,
                        onClear = onClearHistory,
                    )

                    else -> Results(
                        results = (state.results as? SectionState.Content)?.value
                            ?: SearchResults(),
                        type = state.type,
                        query = state.query,
                        onOpenPlaylist = onOpenPlaylist,
                        onOpenAlbum = onOpenAlbum,
                        onOpenArtist = onOpenArtist,
                        onPlayTracks = onPlayTracks,
                    )
                }
            }
        }
    }
}

@Composable
private fun History(
    history: List<String>,
    onSelect: (String) -> Unit,
    onClear: () -> Unit,
) {
    if (history.isEmpty()) {
        EmptyMessage(text = stringResource(R.string.search_prompt))
        return
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.search_recent),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClear) {
                Text(stringResource(R.string.search_clear_history))
            }
        }
        history.forEach { query ->
            Text(
                text = query,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(query) }
                    .padding(horizontal = Spacing.xl, vertical = Spacing.md),
            )
        }
    }
}

@Composable
private fun Results(
    results: SearchResults,
    type: SearchType,
    query: String,
    onOpenPlaylist: (Long) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
) {
    when (type) {
        SearchType.Songs -> {
            if (results.songs.isEmpty()) {
                EmptyMessage(text = stringResource(R.string.search_no_results, query))
            } else {
                val songs = results.songs
                LazyColumn(contentPadding = PaddingValues(bottom = Spacing.xxl)) {
                    itemsIndexed(songs, key = { _, track -> track.id }) { index, track ->
                        TrackRow(track = track, onClick = { onPlayTracks(songs, index) })
                    }
                }
            }
        }

        SearchType.Albums -> ResultGrid(
            count = results.albums.size,
            query = query,
            emptyRes = R.string.search_no_results,
        ) {
            items(results.albums, key = { it.id }) { album ->
                MediaCard(
                    title = album.name,
                    subtitle = album.artistLine,
                    artworkUrl = album.artworkUrl,
                    width = 108.dp,
                    onClick = { onOpenAlbum(album.id) },
                )
            }
        }

        SearchType.Artists -> ResultGrid(
            count = results.artists.size,
            query = query,
            emptyRes = R.string.search_no_results,
        ) {
            items(results.artists, key = { it.id }) { artist ->
                MediaCard(
                    title = artist.name,
                    subtitle = null,
                    artworkUrl = artist.artworkUrl,
                    width = 108.dp,
                    onClick = { onOpenArtist(artist.id) },
                )
            }
        }

        SearchType.Playlists -> ResultGrid(
            count = results.playlists.size,
            query = query,
            emptyRes = R.string.search_no_results,
        ) {
            items(results.playlists, key = { it.id }) { playlist ->
                MediaCard(
                    title = playlist.name,
                    subtitle = null,
                    artworkUrl = playlist.artworkUrl,
                    width = 108.dp,
                    onClick = { onOpenPlaylist(playlist.id) },
                )
            }
        }
    }
}

@Composable
private fun ResultGrid(
    count: Int,
    query: String,
    emptyRes: Int,
    content: androidx.compose.foundation.lazy.grid.LazyGridScope.() -> Unit,
) {
    if (count == 0) {
        EmptyMessage(text = stringResource(emptyRes, query))
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 108.dp),
            contentPadding = PaddingValues(Spacing.xl),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            content = content,
        )
    }
}

private fun SearchType.labelRes(): Int = when (this) {
    SearchType.Songs -> R.string.search_tab_songs
    SearchType.Albums -> R.string.search_tab_albums
    SearchType.Artists -> R.string.search_tab_artists
    SearchType.Playlists -> R.string.search_tab_playlists
}
