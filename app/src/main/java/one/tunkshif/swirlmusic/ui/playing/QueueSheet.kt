package one.tunkshif.swirlmusic.ui.playing

import one.tunkshif.swirlmusic.ui.player.QueueEvent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import one.tunkshif.swirlmusic.R
import one.tunkshif.swirlmusic.ui.components.Artwork
import one.tunkshif.swirlmusic.ui.theme.Spacing
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private object QueueDimens {
    val rowArtwork = 48.dp
    val rowPaddingVertical = Spacing.sm
    val rowPaddingHorizontal = Spacing.sm
    val handle = 24.dp
}

/** The queue sheet over the player: play order, tap to skip, swipe or remove to drop, drag to move. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    queue: QueueUiState,
    onEvent: (QueueEvent) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        modifier = modifier,
    ) {
        QueueSheetContent(queue = queue, onEvent = onEvent)
    }
}

/**
 * The sheet's body without the window, so behaviour tests and goldens render it directly on a
 * `Surface`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheetContent(
    queue: QueueUiState,
    onEvent: (QueueEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = Spacing.xl)) {
            Text(
                text = stringResource(R.string.playing_queue),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = pluralStringResource(
                    R.plurals.playing_queue_count,
                    queue.items.size,
                    queue.items.size,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (queue.items.size > 1 && !queue.canReorder) {
                Text(
                    text = stringResource(R.string.playing_queue_shuffle_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.size(Spacing.md))

        var localItems by remember(queue.items) { mutableStateOf(queue.items) }
        val firstIndex = queue.items.indexOfFirst { it.uid == queue.currentUid }.coerceAtLeast(0)
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = firstIndex)
        val reorderState = rememberReorderableLazyListState(listState) { from, to ->
            localItems = localItems.toMutableList().apply { add(to.index, removeAt(from.index)) }
        }
        // The uid whose drop ends in a Move event: the drag reorders the local copy only, and
        // onDragStopped reports the final position back against the original list.
        var pendingMove by remember { mutableStateOf<Long?>(null) }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // Header sits at Spacing.xl; list padding + row padding must add up to it so the
            // artwork's left edge lines up with the title.
            contentPadding = PaddingValues(
                start = Spacing.xl - QueueDimens.rowPaddingHorizontal,
                end = Spacing.xl - QueueDimens.rowPaddingHorizontal,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + Spacing.md,
            ),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            itemsIndexed(localItems, key = { _, it -> it.uid }) { index, item ->
                val originalIndex = queue.items.indexOfFirst { it.uid == item.uid }
                ReorderableItem(reorderState, key = item.uid) { isDragging ->
                    QueueRow(
                        index = index, count = localItems.size,
                        item = item,
                        isCurrent = item.uid == queue.currentUid,
                        isDragging = isDragging,
                        canReorder = queue.canReorder,
                        originalIndex = originalIndex,
                        originalItems = queue.items,
                        onEvent = onEvent,
                        dragHandle = {
                            if (queue.canReorder) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_drag_handle),
                                    contentDescription = stringResource(R.string.playing_queue_drag),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(QueueDimens.handle)
                                        .draggableHandle(
                                            onDragStarted = { pendingMove = item.uid },
                                            onDragStopped = {
                                                val uid = pendingMove
                                                pendingMove = null
                                                if (uid != null) {
                                                    val fromIndex = queue.items.indexOfFirst { it.uid == uid }
                                                    val toIndex = localItems.indexOfFirst { it.uid == uid }
                                                    if (fromIndex >= 0 && toIndex in queue.items.indices && toIndex != fromIndex) {
                                                        onEvent(
                                                            QueueEvent.Move(
                                                                uid = uid,
                                                                targetUid = queue.items[toIndex].uid,
                                                            ),
                                                        )
                                                    }
                                                }
                                            },
                                        ),
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueRow(
    item: QueueItemUi,
    isCurrent: Boolean,
    isDragging: Boolean,
    canReorder: Boolean,
    index: Int,
    count: Int,
    originalIndex: Int,
    originalItems: List<QueueItemUi>,
    onEvent: (QueueEvent) -> Unit,
    dragHandle: @Composable () -> Unit,
) {
    val removeLabel = stringResource(R.string.playing_queue_remove)
    val moveUpLabel = stringResource(R.string.playing_queue_move_up)
    val moveDownLabel = stringResource(R.string.playing_queue_move_down)
    val nowPlayingLabel = stringResource(R.string.playing_queue_now_playing)

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onEvent(QueueEvent.Remove(item.uid))
            }
            // The row itself always returns: the controller decides whether it actually leaves.
            false
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(ListItemDefaults.segmentedShapes(index, count).shape)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = Spacing.xl),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(ListItemDefaults.segmentedShapes(index, count).shape)
                .background(
                    if (isCurrent) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                )
                .clickable { onEvent(QueueEvent.SkipTo(item.uid)) }
                .semantics {
                    if (isCurrent) stateDescription = nowPlayingLabel
                    customActions = buildList {
                        add(
                            CustomAccessibilityAction(removeLabel) {
                                onEvent(QueueEvent.Remove(item.uid))
                                true
                            },
                        )
                        if (canReorder) {
                            originalItems.getOrNull(originalIndex - 1)?.let { previous ->
                                add(
                                    CustomAccessibilityAction(moveUpLabel) {
                                        onEvent(QueueEvent.Move(item.uid, previous.uid))
                                        true
                                    },
                                )
                            }
                            originalItems.getOrNull(originalIndex + 1)?.let { next ->
                                add(
                                    CustomAccessibilityAction(moveDownLabel) {
                                        onEvent(QueueEvent.Move(item.uid, next.uid))
                                        true
                                    },
                                )
                            }
                        }
                    }
                }
                .padding(
                    horizontal = QueueDimens.rowPaddingHorizontal,
                    vertical = QueueDimens.rowPaddingVertical,
                ),
        ) {
            Artwork(
                url = item.artworkUrl,
                contentDescription = null,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.size(QueueDimens.rowArtwork),
            )
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isCurrent) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.artistLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(Spacing.md))
            dragHandle()
        }
    }
}
