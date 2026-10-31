package one.tunkshif.swirlmusic.player

/**
 * When a play counts as a listen. NetEase counts a track after half of it, or four minutes,
 * whichever comes first.
 *
 * Pure logic, so the rule is testable without a player.
 */
class ScrobbleTracker(
    private val maxPlayedMillis: Long = 4 * 60 * 1_000L,
) {

    private var reported: Long? = null
    private var current: Long? = null

    fun shouldReport(trackId: Long, positionMillis: Long, durationMillis: Long): Boolean {
        if (trackId <= 0 || reported == trackId) return false
        val threshold = thresholdFor(durationMillis)
        return positionMillis >= threshold
    }

    fun markReported(trackId: Long) {
        reported = trackId
        current = trackId
    }

    /** A new track starts a new decision. */
    fun onTrackChanged(trackId: Long) {
        if (current != trackId) current = trackId
    }

    fun thresholdFor(durationMillis: Long): Long {
        if (durationMillis <= 0) return maxPlayedMillis
        return minOf(durationMillis / 2, maxPlayedMillis)
    }
}
