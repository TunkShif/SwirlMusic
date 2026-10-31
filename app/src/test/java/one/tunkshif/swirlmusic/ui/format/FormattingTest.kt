package one.tunkshif.swirlmusic.ui.format

import com.google.common.truth.Truth.assertThat
import one.tunkshif.swirlmusic.ui.home.DayPart
import one.tunkshif.swirlmusic.ui.home.dayPartFor
import org.junit.Test

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
}
