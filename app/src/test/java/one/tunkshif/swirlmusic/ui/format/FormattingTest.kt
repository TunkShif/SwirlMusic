package one.tunkshif.swirlmusic.ui.format

import com.google.common.truth.Truth.assertThat
import one.tunkshif.swirlmusic.ui.home.DayPart
import one.tunkshif.swirlmusic.ui.home.dayPartFor
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FormattingTest {

    @Test
    fun `counts are shortened with one decimal at most`() {
        assertThat(formatCount(0)).isEqualTo("0")
        assertThat(formatCount(980)).isEqualTo("980")
        assertThat(formatCount(1_000)).isEqualTo("1K")
        assertThat(formatCount(12_345)).isEqualTo("12.3K")
        assertThat(formatCount(1_234_567)).isEqualTo("1.2M")
        assertThat(formatCount(25_388_898_304)).isEqualTo("25.4B")
    }

    @Test
    fun `durations are minutes and seconds, with hours only when needed`() {
        assertThat(formatDuration(0)).isEqualTo("0:00")
        assertThat(formatDuration(187_000)).isEqualTo("3:07")
        assertThat(formatDuration(3_723_000)).isEqualTo("1:02:03")
    }

    @Test
    fun `the day is split into four greetings`() {
        assertThat(dayPartFor(0)).isEqualTo(DayPart.Night)
        assertThat(dayPartFor(4)).isEqualTo(DayPart.Night)
        assertThat(dayPartFor(5)).isEqualTo(DayPart.Morning)
        assertThat(dayPartFor(11)).isEqualTo(DayPart.Morning)
        assertThat(dayPartFor(12)).isEqualTo(DayPart.Afternoon)
        assertThat(dayPartFor(17)).isEqualTo(DayPart.Afternoon)
        assertThat(dayPartFor(18)).isEqualTo(DayPart.Evening)
        assertThat(dayPartFor(21)).isEqualTo(DayPart.Evening)
        assertThat(dayPartFor(22)).isEqualTo(DayPart.Night)
        assertThat(dayPartFor(23)).isEqualTo(DayPart.Night)
    }


    @Test
    fun `the quality label joins level bitrate and format`() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        fun name(level: String) = qualityLevelRes(level)?.let { ctx.getString(it) }

        assertThat(formatQualityLabel(name("exhigh"), 320, "mp3"))
            .isEqualTo("Extra high · 320 kbps · MP3")
        assertThat(formatQualityLabel(name("lossless"), 1119, "flac"))
            .isEqualTo("Lossless · 1119 kbps · FLAC")
        assertThat(formatQualityLabel(name("standard"), 128, "mp3"))
            .isEqualTo("Standard · 128 kbps · MP3")
        assertThat(formatQualityLabel(name("hires"), 2000, null))
            .isEqualTo("Hi-Res · 2000 kbps")
        // Unknown level drops its part; lowercase formats still read uppercase.
        assertThat(formatQualityLabel(name("weird"), 320, "mp3")).isEqualTo("320 kbps · MP3")
        assertThat(formatQualityLabel(null, 0, null)).isEmpty()
        assertThat(formatQualityLabel(name("sky"), 320, "flac"))
            .isEqualTo("Surround · 320 kbps · FLAC")
    }

    @Test
    fun `every known level has a name`() {
        for (level in listOf(
            "standard", "higher", "exhigh", "lossless", "hires", "jyeffect", "sky", "jymaster",
        )) {
            assertThat(qualityLevelRes(level)).isNotNull()
        }
        assertThat(qualityLevelRes("dolby")).isNull()
    }
}
