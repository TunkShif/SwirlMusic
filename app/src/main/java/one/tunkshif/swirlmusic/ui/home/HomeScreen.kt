package one.tunkshif.swirlmusic.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalTime
import one.tunkshif.swirlmusic.R
import one.tunkshif.swirlmusic.data.model.ChartSummary
import one.tunkshif.swirlmusic.data.model.PlaylistSummary
import one.tunkshif.swirlmusic.data.model.Track
import one.tunkshif.swirlmusic.ui.common.LoadError
import one.tunkshif.swirlmusic.ui.common.SectionState
import one.tunkshif.swirlmusic.ui.components.Artwork
import one.tunkshif.swirlmusic.ui.format.formatCount
import one.tunkshif.swirlmusic.ui.theme.Spacing

@Composable
fun HomeRoute(
    nickname: String?,
    onPlayTracks: (List<Track>, Int) -> Unit,
    modifier: Modifier = Modifier,
    now: LocalTime = LocalTime.now(),
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(
        nickname = nickname,
        state = state,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onPlayTracks = onPlayTracks,
        modifier = modifier,
        now = now,
    )
}

@Composable
fun HomeScreen(
    nickname: String?,
    state: HomeUiState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    now: LocalTime = LocalTime.now(),
    onPlayTracks: (List<Track>, Int) -> Unit = { _, _ -> },
) {
    // A Surface, not a Box: it also supplies the default content colour, without which section
    // titles fall back to black on a dark scheme.
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        HomeContent(nickname, state, onRefresh, onRetry, now, onPlayTracks)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    nickname: String?,
    state: HomeUiState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    now: LocalTime,
    onPlayTracks: (List<Track>, Int) -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            item { Greeting(nickname = nickname, dayPart = dayPartFor(now.hour)) }
            item { HeroSection(state.daily, onPlayTracks) }
            item { SectionHeader(R.string.home_daily_title) }
            item { DailySection(state.daily, onRetry, onPlayTracks) }
            item { SectionHeader(R.string.home_playlists_title) }
            item { PlaylistSection(state.playlists, onRetry) }
            item { SectionHeader(R.string.home_charts_title) }
            item { ChartSection(state.charts, onRetry) }
        }
    }
}

@Composable
private fun Greeting(nickname: String?, dayPart: DayPart) {
    Column(
        modifier = Modifier.padding(start = Spacing.xl, end = Spacing.xl, top = Spacing.lg),
    ) {
        Text(
            text = stringResource(dayPart.plainGreeting()),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (!nickname.isNullOrBlank()) {
            Text(
                text = nickname,
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * The one big card on the screen: today's first pick, full bleed, with the play action on it.
 * The reference leads with a hero like this instead of another row of small cards.
 */
@Composable
private fun HeroSection(
    state: SectionState<List<Track>>,
    onPlayTracks: (List<Track>, Int) -> Unit,
) {
    val track = (state as? SectionState.Content)?.value?.firstOrNull() ?: return
    val tracks = (state as SectionState.Content).value

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl)
            .height(212.dp)
            .clip(MaterialTheme.shapes.large),
    ) {
        Artwork(
            url = track.artworkUrl,
            contentDescription = null,
            shape = RectangleShape,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Text(
                text = stringResource(R.string.home_hero_kicker),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.8f),
            )
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artistLine,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FilledTonalButton(
            onClick = { onPlayTracks(tracks, 0) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Spacing.lg),
            shape = CircleShape,
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
        }
    }
}

@Composable
private fun SectionHeader(titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(horizontal = Spacing.xl),
    )
}

@Composable
private fun DailySection(
    state: SectionState<List<Track>>,
    onRetry: () -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
) {
    when (state) {
        is SectionState.Loading -> CardRowSkeleton()

        is SectionState.Failed -> SectionError(state.error, onRetry)

        is SectionState.Content -> {
            if (state.value.isEmpty()) {
                SectionEmpty()
            } else {
                val tracks = state.value
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.xl),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                        TrackCard(
                            title = track.title,
                            subtitle = track.artistLine,
                            artworkUrl = track.artworkUrl,
                            onClick = { onPlayTracks(tracks, index) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistSection(state: SectionState<List<PlaylistSummary>>, onRetry: () -> Unit) {
    when (state) {
        is SectionState.Loading -> CardRowSkeleton()

        is SectionState.Failed -> SectionError(state.error, onRetry)

        is SectionState.Content -> {
            if (state.value.isEmpty()) {
                SectionEmpty()
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.xl),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    items(state.value, key = { it.id }) { playlist ->
                        TrackCard(
                            title = playlist.name,
                            subtitle = stringResource(
                                R.string.home_plays,
                                formatCount(playlist.playCount),
                            ),
                            artworkUrl = playlist.artworkUrl,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartSection(state: SectionState<List<ChartSummary>>, onRetry: () -> Unit) {
    when (state) {
        is SectionState.Loading -> ChartSkeleton()

        is SectionState.Failed -> SectionError(state.error, onRetry)

        is SectionState.Content -> {
            if (state.value.isEmpty()) {
                SectionEmpty()
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    state.value.forEach { chart -> ChartRow(chart) }
                }
            }
        }
    }
}

@Composable
private fun TrackCard(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .width(132.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Artwork(
            url = artworkUrl,
            contentDescription = null,
            modifier = Modifier.size(132.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChartRow(chart: ChartSummary) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Artwork(
            url = chart.artworkUrl,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
        )
        Column(modifier = Modifier.align(Alignment.CenterVertically)) {
            Text(
                text = chart.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            chart.updateFrequency?.let { frequency ->
                Text(
                    text = frequency,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            chart.previewLines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SectionError(error: LoadError, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(
                when (error) {
                    LoadError.Network -> R.string.home_error_network
                    LoadError.Server -> R.string.home_error_server
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.home_retry))
        }
    }
}

@Composable
private fun SectionEmpty() {
    Text(
        text = stringResource(R.string.home_empty_title),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.xl),
    )
}

@Composable
private fun CardRowSkeleton() {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.xl),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        userScrollEnabled = false,
    ) {
        items(count = 3) {
            Column(modifier = Modifier.width(132.dp)) {
                Box(
                    modifier = Modifier
                        .size(132.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Box(
                    modifier = Modifier
                        .padding(top = Spacing.sm)
                        .fillMaxWidth(0.8f)
                        .height(14.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Box(
                    modifier = Modifier
                        .padding(top = Spacing.xs)
                        .fillMaxWidth(0.5f)
                        .height(12.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
    }
}

@Composable
private fun ChartSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.4f)
                            .height(16.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(12.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
            }
        }
    }
}

private fun DayPart.plainGreeting(): Int = when (this) {
    DayPart.Morning -> R.string.home_greeting_morning_plain
    DayPart.Afternoon -> R.string.home_greeting_afternoon_plain
    DayPart.Evening -> R.string.home_greeting_evening_plain
    DayPart.Night -> R.string.home_greeting_night_plain
}
