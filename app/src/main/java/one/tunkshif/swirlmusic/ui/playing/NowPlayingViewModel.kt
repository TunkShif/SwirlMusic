package one.tunkshif.swirlmusic.ui.playing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import one.tunkshif.swirlmusic.data.api.resizedArtwork
import one.tunkshif.swirlmusic.player.LoopMode
import one.tunkshif.swirlmusic.player.PlaybackError
import one.tunkshif.swirlmusic.player.PlaybackState
import one.tunkshif.swirlmusic.player.PlayerController
import one.tunkshif.swirlmusic.player.QueueEntry

/** The cover asks NetEase for this many pixels. */
internal const val COVER_ARTWORK_PX = 1080

data class NowPlayingTrack(
    val title: String,
    val artistLine: String,
    val albumName: String?,
    val albumId: Long?,
    /** The track's artwork URL resized to [COVER_ARTWORK_PX]. */
    val artworkUrl: String?,
    /** The original list-sized URL, the Coil placeholder so the cover never flashes empty. */
    val placeholderArtworkUrl: String?,
)

data class NowPlayingUiState(
    val track: NowPlayingTrack? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val isTrial: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val loopMode: LoopMode = LoopMode.ListLoop,
    val hasNext: Boolean = false,
    val error: PlaybackError? = null,
)

sealed interface NowPlayingEvent {
    data object PlayPause : NowPlayingEvent
    data object Next : NowPlayingEvent
    data object Previous : NowPlayingEvent
    data object CycleLoopMode : NowPlayingEvent
    data class SeekTo(val positionMs: Long) : NowPlayingEvent
    data class SkipTo(val uid: Long) : NowPlayingEvent
    data class Remove(val uid: Long) : NowPlayingEvent
    data class Move(val uid: Long, val targetUid: Long) : NowPlayingEvent
}

data class QueueItemUi(
    val uid: Long,
    val title: String,
    val artistLine: String,
    val artworkUrl: String?,
)

data class QueueUiState(
    /** In play order: `playOrder.map { queue[it] }`. */
    val items: List<QueueItemUi> = emptyList(),
    val currentUid: Long? = null,
    val canReorder: Boolean = false,
)

internal fun PlaybackState.toQueueUiState(): QueueUiState =
    queueUiStateOf(queue, playOrder, currentIndex, loopMode)

internal fun queueUiStateOf(
    queue: List<QueueEntry>,
    playOrder: List<Int>,
    currentIndex: Int,
    loopMode: LoopMode,
): QueueUiState {
    val items = playOrder.mapNotNull { queue.getOrNull(it) }
        .map { entry ->
            QueueItemUi(
                uid = entry.uid,
                title = entry.track.title,
                artistLine = entry.track.artistLine,
                artworkUrl = entry.track.artworkUrl,
            )
        }
    return QueueUiState(
        items = items,
        currentUid = queue.getOrNull(currentIndex)?.uid,
        canReorder = loopMode != LoopMode.Shuffle && items.size > 1,
    )
}

/** The inputs [toQueueUiState] reads: position ticks do not remap the queue. */
private data class QueueKey(
    val queue: List<QueueEntry>,
    val playOrder: List<Int>,
    val currentIndex: Int,
    val loopMode: LoopMode,
)

internal fun PlaybackState.toNowPlayingUiState(): NowPlayingUiState {
    val current = current
    return NowPlayingUiState(
        track = current?.let { track ->
            NowPlayingTrack(
                title = track.title,
                artistLine = track.artistLine,
                albumName = track.albumName,
                albumId = track.albumId,
                artworkUrl = track.artworkUrl?.let { resizedArtwork(it, COVER_ARTWORK_PX) },
                placeholderArtworkUrl = track.artworkUrl,
            )
        },
        isPlaying = if (pending != null) playIntent else isPlaying,
        isLoading = isLoading,
        isTrial = isTrial,
        positionMs = positionMs,
        durationMs = durationMs.takeIf { it > 0 } ?: current?.durationMs ?: 0,
        loopMode = loopMode,
        hasNext = hasNext,
        error = error,
    )
}

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val controller: PlayerController,
) : ViewModel() {

    val state: StateFlow<NowPlayingUiState> = controller.state
        .map { it.toNowPlayingUiState() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NowPlayingUiState(),
        )

    val queue: StateFlow<QueueUiState> = controller.state
        .map { QueueKey(it.queue, it.playOrder, it.currentIndex, it.loopMode) }
        .distinctUntilChanged()
        .map { queueUiStateOf(it.queue, it.playOrder, it.currentIndex, it.loopMode) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = QueueUiState(),
        )

    fun onEvent(event: NowPlayingEvent) {
        when (event) {
            NowPlayingEvent.PlayPause -> controller.togglePlayPause()
            NowPlayingEvent.Next -> controller.next()
            NowPlayingEvent.Previous -> controller.previous()
            NowPlayingEvent.CycleLoopMode -> controller.cycleLoopMode()
            is NowPlayingEvent.SeekTo -> controller.seekTo(event.positionMs)
            is NowPlayingEvent.SkipTo -> controller.skipTo(event.uid)
            is NowPlayingEvent.Remove -> controller.remove(event.uid)
            is NowPlayingEvent.Move -> controller.move(event.uid, event.targetUid)
        }
    }
}
