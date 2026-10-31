package one.tunkshif.swirlmusic.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import one.tunkshif.swirlmusic.data.model.Track
import one.tunkshif.swirlmusic.player.PlayerController

/** What the mini player draws. Empty when nothing has been played yet. */
data class MiniPlayerUiState(
    val title: String? = null,
    val subtitle: String? = null,
    val artworkUrl: String? = null,
    val isPlaying: Boolean = false,
    val progress: Float = 0f,
    val isTrial: Boolean = false,
) {
    val hasTrack: Boolean get() = !title.isNullOrBlank()
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val controller: PlayerController,
) : ViewModel() {

    val state: StateFlow<MiniPlayerUiState> = controller.state
        .map { playback ->
            MiniPlayerUiState(
                title = playback.current?.title,
                subtitle = playback.current?.artistLine,
                artworkUrl = playback.current?.artworkUrl,
                isPlaying = playback.isPlaying,
                progress = playback.progress,
                isTrial = playback.isTrial,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MiniPlayerUiState(),
        )

    fun togglePlayPause() = controller.togglePlayPause()

    fun play(tracks: List<Track>, startIndex: Int) = controller.play(tracks, startIndex)
}
