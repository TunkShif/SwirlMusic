package one.tunkshif.swirlmusic.ui.playing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.tunkshif.swirlmusic.data.api.ApiResult
import one.tunkshif.swirlmusic.data.api.resizedArtwork
import one.tunkshif.swirlmusic.data.library.ArtistAvatarRepository
import one.tunkshif.swirlmusic.data.library.CollectionRepository
import one.tunkshif.swirlmusic.data.library.ToggleResult
import one.tunkshif.swirlmusic.data.local.LyricsExtra
import one.tunkshif.swirlmusic.data.local.LyricsPreferences
import one.tunkshif.swirlmusic.data.lyrics.LyricsRepository
import one.tunkshif.swirlmusic.data.model.ArtistRef
import one.tunkshif.swirlmusic.data.model.Lyrics
import one.tunkshif.swirlmusic.data.model.Track
import one.tunkshif.swirlmusic.player.LoopMode
import one.tunkshif.swirlmusic.player.PlaybackError
import one.tunkshif.swirlmusic.player.PlaybackState
import one.tunkshif.swirlmusic.player.PlayerController
import one.tunkshif.swirlmusic.player.StreamQuality
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
    val artists: List<ArtistRef> = emptyList(),
    val artistAvatars: Map<Long, String> = emptyMap(),
)

/** The artists the menu can offer: real ids only, named, deduped by id. */
internal fun Track.menuArtists(): List<ArtistRef> =
    artists.filter { it.id > 0 && it.name.isNotBlank() }.distinctBy { it.id }

/**
 * Avatar map for the choose-artist menu. Emits `emptyMap()` first so `combine` never waits on
 * a fetch; only multi-artist tracks fetch at all — a single artist has no menu.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun artistAvatarsFlow(
    artistIds: Flow<List<Long>>,
    fetch: suspend (List<Long>) -> Map<Long, String>,
): Flow<Map<Long, String>> =
    artistIds.distinctUntilChanged().transformLatest { ids ->
        emit(emptyMap())
        if (ids.size >= 2) emit(fetch(ids))
    }

/** A neighbour cover for the carousel; artwork pre-resized like the main cover. */
data class NowPlayingCover(
    val uid: Long,
    val artworkUrl: String?,
    val placeholderArtworkUrl: String?,
)

data class NowPlayingUiState(
    val track: NowPlayingTrack? = null,
    val currentUid: Long? = null,
    val previous: NowPlayingCover? = null,
    val next: NowPlayingCover? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val isTrial: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val loopMode: LoopMode = LoopMode.ListLoop,
    val hasNext: Boolean = false,
    val error: PlaybackError? = null,
    /** The current track's stream quality; null until the URL resolves. */
    val quality: StreamQuality? = null,
    /** Liked state of the current track: server, then last-known, then false. */
    val liked: Boolean = false,
)

sealed interface NowPlayingEvent {
    data object PlayPause : NowPlayingEvent
    data object Next : NowPlayingEvent
    data object Previous : NowPlayingEvent
    data object CycleLoopMode : NowPlayingEvent
    data class SkipToCover(val uid: Long) : NowPlayingEvent
    data class SeekTo(val positionMs: Long) : NowPlayingEvent
    data object RetryLyrics : NowPlayingEvent
    data object CycleLyricsExtra : NowPlayingEvent
    data object ToggleLike : NowPlayingEvent
}

/** One-shot messages for the toast pill. */
enum class NowPlayingMessage { Liked, Unliked, LikeFailed }

sealed interface LyricsUiState {
    data object Loading : LyricsUiState
    data object Error : LyricsUiState
    data class Content(
        val lyrics: Lyrics,
        /** Effective extra line: the preference if this song has it, else None. */
        val extra: LyricsExtra,
        /** [LyricsExtra.None] plus the kinds this song has (Synced only). */
        val extraChoices: List<LyricsExtra>,
    ) : LyricsUiState
}

internal fun lyricsContentOf(lyrics: Lyrics, preferred: LyricsExtra): LyricsUiState.Content {
    val choices = if (lyrics is Lyrics.Synced) {
        buildList {
            add(LyricsExtra.None)
            if (lyrics.hasTranslation) add(LyricsExtra.Translation)
            if (lyrics.hasRomanization) add(LyricsExtra.Romanization)
        }
    } else {
        listOf(LyricsExtra.None)
    }
    return LyricsUiState.Content(
        lyrics = lyrics,
        extra = if (preferred in choices) preferred else LyricsExtra.None,
        extraChoices = choices,
    )
}

internal fun nextExtra(choices: List<LyricsExtra>, current: LyricsExtra): LyricsExtra {
    val index = choices.indexOf(current)
    return choices[(index + 1) % choices.size]
}

/** The entry after current in play order, wrapping; null when the queue has under 2 items. */
internal fun nextTrackIdInPlayOrder(state: PlaybackState): Long? {
    if (state.queue.size < 2) return null
    val order = state.playOrder.takeIf { it.isNotEmpty() } ?: state.queue.indices.toList()
    val position = order.indexOf(state.currentIndex).takeIf { it >= 0 } ?: 0
    val nextIndex = order[(position + 1) % order.size]
    return state.queue.getOrNull(nextIndex)?.track?.id
}

/**
 * Track id changes and retries fetch lyrics; a null marker goes out first so a new song or a
 * retry never shows the previous song's lines. Null track id → Loading.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun lyricsStateFlow(
    trackIds: Flow<Long?>,
    retry: Flow<Int>,
    fetch: suspend (Long) -> ApiResult<Lyrics>,
    extra: Flow<LyricsExtra>,
): Flow<LyricsUiState> = trackIds
    .combine(retry) { id, _ -> id }
    .flatMapLatest { id ->
        if (id == null) return@flatMapLatest flowOf<ApiResult<Lyrics>?>(null)
        flow {
            emit(null)
            emit(fetch(id))
        }
    }
    .combine(extra) { result, preferred ->
        when {
            result == null -> LyricsUiState.Loading
            result is ApiResult.Success -> lyricsContentOf(result.value, preferred)
            else -> LyricsUiState.Error
        }
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

internal fun PlaybackState.toNowPlayingUiState(): NowPlayingUiState {
    val current = current
    val currentEntry = queue.getOrNull(currentIndex)

    // Play-order neighbours of current, wrapping; null when the queue has under 2 items.
    fun neighbourCover(offset: Int): NowPlayingCover? {
        if (queue.size < 2) return null
        val order = playOrder.takeIf { it.isNotEmpty() } ?: queue.indices.toList()
        val position = order.indexOf(currentIndex).takeIf { it >= 0 } ?: 0
        val index = order[(position + offset + order.size) % order.size]
        return queue.getOrNull(index)?.let { entry ->
            NowPlayingCover(
                uid = entry.uid,
                artworkUrl = entry.track.artworkUrl?.let { resizedArtwork(it, COVER_ARTWORK_PX) },
                placeholderArtworkUrl = entry.track.artworkUrl,
            )
        }
    }

    return NowPlayingUiState(
        track = current?.let { track ->
            NowPlayingTrack(
                title = track.title,
                artistLine = track.artistLine,
                albumName = track.albumName,
                albumId = track.albumId,
                artworkUrl = track.artworkUrl?.let { resizedArtwork(it, COVER_ARTWORK_PX) },
                placeholderArtworkUrl = track.artworkUrl,
                artists = track.menuArtists(),
            )
        },
        currentUid = currentEntry?.uid,
        previous = neighbourCover(offset = -1),
        next = neighbourCover(offset = 1),
        isPlaying = if (pending != null) playIntent else isPlaying,
        isLoading = isLoading,
        isTrial = isTrial,
        positionMs = positionMs,
        durationMs = durationMs.takeIf { it > 0 } ?: current?.durationMs ?: 0,
        loopMode = loopMode,
        hasNext = hasNext,
        error = error,
        quality = quality,
    )
}

/**
 * Liked state for the Now Playing heart. Paints `server ?: lastKnown ?: false` (0012-D51):
 * [CollectionRepository.refreshLikedIds] runs once per process, so a last-known read after it
 * is the server truth; before it finishes the stored row still shows. A toggle flips an
 * optimistic override keyed by track id, ignores taps while a write is in flight, and reverts
 * on failure — a track change mid-write just stops showing the override.
 */
internal class TrackLikeState(
    private val scope: CoroutineScope,
    private val collection: CollectionRepository,
    trackIds: Flow<Long?>,
) {

    private val version = MutableStateFlow(0)
    private val override = MutableStateFlow<Pair<Long, Boolean>?>(null)
    private var writeInFlight = false

    private val _messages = Channel<NowPlayingMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    val liked: StateFlow<Boolean> = combine(trackIds, version, override) { id, _, pending ->
        when {
            id == null -> false
            pending?.first == id -> pending.second
            else -> collection.lastKnownTrackLiked(id) ?: false
        }
    }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        scope.launch {
            collection.refreshLikedIds()
            version.update { it + 1 }
        }
    }

    fun toggle(trackId: Long?) {
        if (trackId == null || writeInFlight) return
        val target = !(override.value?.takeIf { it.first == trackId }?.second ?: liked.value)
        override.value = trackId to target
        writeInFlight = true
        scope.launch {
            val result = collection.setTrackLiked(trackId, target)
            writeInFlight = false
            if (override.value?.first == trackId) override.value = null
            version.update { it + 1 }
            _messages.send(
                when (result) {
                    ToggleResult.Done ->
                        if (target) NowPlayingMessage.Liked else NowPlayingMessage.Unliked
                    else -> NowPlayingMessage.LikeFailed
                },
            )
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val controller: PlayerController,
    private val lyricsRepository: LyricsRepository,
    private val lyricsPreferences: LyricsPreferences,
    private val collection: CollectionRepository,
    artistAvatarRepository: ArtistAvatarRepository,
) : ViewModel() {

    private val likeState = TrackLikeState(
        scope = viewModelScope,
        collection = collection,
        trackIds = controller.state.map { it.current?.id }.distinctUntilChanged(),
    )

    val messages = likeState.messages

    val state: StateFlow<NowPlayingUiState> = combine(
        controller.state.map { it.toNowPlayingUiState() },
        artistAvatarsFlow(
            controller.state.map { it.current?.menuArtists()?.map(ArtistRef::id).orEmpty() },
            artistAvatarRepository::avatars,
        ),
        likeState.liked,
    ) { uiState, avatars, liked ->
        uiState.copy(track = uiState.track?.copy(artistAvatars = avatars), liked = liked)
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NowPlayingUiState(),
        )

    private val lyricsRetry = MutableStateFlow(0)

    val lyrics: StateFlow<LyricsUiState> = lyricsStateFlow(
        trackIds = controller.state.map { it.current?.id }.distinctUntilChanged(),
        retry = lyricsRetry,
        fetch = lyricsRepository::lyrics,
        extra = lyricsPreferences.extra,
    )
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LyricsUiState.Loading,
        )

    init {
        viewModelScope.launch {
            controller.state
                .map(::nextTrackIdInPlayOrder)
                .distinctUntilChanged()
                .filterNotNull()
                .collect { lyricsRepository.prefetch(it) }
        }
    }

    fun onEvent(event: NowPlayingEvent) {
        when (event) {
            NowPlayingEvent.PlayPause -> controller.togglePlayPause()
            NowPlayingEvent.Next -> controller.next()
            is NowPlayingEvent.SkipToCover -> controller.skipTo(event.uid)
            NowPlayingEvent.Previous -> controller.previous()
            NowPlayingEvent.CycleLoopMode -> controller.cycleLoopMode()
            is NowPlayingEvent.SeekTo -> controller.seekTo(event.positionMs)
            NowPlayingEvent.RetryLyrics -> lyricsRetry.update { it + 1 }
            NowPlayingEvent.ToggleLike -> likeState.toggle(controller.state.value.current?.id)
            NowPlayingEvent.CycleLyricsExtra -> viewModelScope.launch {
                val content = lyrics.value as? LyricsUiState.Content ?: return@launch
                lyricsPreferences.setExtra(nextExtra(content.extraChoices, content.extra))
            }
        }
    }
}
