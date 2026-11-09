package one.tunkshif.swirlmusic.ui.format

import androidx.annotation.StringRes
import java.util.Locale
import one.tunkshif.swirlmusic.R

/**
 * Play counts the way a list reads them: `980`, `12.3K`, `1.2M`.
 *
 * Pinned to [Locale.US] so a golden image and a test agree regardless of device language.
 */
fun formatCount(count: Long): String = when {
    count <= 0 -> "0"
    count < 1_000 -> count.toString()
    count < 1_000_000 -> oneDecimal(count / 1_000.0) + "K"
    count < 1_000_000_000 -> oneDecimal(count / 1_000_000.0) + "M"
    // NetEase reports aggregated counts for personalised playlists: 私人雷达 alone is 25.4B.
    else -> oneDecimal(count / 1_000_000_000.0) + "B"
}

private fun oneDecimal(value: Double): String {
    val rounded = String.format(Locale.US, "%.1f", value)
    return if (rounded.endsWith(".0")) rounded.dropLast(2) else rounded
}

/** `/song/url/v1` `level` values we can name; anything else shows no level. */
@StringRes
fun qualityLevelRes(level: String): Int? = when (level) {
    "standard" -> R.string.quality_standard
    "higher" -> R.string.quality_higher
    "exhigh" -> R.string.quality_exhigh
    "lossless" -> R.string.quality_lossless
    "hires" -> R.string.quality_hires
    "jyeffect" -> R.string.quality_jyeffect
    "sky" -> R.string.quality_sky
    "jymaster" -> R.string.quality_jymaster
    else -> null
}

/**
 * The Now Playing quality label, `"Extra high · 320 kbps · MP3"`. A null [levelName] (unknown
 * level) or [format] drops that part; nothing left means no label.
 */
fun formatQualityLabel(levelName: String?, bitrateKbps: Int, format: String?): String =
    buildList {
        levelName?.let(::add)
        if (bitrateKbps > 0) add("$bitrateKbps kbps")
        format?.takeIf { it.isNotBlank() }?.let { add(it.uppercase(Locale.US)) }
    }.joinToString(" · ")

/** `3:07`, or `1:02:03` for long tracks. */
fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "0:00"
    val totalSeconds = durationMs / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
