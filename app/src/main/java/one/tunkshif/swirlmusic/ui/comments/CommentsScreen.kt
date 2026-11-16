package one.tunkshif.swirlmusic.ui.comments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.ZoneId
import one.tunkshif.swirlmusic.R
import one.tunkshif.swirlmusic.data.model.Comment
import one.tunkshif.swirlmusic.data.model.CommentSort
import one.tunkshif.swirlmusic.data.model.CommentTarget
import one.tunkshif.swirlmusic.ui.common.LoadError
import one.tunkshif.swirlmusic.ui.common.SectionError
import one.tunkshif.swirlmusic.ui.common.SectionState
import one.tunkshif.swirlmusic.ui.common.SkeletonBar
import one.tunkshif.swirlmusic.ui.components.Artwork
import one.tunkshif.swirlmusic.ui.components.EmptyState
import one.tunkshif.swirlmusic.ui.format.CommentTime
import one.tunkshif.swirlmusic.ui.format.commentTimeOf
import one.tunkshif.swirlmusic.ui.format.formatCommentDate
import one.tunkshif.swirlmusic.ui.format.formatCount
import one.tunkshif.swirlmusic.ui.theme.Spacing

/** A new page starts loading once this many rows remain below the fold. */
private const val LOAD_AHEAD_ROWS = 5

/** The entry point wired into the shell's nav graph. */
@Composable
fun CommentsRoute(
    target: CommentTarget,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: CommentsViewModel = hiltViewModel(),
) {
    LaunchedEffect(target) { viewModel.load(target) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    CommentsScreen(
        state = state,
        title = title,
        onBack = onBack,
        onSelectSort = viewModel::onSelectSort,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
        contentPadding = contentPadding,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsScreen(
    state: CommentsUiState,
    title: String,
    onBack: () -> Unit,
    onSelectSort: (CommentSort) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    nowMillis: Long = System.currentTimeMillis(),
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            modifier = modifier
                .fillMaxSize()
                .padding(
                    top = contentPadding.calculateTopPadding(),
                    start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
                    end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
                ),
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = stringResource(R.string.comments_title),
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
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
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                val sorts = CommentSort.entries
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.xl, vertical = Spacing.sm),
                ) {
                    sorts.forEachIndexed { index, sort ->
                        SegmentedButton(
                            selected = state.sort == sort,
                            onClick = { onSelectSort(sort) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = sorts.size,
                            ),
                            label = { Text(stringResource(sort.labelRes())) },
                        )
                    }
                }
                state.totalCount?.let { total ->
                    val locale = LocalConfiguration.current.locales[0]
                    Text(
                        text = pluralStringResource(
                            R.plurals.comments_count,
                            total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                            String.format(locale, "%,d", total),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.xl),
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    when (val comments = state.comments) {
                        is SectionState.Loading -> CommentSkeletonRows()
                        is SectionState.Failed -> SectionError(
                            error = comments.error,
                            onRetry = onRetry,
                        )
                        is SectionState.Content -> {
                            if (comments.value.isEmpty()) {
                                EmptyState(
                                    icon = ImageVector.vectorResource(R.drawable.ic_comment),
                                    title = stringResource(R.string.comments_empty),
                                    body = stringResource(R.string.comments_empty_body),
                                )
                            } else {
                                CommentList(
                                    comments = comments.value,
                                    state = state,
                                    onLoadMore = onLoadMore,
                                    nowMillis = nowMillis,
                                    bottomPadding = contentPadding.calculateBottomPadding(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentList(
    comments: List<Comment>,
    state: CommentsUiState,
    onLoadMore: () -> Unit,
    nowMillis: Long,
    bottomPadding: androidx.compose.ui.unit.Dp,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(listState, comments.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last ->
                if (state.canLoadMore && last != null && last >= comments.size - LOAD_AHEAD_ROWS) {
                    onLoadMore()
                }
            }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.md,
            end = Spacing.md,
            bottom = bottomPadding + Spacing.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        itemsIndexed(comments, key = { _, comment -> comment.id }) { index, comment ->
            CommentRow(
                comment = comment,
                index = index,
                count = comments.size,
                nowMillis = nowMillis,
            )
        }
        if (state.canLoadMore || state.loadingMore || state.moreError != null) {
            item(key = "commentsMore") {
                MoreFooter(
                    loading = state.loadingMore,
                    error = state.moreError,
                    onRetry = onLoadMore,
                )
            }
        }
    }
}

/**
 * One comment in the D46 segmented style: circular avatar, name, "time · location" meta,
 * an optional quote box above the text, and a display-only likes/replies footer. Nothing
 * on the row reacts to clicks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentRow(
    comment: Comment,
    index: Int,
    count: Int,
    nowMillis: Long,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ListItemDefaults.segmentedShapes(index, count).shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Artwork(
            url = comment.avatarUrl,
            contentDescription = null,
            shape = CircleShape,
            modifier = Modifier.size(40.dp),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = comment.userName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = commentMeta(comment, nowMillis),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            comment.replyTo?.let { quote ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                ) {
                    Text(
                        text = stringResource(
                            R.string.comments_quote_fmt,
                            quote.userName,
                            quote.text,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(text = comment.text, style = MaterialTheme.typography.bodyMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_favorite),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = formatCount(comment.likedCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (comment.replyCount > 0) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.comments_reply_count,
                            comment.replyCount,
                            comment.replyCount,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun commentMeta(comment: Comment, nowMillis: Long): String {
    val zone = ZoneId.systemDefault()
    val locale = LocalConfiguration.current.locales[0]
    val time = when (val t = commentTimeOf(comment.timeMillis, nowMillis, zone)) {
        CommentTime.JustNow -> stringResource(R.string.comments_time_just_now)
        is CommentTime.MinutesAgo ->
            pluralStringResource(R.plurals.comments_time_minutes, t.minutes, t.minutes)
        is CommentTime.HoursAgo ->
            pluralStringResource(R.plurals.comments_time_hours, t.hours, t.hours)
        CommentTime.Yesterday -> stringResource(R.string.comments_time_yesterday)
        is CommentTime.ThisYear ->
            formatCommentDate(t.millis, withYear = false, zone = zone, locale = locale)
        is CommentTime.Older ->
            formatCommentDate(t.millis, withYear = true, zone = zone, locale = locale)
    }
    return listOfNotNull(time, comment.location).joinToString(" · ")
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
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

/** Skeletons on the real row's anatomy: circle avatar, name/meta bars, two text lines. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentSkeletonRows(count: Int = 6) {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        repeat(count) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ListItemDefaults.segmentedShapes(index, count).shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                SkeletonBar(shape = CircleShape, modifier = Modifier.size(40.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    modifier = Modifier.weight(1f),
                ) {
                    SkeletonBar(
                        textStyle = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.fillMaxWidth(0.5f),
                    )
                    SkeletonBar(
                        textStyle = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.fillMaxWidth(0.3f),
                    )
                    SkeletonBar(
                        textStyle = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                    SkeletonBar(
                        textStyle = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth(0.7f),
                    )
                }
            }
        }
    }
}

private fun CommentSort.labelRes(): Int = when (this) {
    CommentSort.Hot -> R.string.comments_sort_hot
    CommentSort.Newest -> R.string.comments_sort_newest
}
