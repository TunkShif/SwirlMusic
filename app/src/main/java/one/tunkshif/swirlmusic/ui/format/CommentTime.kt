package one.tunkshif.swirlmusic.ui.format

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * How far back a comment sits, for the meta line. The composable resolves each case to a
 * resource string (or [formatCommentDate] for dates) — keeping this pure means the boundary
 * rules are testable without a Context.
 *
 * `timeMillis` in the future or under a minute old is [JustNow]; then whole minutes under an
 * hour, whole hours under a day, the previous calendar day in [zone], a bare date inside the
 * current year, and a dated year further back.
 */
sealed interface CommentTime {
    data object JustNow : CommentTime
    data class MinutesAgo(val minutes: Int) : CommentTime
    data class HoursAgo(val hours: Int) : CommentTime
    data object Yesterday : CommentTime

    /** Same calendar year as [commentTimeOf]'s `now`: a localized month+day. */
    data class ThisYear(val millis: Long) : CommentTime

    /** Earlier than that: a localized month+day+year. */
    data class Older(val millis: Long) : CommentTime
}

fun commentTimeOf(
    timeMillis: Long,
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): CommentTime {
    val diff = nowMillis - timeMillis
    return when {
        diff < 60_000L -> CommentTime.JustNow
        diff < 3_600_000L -> CommentTime.MinutesAgo((diff / 60_000L).toInt())
        diff < 86_400_000L -> CommentTime.HoursAgo((diff / 3_600_000L).toInt())
        else -> {
            val day = Instant.ofEpochMilli(timeMillis).atZone(zone).toLocalDate()
            val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
            when {
                day == today.minusDays(1) -> CommentTime.Yesterday
                day.year == today.year -> CommentTime.ThisYear(timeMillis)
                else -> CommentTime.Older(timeMillis)
            }
        }
    }
}

/** The date part of a comment's meta line: `Mar 6` this year, `Mar 6, 2021` before. */
fun formatCommentDate(
    millis: Long,
    withYear: Boolean,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String {
    val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val formatter = if (withYear) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    } else {
        // Localized medium always carries the year; a pattern drops it while keeping the
        // locale's month name and field order for the US-style "Mar 6" look.
        DateTimeFormatter.ofPattern("MMM d", locale)
    }
    return date.format(formatter)
}
