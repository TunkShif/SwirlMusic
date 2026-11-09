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
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
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
import one.tunkshif.swirlmusic.data.session.SessionRepository
import one.tunkshif.swirlmusic.data.session.SessionState
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
 * A shuffle order of `0 until count` with [first] first — the current track keeps playing
 * while everything else is shuffled behind it. Pure and unit-tested.
 */
internal fun shuffledWithFirst(count: Int, first: Int, random: Random): IntArray {
    val rest = (0 until count).filter { it != first }.shuffled(random)
    return intArrayOf(first) + rest.toIntArray()
}

/**
 * Walks the playback order of a shuffled timeline: start at the first window and follow the
 * next-window chain until it ends. Pure and unit-tested through the function parameters.
 */
internal fun playbackOrder(count: Int, firstIndex: () -> Int, nextIndex: (Int) -> Int): List<Int> {
    if (count <= 0) return emptyList()
    val first = firstIndex()
    if (first < 0) return List(count) { it }
    val order = ArrayList<Int>(count)
    var index = first
    while (index != C.INDEX_UNSET && order.size < count) {
        order += index
        index = nextIndex(index)
    }
    return order
}

/** The entry's new index after [removed] is taken out of a list of [sizeAfter] + 1 items. */
internal fun indexAfterRemoval(current: Int, removed: Int, sizeAfter: Int): Int = when {
    removed < current -> current - 1
    removed == current -> current.coerceAtMost(sizeAfter - 1)
    else -> current
}

/** The entry's new index after the item at [from] is moved to [to]. */
internal fun indexAfterMove(current: Int, from: Int, to: Int): Int = when {
    current == from -> to
    from < current && to >= current -> current - 1
    from > current && to <= current -> current + 1
    else -> current
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
    private val sessionRepository: SessionRepository,
    private val resumeStore: PlaybackResumeStore,
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

    /** Queue entries stay index-aligned 1:1 with the player's media items. */
    private var queue: List<QueueEntry> = emptyList()

    /** Uids identify an entry across reorder/removal; never reused within the process. */
    private var nextUid = 0L

    private val playable = mutableMapOf<Long, PlayableUrl>()
    private val retriedTracks = mutableSetOf<Long>()
    private var serviceStarted = false
    private var ticker: Job? = null
    private var restoredQueue: RestoredQueue? = null
    private var lastPositionSave = 0L
    private var shuffledOrder: List<Int> = emptyList()
    private var normalizingFlags = false
    private val random = Random

    /** `0 until size` without materialising thousands of ints per publish. */
    private class IdentityOrder(override val size: Int) : AbstractList<Int>() {
        override fun get(index: Int) = index
    }

    init {
        // Every mode loops; ListLoop is the default for a fresh player.
        player.repeatMode = Player.REPEAT_MODE_ALL
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
                    if (restoredQueue == null && queue.isNotEmpty()) {
                        saveResumeState(playGeneration, player.currentPosition)
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (!isPlaying && restoredQueue == null && queue.isNotEmpty()) {
                        saveResumeState(playGeneration, player.currentPosition)
                    }
                }

                override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                    recomputePlayOrder()
                }

                override fun onRepeatModeChanged(repeatMode: Int) {
                    normalizeFlags()
                }

                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    normalizeFlags()
                    recomputePlayOrder()
                }
            },
        )
        startTicker()

        // Restore the last queue so the mini player shows the last track, paused.
        scope.launch {
            val (tracks, restored) = resumeStore.load() ?: return@launch
            if (player.mediaItemCount > 0 || queue.isNotEmpty()) return@launch
            restoredQueue = restored
            queue = tracks.map { QueueEntry(nextUid(), it) }
            publish()
        }

        // Signing out stops playback and wipes the previous account's resume state.
        scope.launch {
            sessionRepository.state.collect { session ->
                if (session is SessionState.SignedOut) stopForSignOut()
            }
        }
    }

    private fun nextUid(): Long = nextUid++

    private fun isRestored() = restoredQueue != null && player.mediaItemCount == 0

    private fun currentIndex(): Int =
        if (isRestored()) restoredQueue!!.currentIndex else player.currentMediaItemIndex

    /**
     * The mode to report and persist. In restored mode the player still carries the fresh
     * ListLoop defaults; the restored queue's saved mode is the truth until playback starts.
     */
    private fun loopMode(): LoopMode =
        if (isRestored()) restoredQueue!!.loopMode
        else loopModeOf(player.repeatMode, player.shuffleModeEnabled)

    /** Halt any in-flight play job and empty both the queue and the player's items. */
    private fun clearPlayback() {
        playGeneration++
        playJob?.cancel()
        pendingTrack = null
        restoredQueue = null
        player.pause()
        player.clearMediaItems()
        queue = emptyList()
        playable.clear()
        retriedTracks.clear()
    }

    private fun stopForSignOut() {
        clearPlayback()
        serviceStarted = false
        context.stopService(Intent(context, PlaybackService::class.java))
        publish()
    }

    /** Swipe-away: pause at once, save where we are, and let the service die. */
    fun onTaskRemoved() {
        if (pendingTrack != null) playWhenReadyIntent = false
        player.pause()
        saveResumeState(playGeneration, player.currentPosition)
        publish()
    }

    /** The service ended on its own; the next playback must start it again. */
    fun onServiceStopped() {
        serviceStarted = false
    }

    /** Persist the queue order + resume row; a stale play() generation skips its write. */
    private fun saveResumeState(generation: Int, positionMs: Long) {
        if (generation != playGeneration) return
        scope.launch {
            // Nothing left to resume: a save racing a remove-only clear must not resurrect
            // the row.
            if (queue.isEmpty() && restoredQueue == null) {
                resumeStore.clear()
                return@launch
            }
            resumeStore.save(
                queue = queue.map { it.track },
                currentIndex = currentIndex().takeIf { it >= 0 } ?: 0,
                positionMs = positionMs,
                loopMode = loopMode(),
            )
        }
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
    private var pendingTrack: Track? = null
    private var playWhenReadyIntent = false

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun play(tracks: List<Track>, startIndex: Int, rest: TrackPager? = null, startPositionMs: Long = 0) {
        if (tracks.isEmpty()) return
        val generation = ++playGeneration
        playJob?.cancel()
        // Show the tapped track at once; the old song stops immediately.
        pendingTrack = tracks.getOrNull(startIndex.coerceIn(0, tracks.lastIndex))
        playWhenReadyIntent = true
        if (player.isPlaying) player.pause()
        publish(error = null)
        val pendingStartTrackId = pendingTrack?.id
        playJob = scope.launch {
            // Fast start: resolve only the window around the tapped track.
            val split = queueSplit(tracks.size, startIndex, PLAY_WINDOW)
            val window = tracks.subList(split.window.first, split.window.last + 1)
            val items = itemsFor(window)
            if (items.isEmpty()) {
                pendingTrack = null
                publish(error = PlaybackError.Unavailable)
                return@launch
            }
            // The start track must play: if it was dropped, start at the first playable.
            val windowTracks = items.map { it.first }
            val newStart = (windowTracks.indexOfFirst { it.id == window.getOrNull(0)?.id })
                .takeIf { it >= 0 } ?: 0
            queue = windowTracks.map { QueueEntry(nextUid(), it) }
            retriedTracks.clear()
            pendingTrack = null
            restoredQueue = null
            val seekMs = if (windowTracks[newStart].id == pendingStartTrackId) startPositionMs else 0
            player.setMediaItems(items.map { it.second }, newStart, seekMs)
            // setMediaItems replaces the shuffle order with a random one; put the start first again.
            if (player.shuffleModeEnabled && player.mediaItemCount > 1) {
                player.setShuffleOrder(
                    DefaultShuffleOrder(
                        shuffledWithFirst(player.mediaItemCount, newStart, random),
                        random.nextLong(),
                    ),
                )
            }
            player.prepare()
            player.playWhenReady = playWhenReadyIntent
            ensureService()
            publish(error = null)
            saveResumeState(generation, seekMs)

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
                    queue = headItems.map { QueueEntry(nextUid(), it.first) } + queue
                    publish()
                    saveResumeState(generation, player.currentPosition)
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
                queue = queue + items.map { QueueEntry(nextUid(), it.first) }
                player.addMediaItems(items.map { it.second })
                publish()
                saveResumeState(generation, player.currentPosition)
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
        if (pendingTrack != null) {
            playWhenReadyIntent = !playWhenReadyIntent
            publish()
            return@launch
        }
        val restored = restoredQueue
        if (restored != null && player.mediaItemCount == 0) {
            // Restore the loop mode first, then take the normal play path at the saved spot.
            applyLoopMode(restored.loopMode)
            play(queue.map { it.track }, restored.currentIndex, startPositionMs = restored.positionMs)
            return@launch
        }
        if (player.mediaItemCount == 0) return@launch
        if (player.isPlaying) {
            player.pause()
        } else {
            ensureService()
            player.play()
        }
    }

    fun next() = scope.launch {
        val restored = restoredQueue
        if (restored != null && player.mediaItemCount == 0) {
            // Every mode loops, so "next" wraps around.
            applyLoopMode(restored.loopMode)
            play(queue.map { it.track }, (restored.currentIndex + 1) % queue.size)
            return@launch
        }
        if (player.mediaItemCount == 0) return@launch
        player.seekToNext()
    }

    fun previous() = scope.launch {
        val restored = restoredQueue
        if (restored != null && player.mediaItemCount == 0) {
            applyLoopMode(restored.loopMode)
            play(queue.map { it.track }, (restored.currentIndex - 1 + queue.size) % queue.size)
            return@launch
        }
        if (player.mediaItemCount == 0) return@launch
        player.seekToPrevious()
    }

    /**
     * Always the previous track, never "restart current": `seekToPrevious()` restarts the
     * track when it has played past ~3 s, `seekToPreviousMediaItem()` never does.
     */
    fun previousTrack() = scope.launch {
        val restored = restoredQueue
        if (restored != null && player.mediaItemCount == 0) {
            applyLoopMode(restored.loopMode)
            play(queue.map { it.track }, (restored.currentIndex - 1 + queue.size) % queue.size)
            return@launch
        }
        if (player.mediaItemCount == 0 || !player.hasPreviousMediaItem()) return@launch
        player.seekToPreviousMediaItem()
    }

    /** Jump to a queue entry by uid. In restored mode this starts playback there. */
    fun skipTo(uid: Long) = scope.launch {
        val index = queue.indexOfFirst { it.uid == uid }
        if (index < 0) return@launch
        val restored = restoredQueue
        if (restored != null && player.mediaItemCount == 0) {
            applyLoopMode(restored.loopMode)
            play(queue.map { it.track }, index)
            return@launch
        }
        if (player.mediaItemCount == 0) return@launch
        player.seekTo(index, 0)
        ensureService()
        player.play()
    }

    /**
     * Remove a queue entry by uid. Removing the current entry lets ExoPlayer advance to the
     * next as it does; removing the only entry stops playback and wipes the resume state.
     */
    fun remove(uid: Long) = scope.launch {
        val index = queue.indexOfFirst { it.uid == uid }
        if (index < 0) return@launch
        if (queue.size == 1) {
            clearPlayback()
            serviceStarted = false
            context.stopService(Intent(context, PlaybackService::class.java))
            resumeStore.clear()
            publish()
            return@launch
        }
        if (player.mediaItemCount > 0) player.removeMediaItem(index)
        queue = queue.toMutableList().apply { removeAt(index) }
        restoredQueue?.let { restored ->
            restoredQueue = restored.copy(
                currentIndex = indexAfterRemoval(restored.currentIndex, index, queue.size),
            )
        }
        publish()
        saveResumeState(playGeneration, if (isRestored()) restoredQueue!!.positionMs else player.currentPosition)
    }

    /**
     * Move the entry [uid] to the position of [targetUid], like [List] move semantics. Disabled
     * in Shuffle: the play order no longer matches the list order.
     */
    fun move(uid: Long, targetUid: Long) = scope.launch {
        if (uid == targetUid) return@launch
        if (loopMode() == LoopMode.Shuffle) return@launch
        val from = queue.indexOfFirst { it.uid == uid }
        val to = queue.indexOfFirst { it.uid == targetUid }
        if (from < 0 || to < 0) return@launch
        if (player.mediaItemCount > 0) player.moveMediaItem(from, to)
        queue = queue.toMutableList().apply { add(to, removeAt(from)) }
        restoredQueue?.let { restored ->
            restoredQueue = restored.copy(
                currentIndex = indexAfterMove(restored.currentIndex, from, to),
            )
        }
        publish()
        saveResumeState(playGeneration, if (isRestored()) restoredQueue!!.positionMs else player.currentPosition)
    }

    fun seekTo(positionMs: Long) = scope.launch {
        val clamped = positionMs.coerceAtLeast(0)
        val restored = restoredQueue
        if (restored != null && player.mediaItemCount == 0) {
            // Restored mode has no prepared items; the seek only moves the resume point.
            restoredQueue = restored.copy(positionMs = clamped)
            publish()
            saveResumeState(playGeneration, clamped)
            return@launch
        }
        player.seekTo(clamped)
        publish()
    }

    fun cycleLoopMode() = scope.launch {
        val restored = restoredQueue
        if (restored != null && player.mediaItemCount == 0) {
            // The player is empty in restored mode; the mode lives on the restored queue only.
            restoredQueue = restored.copy(loopMode = restored.loopMode.next())
            publish()
            saveResumeState(playGeneration, restoredQueue!!.positionMs)
            return@launch
        }
        applyLoopMode(loopMode().next())
        publish()
        saveResumeState(playGeneration, if (isRestored()) restoredQueue!!.positionMs else player.currentPosition)
    }

    /**
     * Set the player flags to the canonical combo for [mode]. Entering Shuffle gives the current
     * track the first slot of the shuffle order so it keeps playing.
     */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun applyLoopMode(mode: LoopMode) {
        when (mode) {
            LoopMode.ListLoop -> {
                player.shuffleModeEnabled = false
                player.repeatMode = Player.REPEAT_MODE_ALL
            }

            LoopMode.Shuffle -> {
                // A shuffle order must match the item count; an empty player keeps flag state only.
                if (player.mediaItemCount > 1) {
                    player.setShuffleOrder(
                        DefaultShuffleOrder(
                            shuffledWithFirst(
                                player.mediaItemCount,
                                player.currentMediaItemIndex.coerceIn(0, player.mediaItemCount - 1),
                                random,
                            ),
                            random.nextLong(),
                        ),
                    )
                }
                // REPEAT_ALL first so shuffle+ONE never becomes visible.
                player.repeatMode = Player.REPEAT_MODE_ALL
                player.shuffleModeEnabled = true
            }

            LoopMode.RepeatOne -> {
                player.shuffleModeEnabled = false
                player.repeatMode = Player.REPEAT_MODE_ONE
            }
        }
    }

    /**
     * Anything can set the player flags (the notification, a Bluetooth controller). Fold every
     * combination into one of the three canonical ones: REPEAT_OFF -> ALL, shuffle+ONE ->
     * shuffle+ALL. Guarded so correcting the player doesn't recurse.
     */
    private fun normalizeFlags() {
        if (normalizingFlags) return
        normalizingFlags = true
        try {
            when (loopModeOf(player.repeatMode, player.shuffleModeEnabled)) {
                LoopMode.Shuffle -> {
                    if (player.repeatMode != Player.REPEAT_MODE_ALL) {
                        player.repeatMode = Player.REPEAT_MODE_ALL
                    }
                }

                LoopMode.RepeatOne -> {
                    if (player.shuffleModeEnabled) player.shuffleModeEnabled = false
                }

                LoopMode.ListLoop -> {
                    if (player.repeatMode != Player.REPEAT_MODE_ALL) {
                        player.repeatMode = Player.REPEAT_MODE_ALL
                    }
                }
            }
        } finally {
            normalizingFlags = false
        }
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
            if (isRestored()) {
                // The player is empty; inserting a media item would silently leave restored mode.
                queue = if (next) {
                    queue.toMutableList().apply {
                        add((restoredQueue!!.currentIndex + 1).coerceAtMost(size), QueueEntry(nextUid(), track))
                    }
                } else {
                    queue + QueueEntry(nextUid(), track)
                }
                publish()
                saveResumeState(playGeneration, restoredQueue!!.positionMs)
                return@launch
            }
            queue = if (next) {
                queue.toMutableList().apply {
                    add((currentIndex() + 1).coerceAtMost(size), QueueEntry(nextUid(), track))
                }
            } else {
                queue + QueueEntry(nextUid(), track)
            }

            val insertAt = if (next) {
                (player.currentMediaItemIndex + 1).coerceAtMost(player.mediaItemCount)
            } else {
                player.mediaItemCount
            }
            player.addMediaItem(insertAt, buildItem(track, requireNotNull(url.url)))
            publish()
            saveResumeState(playGeneration, player.currentPosition)
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
        val entry = queue.getOrNull(nextIndex) ?: return
        if (resolver.isFresh(entry.track.id)) return
        val generation = playGeneration
        val url = resolver.resolve(listOf(entry.track.id), forceRefresh = true)[entry.track.id]
            ?.takeIf { it.isPlayable } ?: return
        if (generation != playGeneration) return
        // The head insert or a move may have shifted indices while resolving: re-locate by uid.
        val index = if (queue.getOrNull(nextIndex)?.uid == entry.uid) {
            nextIndex
        } else {
            queue.indexOfFirst { it.uid == entry.uid }
        }
        if (index < 0) return
        playable[entry.track.id] = url
        player.replaceMediaItem(index, buildItem(entry.track, requireNotNull(url.url)))
    }

    /**
     * A signed URL expired, or the CDN refused it. Refetch that one track and resume where the
     * listener was, once. A second failure is reported.
     */
    private fun onPlaybackFailed() {
        val index = player.currentMediaItemIndex
        val entry = queue.getOrNull(index) ?: run {
            publish(error = PlaybackError.Server)
            return
        }

        if (!retriedTracks.add(entry.track.id)) {
            player.pause()
            publish(error = PlaybackError.Network)
            return
        }

        val position = player.currentPosition
        scope.launch {
            val url = resolver.resolve(listOf(entry.track.id), forceRefresh = true)[entry.track.id]
            if (url == null || !url.isPlayable) {
                publish(error = PlaybackError.Unavailable)
                return@launch
            }

            // The entry may have moved while the URL was resolving: re-locate by uid.
            val nowIndex = queue.indexOfFirst { it.uid == entry.uid }
            if (nowIndex < 0) return@launch
            playable[entry.track.id] = url
            player.replaceMediaItem(nowIndex, buildItem(entry.track, requireNotNull(url.url)))
            player.seekTo(nowIndex, position)
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
                    val now = System.currentTimeMillis()
                    if (restoredQueue == null && now - lastPositionSave >= 10_000) {
                        lastPositionSave = now
                        saveResumeState(playGeneration, player.currentPosition)
                    }
                }
                delay(POSITION_TICK_MILLIS)
            }
        }
    }

    private fun maybeReportListen() {
        val track = queue.getOrNull(player.currentMediaItemIndex)?.track ?: return
        val position = player.currentPosition
        val duration = player.duration.takeIf { it > 0 } ?: track.durationMs

        if (!scrobbler.shouldReport(track.id, position, duration)) return

        scrobbler.markReported(track.id)
        val playedSeconds = (position / 1_000).toInt()
        scope.launch { playbackApi.scrobble(track.id, playedSeconds) }
    }

    /**
     * The shuffled play order, cached from the timeline. Recomputed on timeline/shuffle changes
     * only — never per tick, since queues can run to thousands of entries. The non-shuffled
     * order is a virtual identity list built at publish time.
     */
    private fun recomputePlayOrder() {
        shuffledOrder = if (player.shuffleModeEnabled && player.mediaItemCount > 0) {
            val timeline = player.currentTimeline
            playbackOrder(
                timeline.windowCount,
                { timeline.getFirstWindowIndex(true) },
                { timeline.getNextWindowIndex(it, Player.REPEAT_MODE_OFF, true) },
            )
        } else {
            emptyList()
        }
    }

    private fun publish(error: PlaybackError? = null) {
        val restored = isRestored()
        val index = currentIndex()
        val current = queue.getOrNull(index)
        _state.value = PlaybackState(
            queue = queue,
            currentIndex = index,
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            positionMs = if (restored) {
                restoredQueue?.positionMs ?: 0
            } else {
                player.currentPosition.coerceAtLeast(0)
            },
            durationMs = if (restored) current?.track?.durationMs ?: 0 else player.duration.coerceAtLeast(0),
            loopMode = loopMode(),
            playOrder = if (player.shuffleModeEnabled && player.mediaItemCount > 0) {
                shuffledOrder
            } else {
                IdentityOrder(queue.size)
            },
            hasNext = if (restored) queue.size > 1 else player.hasNextMediaItem(),
            hasPrevious = if (restored) queue.size > 1 else player.hasPreviousMediaItem(),
            isTrial = current?.let { playable[it.track.id]?.isTrial } == true,
            quality = current?.let { playable[it.track.id] }?.let {
                StreamQuality(level = it.level, bitrateKbps = it.bitrate / 1_000, format = it.format)
            },
            error = error,
            pending = pendingTrack,
            playIntent = playWhenReadyIntent,
            restored = restored,
        )
    }

    private companion object {
        const val POSITION_TICK_MILLIS = 500L
        const val PLAY_WINDOW = 50
        const val RESOLVE_CHUNK = 50
    }
}
