package one.tunkshif.swirlmusic.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import one.tunkshif.swirlmusic.data.api.NcmPlaybackApi
import one.tunkshif.swirlmusic.data.model.PlayableUrl
import one.tunkshif.swirlmusic.data.model.Track
import one.tunkshif.swirlmusic.di.MainDispatcher

/** Index ranges for the fast-start queue fill. Empty ranges mean "nothing to do". */
internal data class QueueSplit(
    val window: IntRange,
    val head: IntRange,
    val tail: IntRange,
)

/**
 * Splits `count` items into the play window, the prefix to re-insert at 0, and the tail to
 * append — with an out-of-range [startIndex] clamped into the list. Pure and unit-tested.
 */
internal fun queueSplit(count: Int, startIndex: Int, windowSize: Int): QueueSplit {
    val last = (count - 1).coerceAtLeast(0)
    val windowStart = startIndex.coerceIn(0, last)
    val windowEnd = (windowStart + windowSize - 1).coerceAtMost(last)
    return QueueSplit(
        window = windowStart..windowEnd,
        head = if (windowStart > 0) 0 until windowStart else IntRange.EMPTY,
        tail = if (windowEnd < last) (windowEnd + 1)..last else IntRange.EMPTY,
    )
}

/**
 * The one player in the app.
 *
 * Queue, shuffle, and repeat are ExoPlayer's own, which is also what the media notification shows.
 * What lives here is the NetEase part: resolving signed URLs in one request per queue, refetching a
 * URL when playback fails because it expired, and reporting a listen once it counts.
 */
@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resolver: SongUrlResolver,
    private val playbackApi: NcmPlaybackApi,
    private val scrobbler: ScrobbleTracker,
    @MainDispatcher mainDispatcher: CoroutineDispatcher,
) {

    /**
     * ExoPlayer must be used from the thread that created it, which is the main thread here.
     * Every coroutine that touches the player runs in this scope, never on a worker.
     */
    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var queue: List<Track> = emptyList()
    private val playable = mutableMapOf<Long, PlayableUrl>()
    private val retriedTracks = mutableSetOf<Long>()
    private var repeatMode = RepeatMode.Off
    private var serviceStarted = false
    private var ticker: Job? = null

    init {
        player.addListener(
            object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    publish()
                }

                override fun onPlayerError(error: PlaybackException) {
                    onPlaybackFailed()
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    scope.launch { refreshNextIfStale() }
                }
            },
        )
        startTicker()
    }

    // region commands

    /**
     * Supplies the tracks after the ones passed to [play], in list order, one chunk per call.
     * Null means done; an EMPTY chunk means every id was unavailable — skip it and continue,
     * never treat it as the end.
     */
    fun interface TrackPager { suspend fun next(): List<Track>? }

    private var playGeneration = 0
    private var playJob: Job? = null

    fun play(tracks: List<Track>, startIndex: Int, rest: TrackPager? = null) {
        if (tracks.isEmpty()) return
        val generation = ++playGeneration
        playJob?.cancel()
        playJob = scope.launch {
            // Fast start: resolve only the window around the tapped track.
            val split = queueSplit(tracks.size, startIndex, PLAY_WINDOW)
            val window = tracks.subList(split.window.first, split.window.last + 1)
            val items = itemsFor(window)
            if (items.isEmpty()) {
                publish(error = PlaybackError.Unavailable)
                return@launch
            }
            // The start track must play: if it was dropped, start at the first playable.
            val windowTracks = items.map { it.first }
            val newStart = (windowTracks.indexOfFirst { it.id == window.getOrNull(0)?.id })
                .takeIf { it >= 0 } ?: 0
            queue = windowTracks
            retriedTracks.clear()
            player.setMediaItems(items.map { it.second }, newStart, 0)
            player.prepare()
            player.play()
            ensureService()
            publish(error = null)

            // Background fill, in order: tail of the list, then the head (inserted at 0 so
            // list order holds), then pager chunks. Each step bails if a newer play() won.
            val tail = tracks.subList(split.tail.first, split.tail.last + 1)
            appendResolved(tail, generation)
            if (!isCurrent(generation)) return@launch
            val head = tracks.subList(split.head.first, split.head.last + 1)
            if (head.isNotEmpty()) {
                val headItems = itemsFor(head)
                if (headItems.isNotEmpty() && isCurrent(generation)) {
                    player.addMediaItems(0, headItems.map { it.second })
                    queue = headItems.map { it.first } + queue
                    publish()
                }
            }
            if (!isCurrent(generation)) return@launch
            if (rest != null) {
                while (isActive && isCurrent(generation)) {
                    val chunk = rest.next() ?: break
                    // Empty is a dead slice, not the end of the list.
                    if (chunk.isNotEmpty()) appendResolved(chunk, generation)
                }
            }
        }
    }

    private fun isCurrent(generation: Int) = generation == playGeneration

    /** Resolve + append a slice of tracks at the queue end, keeping queue/playable in sync. */
    private suspend fun appendResolved(tracks: List<Track>, generation: Int) {
        tracks.chunked(RESOLVE_CHUNK).forEach { chunk ->
            if (!isCurrent(generation)) return
            val items = itemsFor(chunk)
            if (items.isNotEmpty()) {
                queue = queue + items.map { it.first }
                player.addMediaItems(items.map { it.second })
                publish()
            }
        }
    }

    /** Resolve a slice and return (track, MediaItem) pairs for the playable ones, in order. */
    private suspend fun itemsFor(tracks: List<Track>): List<Pair<Track, MediaItem>> {
        val resolved = resolver.resolve(tracks.map { it.id })
        return tracks.mapNotNull { track ->
            val url = resolved[track.id]?.takeIf { it.isPlayable } ?: return@mapNotNull null
            playable[track.id] = url
            track to buildItem(track, requireNotNull(url.url))
        }
    }

    // Commands hop onto the main scope: ExoPlayer rejects calls from any other thread, and callers
    // should not have to care which thread they are on.

    fun togglePlayPause() = scope.launch {
        if (player.mediaItemCount == 0) return@launch
        if (player.isPlaying) player.pause() else player.play()
    }

    fun next() = scope.launch {
        if (player.mediaItemCount == 0) return@launch
        player.seekToNext()
    }

    fun previous() = scope.launch {
        if (player.mediaItemCount == 0) return@launch
        player.seekToPrevious()
    }

    fun seekTo(positionMs: Long) = scope.launch {
        player.seekTo(positionMs.coerceAtLeast(0))
        publish()
    }

    fun cycleRepeat() = scope.launch {
        repeatMode = when (repeatMode) {
            RepeatMode.Off -> RepeatMode.All
            RepeatMode.All -> RepeatMode.One
            RepeatMode.One -> RepeatMode.Off
        }
        player.repeatMode = when (repeatMode) {
            RepeatMode.Off -> Player.REPEAT_MODE_OFF
            RepeatMode.All -> Player.REPEAT_MODE_ALL
            RepeatMode.One -> Player.REPEAT_MODE_ONE
        }
        publish()
    }

    fun toggleShuffle() = scope.launch {
        player.shuffleModeEnabled = !player.shuffleModeEnabled
        publish()
    }

    /** Adds a track right after the current one. */
    fun playNext(track: Track) = enqueue(track, next = true)

    /** Adds a track to the end of the queue. */
    fun addToQueue(track: Track) = enqueue(track, next = false)

    // endregion

    private fun enqueue(track: Track, next: Boolean) {
        scope.launch {
            val url = resolver.resolve(listOf(track.id))[track.id]
                ?.takeIf { it.isPlayable } ?: return@launch

            playable[track.id] = url
            queue = if (next) {
                queue.toMutableList().apply { add((player.currentMediaItemIndex + 1).coerceAtMost(size), track) }
            } else {
                queue + track
            }

            val insertAt = if (next) {
                (player.currentMediaItemIndex + 1).coerceAtMost(player.mediaItemCount)
            } else {
                player.mediaItemCount
            }
            player.addMediaItem(insertAt, buildItem(track, requireNotNull(url.url)))
            publish()
        }
    }

    /**
     * URLs resolved early in a long queue expire before they play. When a track ends, check
     * the NEXT item (shuffle-aware) and refresh its URL before it starts. The playback-error
     * refetch stays as the fallback.
     */
    private suspend fun refreshNextIfStale() {
        val nextIndex = player.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET) return
        val track = queue.getOrNull(nextIndex) ?: return
        if (resolver.isFresh(track.id)) return
        val generation = playGeneration
        val url = resolver.resolve(listOf(track.id), forceRefresh = true)[track.id]
            ?.takeIf { it.isPlayable } ?: return
        if (generation != playGeneration) return
        // The head insert or a playNext may have shifted indices while resolving: re-locate.
        val index = if (queue.getOrNull(nextIndex)?.id == track.id) nextIndex else queue.indexOf(track)
        if (index < 0) return
        playable[track.id] = url
        player.replaceMediaItem(index, buildItem(track, requireNotNull(url.url)))
    }

    /**
     * A signed URL expired, or the CDN refused it. Refetch that one track and resume where the
     * listener was, once. A second failure is reported.
     */
    private fun onPlaybackFailed() {
        val index = player.currentMediaItemIndex
        val track = queue.getOrNull(index) ?: run {
            publish(error = PlaybackError.Server)
            return
        }

        if (!retriedTracks.add(track.id)) {
            player.pause()
            publish(error = PlaybackError.Network)
            return
        }

        val position = player.currentPosition
        scope.launch {
            val url = resolver.resolve(listOf(track.id), forceRefresh = true)[track.id]
            if (url == null || !url.isPlayable) {
                publish(error = PlaybackError.Unavailable)
                return@launch
            }

            playable[track.id] = url
            player.replaceMediaItem(index, buildItem(track, requireNotNull(url.url)))
            player.seekTo(index, position)
            player.play()
            publish(error = null)
        }
    }

    private fun buildItem(track: Track, url: String): MediaItem = MediaItem.Builder()
        .setMediaId(track.id.toString())
        .setUri(url)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artistLine)
                .setArtworkUri(track.artworkUrl?.let(Uri::parse))
                .build(),
        )
        .build()

    private fun ensureService() {
        if (serviceStarted) return
        // The user started playback, so the app is in the foreground and a plain start is allowed.
        // Media3 promotes the service to foreground when it posts the media notification.
        context.startService(Intent(context, PlaybackService::class.java))
        serviceStarted = true
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                if (player.isPlaying) {
                    publish()
                    maybeReportListen()
                }
                delay(POSITION_TICK_MILLIS)
            }
        }
    }

    private fun maybeReportListen() {
        val track = queue.getOrNull(player.currentMediaItemIndex) ?: return
        val position = player.currentPosition
        val duration = player.duration.takeIf { it > 0 } ?: track.durationMs

        if (!scrobbler.shouldReport(track.id, position, duration)) return

        scrobbler.markReported(track.id)
        val playedSeconds = (position / 1_000).toInt()
        scope.launch { playbackApi.scrobble(track.id, playedSeconds) }
    }

    private fun publish(error: PlaybackError? = null) {
        val index = player.currentMediaItemIndex
        val current = queue.getOrNull(index)
        _state.value = PlaybackState(
            queue = queue,
            currentIndex = index,
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.coerceAtLeast(0),
            repeatMode = repeatMode,
            shuffle = player.shuffleModeEnabled,
            isTrial = current?.let { playable[it.id]?.isTrial } == true,
            error = error,
        )
    }

    private companion object {
        const val POSITION_TICK_MILLIS = 500L
        const val PLAY_WINDOW = 50
        const val RESOLVE_CHUNK = 50
    }
}
