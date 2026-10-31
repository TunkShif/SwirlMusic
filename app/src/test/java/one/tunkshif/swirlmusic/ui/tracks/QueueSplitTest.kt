package one.tunkshif.swirlmusic.ui.tracks

import com.google.common.truth.Truth.assertThat
import one.tunkshif.swirlmusic.player.queueSplit
import org.junit.Test

class QueueSplitTest {

    @Test
    fun `start at 0 leaves no head`() {
        val split = queueSplit(count = 100, startIndex = 0, windowSize = 50)
        assertThat(split.window).isEqualTo(0..49)
        assertThat(split.head.isEmpty()).isTrue()
        assertThat(split.tail).isEqualTo(50..99)
    }

    @Test
    fun `a middle start splits head and tail`() {
        val split = queueSplit(count = 100, startIndex = 30, windowSize = 50)
        assertThat(split.window).isEqualTo(30..79)
        assertThat(split.head).isEqualTo(0..29)
        assertThat(split.tail).isEqualTo(80..99)
    }

    @Test
    fun `a start inside the last window leaves no tail`() {
        val split = queueSplit(count = 100, startIndex = 90, windowSize = 50)
        assertThat(split.window).isEqualTo(90..99)
        assertThat(split.head).isEqualTo(0..89)
        assertThat(split.tail.isEmpty()).isTrue()
    }

    @Test
    fun `a start beyond the end clamps to the last track`() {
        val split = queueSplit(count = 100, startIndex = 250, windowSize = 50)
        assertThat(split.window).isEqualTo(99..99)
        assertThat(split.head).isEqualTo(0..98)
        assertThat(split.tail.isEmpty()).isTrue()
    }
}
