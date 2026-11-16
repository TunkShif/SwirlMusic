package one.tunkshif.swirlmusic.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalTime
import one.tunkshif.swirlmusic.R
import one.tunkshif.swirlmusic.data.model.RecentAlbum
import one.tunkshif.swirlmusic.data.model.Track
import one.tunkshif.swirlmusic.player.PlayerController
import one.tunkshif.swirlmusic.ui.common.LoadError
import one.tunkshif.swirlmusic.ui.common.SectionState
import one.tunkshif.swirlmusic.ui.components.Artwork
import one.tunkshif.swirlmusic.ui.components.ToastPillHost
import one.tunkshif.swirlmusic.ui.components.ToastPillState
import one.tunkshif.swirlmusic.ui.components.rememberToastPillState
import one.tunkshif.swirlmusic.ui.theme.HeroTitleStyle
import one.tunkshif.swirlmusic.ui.theme.Spacing

@Composable
fun HomeRoute(
    nickname: String?,
    onPlayTracks: (List<Track>, Int, PlayerController.TrackPager?) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    now: LocalTime = LocalTime.now(),
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toastState = rememberToastPillState()
    // Coming back to Home refreshes the recent row when it is stale (see HomeViewModel).
    LaunchedEffect(Unit) { viewModel.onShown() }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            when (message) {
                HomeMessage.LuckyFailed ->
                    toastState.show(R.drawable.ic_error, R.string.home_lucky_failed)
            }
        }
    }
    HomeScreen(
        nickname = nickname,
        state = state,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onPlayTracks = onPlayTracks,
        onOpenAlbum = onOpenAlbum,
        onLucky = { viewModel.feelingLucky(onPlayTracks) },
        toastState = toastState,
        contentPadding = contentPadding,
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
    contentPadding: PaddingValues = PaddingValues(),
    now: LocalTime = LocalTime.now(),
    onPlayTracks: (List<Track>, Int, PlayerController.TrackPager?) -> Unit = { _, _, _ -> },
    onOpenAlbum: (Long) -> Unit = {},
    onLucky: () -> Unit = {},
    toastState: ToastPillState = rememberToastPillState(),
) {
    // A Surface, not a Box: it also supplies the default content colour, without which section
    // titles fall back to black on a dark scheme.
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        HomeContent(
            nickname,
            state,
            onRefresh,
            onRetry,
            now,
            onPlayTracks,
            onOpenAlbum,
            onLucky,
            toastState,
            contentPadding,
        )
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
    onPlayTracks: (List<Track>, Int, PlayerController.TrackPager?) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onLucky: () -> Unit,
    toastState: ToastPillState,
    contentPadding: PaddingValues,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = contentPadding.calculateTopPadding(),
                        start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
                        end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
                    ),
                contentPadding = PaddingValues(
                    bottom = contentPadding.calculateBottomPadding() + Spacing.xxl,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                item { Greeting(nickname = nickname, dayPart = dayPartFor(now.hour)) }
                item { HeroSection(state.daily, onPlayTracks) }
                if (state.likedIds.isNotEmpty()) {
                    item { LuckyButton(loading = state.feelingLucky, onClick = onLucky) }
                }
                item { SectionHeader(R.string.home_daily_title) }
                item { DailySection(state.daily, onRetry, onPlayTracks) }
                // An empty recent row hides its header too; loading and errors still show one.
                if (state.recent !is SectionState.Content || state.recent.value.isNotEmpty()) {
                    item { SectionHeader(R.string.home_recent_title) }
                    item { RecentSection(state.recent, onRetry, onOpenAlbum) }
                }
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

@Composable
private fun Greeting(nickname: String?, dayPart: DayPart) {
    Column(
        modifier = Modifier.padding(start = Spacing.xl, end = Spacing.xl, top = Spacing.lg),
    ) {
        // One line, whatever the day part: auto-size shrinks long greetings like
        // "Good afternoon" instead of letting them wrap.
        Text(
            text = stringResource(dayPart.plainGreeting()),
            style = HeroTitleStyle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(
                minFontSize = 26.sp,
                maxFontSize = HeroTitleStyle.fontSize,
            ),
        )
        if (!nickname.isNullOrBlank()) {
            Text(
                text = nickname,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Today's pick: a text-and-play row the way the reference does "Your Mix", with a small
 * collage of the first three daily artworks underneath instead of one big gradient card.
 */
@Composable
private fun HeroSection(
    state: SectionState<List<Track>>,
    onPlayTracks: (List<Track>, Int, PlayerController.TrackPager?) -> Unit,
) {
    val tracks = (state as? SectionState.Content)?.value ?: return
    val track = tracks.firstOrNull() ?: return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_hero_kicker),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.artistLine,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FilledIconButton(
                onClick = { onPlayTracks(tracks, 0, null) },
                modifier = Modifier.size(96.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.home_hero_play),
                    modifier = Modifier.size(40.dp),
                )
            }
        }
        HeroCollage(tracks = tracks.take(3))
    }
}

@Composable
private fun HeroCollage(tracks: List<Track>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center,
    ) {
        tracks.getOrNull(1)?.let { side ->
            Artwork(
                url = side.artworkUrl,
                contentDescription = null,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = Spacing.xl, y = (-24).dp)
                    .size(96.dp),
            )
        }
        tracks.getOrNull(2)?.let { side ->
            Artwork(
                url = side.artworkUrl,
                contentDescription = null,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = -Spacing.xl, y = 24.dp)
                    .size(96.dp),
            )
        }
        tracks.firstOrNull()?.let { center ->
            Artwork(
                url = center.artworkUrl,
                contentDescription = null,
                shape = RoundedCornerShape(percent = 50),
                modifier = Modifier
                    .graphicsLayer { rotationZ = -35f }
                    .size(width = 210.dp, height = 120.dp),
            )
        }
    }
}

@Composable
private fun SectionHeader(titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = Spacing.xl),
    )
}

@Composable
private fun DailySection(
    state: SectionState<List<Track>>,
    onRetry: () -> Unit,
    onPlayTracks: (List<Track>, Int, PlayerController.TrackPager?) -> Unit,
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
                            onClick = { onPlayTracks(tracks, index, null) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * The "Feeling lucky" action (0013-D57): a full-width tonal button under the hero. While the
 * first shuffled page resolves it swaps its content for a spinner and ignores taps, so the
 * button keeps its colour instead of greying out.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LuckyButton(loading: Boolean, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = { if (!loading) onClick() },
        modifier = Modifier
            .padding(horizontal = Spacing.xl)
            .fillMaxWidth()
            .height(48.dp),
    ) {
        if (loading) {
            LoadingIndicator(modifier = Modifier.size(24.dp))
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_shuffle),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Text(
                text = stringResource(R.string.home_feeling_lucky),
                modifier = Modifier.padding(start = Spacing.sm),
            )
        }
    }
}

@Composable
private fun RecentSection(
    state: SectionState<List<RecentAlbum>>,
    onRetry: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
) {
    when (state) {
        is SectionState.Loading -> CardRowSkeleton()

        is SectionState.Failed -> SectionError(state.error, onRetry)

        is SectionState.Content -> LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.xl),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            items(state.value, key = { it.album.id }) { recent ->
                TrackCard(
                    title = recent.album.name,
                    subtitle = recent.album.artistLine,
                    artworkUrl = recent.album.artworkUrl,
                    onClick = { onOpenAlbum(recent.album.id) },
                )
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
            .width(140.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Artwork(
            url = artworkUrl,
            contentDescription = null,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.size(140.dp),
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
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
                Box(
                    modifier = Modifier
                        .padding(top = Spacing.sm)
                        .fillMaxWidth(0.8f)
                        .height(14.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
                Box(
                    modifier = Modifier
                        .padding(top = Spacing.xs)
                        .fillMaxWidth(0.5f)
                        .height(12.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
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
