package one.tunkshif.swirlmusic.ui.playing

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DisplayedPositionTest {

    @Test
    fun `a drag shows the drag value, not the reported one`() {
        assertThat(displayedPositionMs(reportedMs = 5_000, dragMs = 12_000, hold = null, nowMs = 0))
            .isEqualTo(12_000)
    }

    @Test
    fun `a hold shows the target while the report is still stale`() {
        val hold = SeekHold(targetMs = 60_000, releasedAtMs = 10_000)

        assertThat(displayedPositionMs(reportedMs = 5_000, dragMs = null, hold = hold, nowMs = 10_500))
            .isEqualTo(60_000)
        assertThat(hold.releaseIfCaughtUp(reportedMs = 5_000, nowMs = 10_500)).isEqualTo(hold)
    }

    @Test
    fun `a hold releases when the report catches up`() {
        val hold = SeekHold(targetMs = 60_000, releasedAtMs = 10_000)

        assertThat(hold.releaseIfCaughtUp(reportedMs = 60_500, nowMs = 10_500)).isNull()
        assertThat(hold.releaseIfCaughtUp(reportedMs = 59_001, nowMs = 10_500)).isNull()
    }

    @Test
    fun `a hold releases on timeout even when the report never catches up`() {
        val hold = SeekHold(targetMs = 60_000, releasedAtMs = 10_000)

        assertThat(hold.releaseIfCaughtUp(reportedMs = 5_000, nowMs = 12_000)).isNull()
        assertThat(hold.releaseIfCaughtUp(reportedMs = 5_000, nowMs = 11_999)).isEqualTo(hold)
    }

    @Test
    fun `rebase only when the report moved beyond the threshold`() {
        // Tick noise: within 300 ms, keep extrapolating.
        assertThat(shouldRebasePosition(reportedMs = 10_250, extrapolatedMs = 10_400)).isFalse()
        assertThat(shouldRebasePosition(reportedMs = 10_100, extrapolatedMs = 10_500)).isTrue()
        assertThat(shouldRebasePosition(reportedMs = 10_900, extrapolatedMs = 10_500)).isTrue()
    }

    @Test
    fun `a released seek holds the target then follows the player`() {
        var now = 1_000L
        val position = DisplayedPosition(initialMs = 0, now = { now })

        position.onSeeked(targetMs = 30_000)
        assertThat(position.ms).isEqualTo(30_000)

        // A stale report does not bounce the bar back.
        now = 1_200
        position.onReported(reportedMs = 800, durationMs = 60_000, isPlaying = true)
        assertThat(position.ms).isEqualTo(30_000)

        // The report catches up: extrapolation continues from the target.
        now = 1_400
        position.onReported(reportedMs = 30_200, durationMs = 60_000, isPlaying = true)
        now = 1_900
        position.onFrame(reportedMs = 30_200, durationMs = 60_000)
        assertThat(position.ms).isGreaterThan(30_000)
    }

    @Test
    fun `resuming after a pause does not leap by the paused duration`() {
        var now = 0L
        val position = DisplayedPosition(initialMs = 10_000, now = { now })

        // Play briefly, then pause at 20 s.
        now = 500
        position.onReported(reportedMs = 20_000, durationMs = 60_000, isPlaying = false)

        // Sit paused for a minute; reports keep landing at the paused position.
        now = 60_500
        position.onReported(reportedMs = 20_000, durationMs = 60_000, isPlaying = false)

        // Resume: the first frame extrapolates from the pause point, not +60 s.
        now = 60_600
        position.onFrame(reportedMs = 20_000, durationMs = 60_000)
        assertThat(position.ms).isAtLeast(20_000L)
        assertThat(position.ms).isAtMost(20_500L)
    }
}
