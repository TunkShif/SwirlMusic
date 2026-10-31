package one.tunkshif.swirlmusic.player

import one.tunkshif.swirlmusic.data.model.Track

enum class RepeatMode { Off, All, One }

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
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val repeatMode: RepeatMode = RepeatMode.Off,
    val shuffle: Boolean = false,
    val isTrial: Boolean = false,
    val error: PlaybackError? = null,
) {
    val current: Track? get() = queue.getOrNull(currentIndex)

    val hasNext: Boolean
        get() = when (repeatMode) {
            RepeatMode.All -> queue.isNotEmpty()
            else -> currentIndex in 0 until queue.lastIndex
        }

    val hasPrevious: Boolean
        get() = when (repeatMode) {
            RepeatMode.All -> queue.isNotEmpty()
            else -> currentIndex > 0
        }

    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}
