package one.tunkshif.swirlmusic.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ScrobbleTrackerTest {

    private val tracker = ScrobbleTracker(maxPlayedMillis = 240_000)

    @Test
    fun `half of a short track counts`() {
        assertThat(tracker.shouldReport(1, 59_000, 120_000)).isFalse()
        assertThat(tracker.shouldReport(1, 60_000, 120_000)).isTrue()
    }

    @Test
    fun `a long track counts at four minutes`() {
        assertThat(tracker.thresholdFor(3_600_000)).isEqualTo(240_000)
        assertThat(tracker.shouldReport(2, 239_000, 3_600_000)).isFalse()
        assertThat(tracker.shouldReport(2, 240_000, 3_600_000)).isTrue()
    }

    @Test
    fun `an unknown duration waits for the cap`() {
        assertThat(tracker.thresholdFor(0)).isEqualTo(240_000)
    }

    @Test
    fun `a track is reported once`() {
        assertThat(tracker.shouldReport(1, 100_000, 120_000)).isTrue()
        tracker.markReported(1)

        assertThat(tracker.shouldReport(1, 110_000, 120_000)).isFalse()
    }

    @Test
    fun `a new track can be reported`() {
        tracker.markReported(1)

        assertThat(tracker.shouldReport(2, 200_000, 120_000)).isTrue()
    }

    @Test
    fun `an invalid id is never reported`() {
        assertThat(tracker.shouldReport(0, 999_000, 1_000)).isFalse()
    }
}
