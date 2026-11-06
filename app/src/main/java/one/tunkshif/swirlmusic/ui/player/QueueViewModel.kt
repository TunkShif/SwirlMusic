package one.tunkshif.swirlmusic.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import one.tunkshif.swirlmusic.player.LoopMode
import one.tunkshif.swirlmusic.player.PlayerController
import one.tunkshif.swirlmusic.player.QueueEntry
import one.tunkshif.swirlmusic.ui.playing.QueueItemUi
import one.tunkshif.swirlmusic.ui.playing.QueueUiState
import one.tunkshif.swirlmusic.ui.playing.queueUiStateOf

sealed interface QueueEvent {
    data class SkipTo(val uid: Long) : QueueEvent
    data class Remove(val uid: Long) : QueueEvent
    data class Move(val uid: Long, val targetUid: Long) : QueueEvent
}

/** Shell-scoped queue for the bottom sheet — shared by the mini player and Now Playing. */
@HiltViewModel
class QueueViewModel @Inject constructor(
    private val controller: PlayerController,
) : ViewModel() {

    val queue: StateFlow<QueueUiState> = controller.state
        .map { QueueKey(it.queue, it.playOrder, it.currentIndex, it.loopMode) }
        .distinctUntilChanged()
        .map { queueUiStateOf(it.queue, it.playOrder, it.currentIndex, it.loopMode) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = QueueUiState(),
        )

    fun onEvent(event: QueueEvent) {
        when (event) {
            is QueueEvent.SkipTo -> controller.skipTo(event.uid)
            is QueueEvent.Remove -> controller.remove(event.uid)
            is QueueEvent.Move -> controller.move(event.uid, event.targetUid)
        }
    }
}

/** The inputs queueUiStateOf reads: position ticks do not remap the queue. */
private data class QueueKey(
    val queue: List<QueueEntry>,
    val playOrder: List<Int>,
    val currentIndex: Int,
    val loopMode: LoopMode,
)
