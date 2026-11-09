package one.tunkshif.swirlmusic.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import one.tunkshif.swirlmusic.data.media.ArtworkSeedSource
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
    /** A tapped track is resolving; show the loading ring instead of progress. */
    val isLoading: Boolean = false,
    /** Changes when the track does — the text swipe keys its exit/enter animation on it. */
    val trackKey: Long? = null,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
) {
    val hasTrack: Boolean get() = !title.isNullOrBlank()
}

/**
 * Artwork URL → colour seed. PlaybackState ticks every 500 ms, so dedupe the URL first;
 * mapLatest keeps the previous seed visible while the next one resolves.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal fun Flow<String?>.toAlbumSeeds(source: ArtworkSeedSource): Flow<Int?> =
    distinctUntilChanged().mapLatest { url -> url?.let { source.seed(it) } }

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val controller: PlayerController,
    artworkSeedSource: ArtworkSeedSource,
) : ViewModel() {

    /** The colour seed of the current track's artwork, kept while the next one loads. */
    val albumSeed: StateFlow<Int?> = controller.state
        .map { it.current?.artworkUrl }
        .toAlbumSeeds(artworkSeedSource)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    val state: StateFlow<MiniPlayerUiState> = controller.state
        .map { playback ->
            MiniPlayerUiState(
                title = playback.current?.title,
                subtitle = playback.current?.artistLine,
                artworkUrl = playback.current?.artworkUrl,
                isPlaying = if (playback.pending != null) playback.playIntent else playback.isPlaying,
                progress = playback.progress,
                isTrial = playback.isTrial,
                isLoading = playback.isLoading,
                // Same precedence as `current`: a pending track (not in the queue yet) wins
                // by id; otherwise the queue uid, distinct when a track plays twice.
                trackKey = playback.pending?.id
                    ?: playback.queue.getOrNull(playback.currentIndex)?.uid,
                hasNext = playback.hasNext,
                hasPrevious = playback.hasPrevious,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MiniPlayerUiState(),
        )

    fun togglePlayPause() = controller.togglePlayPause()

    fun next() = controller.next()

    fun previousTrack() = controller.previousTrack()

    fun play(tracks: List<Track>, startIndex: Int, rest: PlayerController.TrackPager? = null) =
        controller.play(tracks, startIndex, rest)
}
