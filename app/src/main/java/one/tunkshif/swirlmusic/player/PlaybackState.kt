package one.tunkshif.swirlmusic.player

import androidx.media3.common.Player
import one.tunkshif.swirlmusic.data.model.Track

/**
 * The three queue-loop modes. There is no "stop at the end": every mode loops, they differ in
 * order and in whether the current track repeats.
 */
enum class LoopMode { ListLoop, Shuffle, RepeatOne }

fun LoopMode.next(): LoopMode = when (this) {
    LoopMode.ListLoop -> LoopMode.Shuffle
    LoopMode.Shuffle -> LoopMode.RepeatOne
    LoopMode.RepeatOne -> LoopMode.ListLoop
}

/** Player flags -> mode. Shuffle wins; else REPEAT_MODE_ONE -> RepeatOne; else ListLoop. */
internal fun loopModeOf(playerRepeatMode: Int, shuffle: Boolean): LoopMode = when {
    shuffle -> LoopMode.Shuffle
    playerRepeatMode == Player.REPEAT_MODE_ONE -> LoopMode.RepeatOne
    else -> LoopMode.ListLoop
}

/**
 * One queue slot. [uid] identifies the entry across reorder and removal; it is never reused
 * within the process, so two plays of the same track stay distinct.
 */
data class QueueEntry(val uid: Long, val track: Track)

enum class PlaybackError {
    /** The server returned no URL: taken down, or premium only. */
    Unavailable,

    Network,
    Server,
}

/**
 * Everything the UI needs about playback. One immutable snapshot, emitted by
 * [PlayerController].
 */
data class PlaybackState(
    val queue: List<QueueEntry> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val loopMode: LoopMode = LoopMode.ListLoop,
    /** Queue indices in the order they will play; identity order when not shuffled. */
    val playOrder: List<Int> = emptyList(),
    val hasNext: Boolean = false,
    val isTrial: Boolean = false,
    val error: PlaybackError? = null,
    /** A tapped track waiting for its URL — the mini player shows it before audio starts. */
    val pending: Track? = null,
    /** What the play/pause button means while resolving: the intent, not the player state. */
    val playIntent: Boolean = false,
    /** Queue/position was restored from storage; the player holds no prepared items yet. */
    val restored: Boolean = false,
) {
    val current: Track? get() = pending ?: queue.getOrNull(currentIndex)?.track

    val isLoading: Boolean get() = pending != null || isBuffering

    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}
