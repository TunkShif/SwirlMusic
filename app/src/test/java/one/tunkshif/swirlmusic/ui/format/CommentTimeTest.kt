package one.tunkshif.swirlmusic.ui.format

import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import org.junit.Test

class CommentTimeTest {

    private val zone: ZoneId = ZoneId.of("UTC")

    private fun millis(iso: String): Long =
        ZonedDateTime.parse(iso).toInstant().toEpochMilli()

    private val now = millis("2026-03-06T12:00:00Z")

    private fun of(timeMillis: Long) = commentTimeOf(timeMillis, now, zone)

    @Test
    fun `under a minute is just now`() {
        assertThat(of(now - 59_000L)).isEqualTo(CommentTime.JustNow)
        assertThat(of(now + 5_000L)).isEqualTo(CommentTime.JustNow)
    }

    @Test
    fun `under an hour is minutes ago`() {
        assertThat(of(now - 60_000L)).isEqualTo(CommentTime.MinutesAgo(1))
        assertThat(of(now - 59L * 60_000L)).isEqualTo(CommentTime.MinutesAgo(59))
    }

    @Test
    fun `under a day is hours ago`() {
        assertThat(of(now - 60L * 60_000L)).isEqualTo(CommentTime.HoursAgo(1))
        assertThat(of(now - 23L * 3_600_000L)).isEqualTo(CommentTime.HoursAgo(23))
    }

    @Test
    fun `the previous calendar day is yesterday even past 24 hours`() {
        // 2026-03-05 11:00 is 25 h before noon on the 6th, but still "yesterday".
        assertThat(of(millis("2026-03-05T11:00:00Z"))).isEqualTo(CommentTime.Yesterday)
        // Just past midnight: 2026-03-05 23:59 is 12 h old, so hours win first.
        assertThat(of(millis("2026-03-05T23:59:00Z"))).isEqualTo(CommentTime.HoursAgo(12))
    }

    @Test
    fun `older dates in the same year show month and day`() {
        val time = of(millis("2026-01-15T08:00:00Z"))
        assertThat(time).isEqualTo(CommentTime.ThisYear(millis("2026-01-15T08:00:00Z")))
        assertThat(
            formatCommentDate(
                (time as CommentTime.ThisYear).millis,
                withYear = false,
                zone = zone,
                locale = Locale.US,
            ),
        ).isEqualTo("Jan 15")
    }

    @Test
    fun `a previous year shows the full date`() {
        val time = of(millis("2025-12-31T23:00:00Z"))
        assertThat(time).isEqualTo(CommentTime.Older(millis("2025-12-31T23:00:00Z")))
        assertThat(
            formatCommentDate(
                (time as CommentTime.Older).millis,
                withYear = true,
                zone = zone,
                locale = Locale.US,
            ),
        ).isEqualTo("Dec 31, 2025")
    }

    @Test
    fun `the zone decides which calendar day counts`() {
        // 2026-03-05T20:30Z is Mar 5 in UTC but Mar 6 04:30 in Shanghai — same day there.
        val t = millis("2026-03-05T20:30:00Z")
        val shanghai = ZoneId.of("Asia/Shanghai")
        val nowShanghai = ZonedDateTime.parse("2026-03-06T04:31:00+08:00").toInstant().toEpochMilli()
        assertThat(commentTimeOf(t, nowShanghai, shanghai)).isEqualTo(CommentTime.MinutesAgo(1))
    }
}
