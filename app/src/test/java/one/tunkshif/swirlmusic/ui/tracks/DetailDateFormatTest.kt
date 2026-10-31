package one.tunkshif.swirlmusic.ui.tracks

import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import java.util.Locale
import org.junit.Test

class DetailDateFormatTest {

    private val released = 1155600000000L // 2006-08-15 00:00 UTC

    @Test
    fun `released is a UTC date and never shifts a day`() {
        for (zone in listOf("America/Los_Angeles", "Asia/Shanghai")) {
            assertThat(
                formatDetailDate(DateKind.Released, released, ZoneId.of(zone), Locale.US),
            ).isEqualTo("Aug 15, 2006")
        }
    }

    @Test
    fun `created follows the supplied zone`() {
        assertThat(
            formatDetailDate(DateKind.Created, released, ZoneId.of("Asia/Shanghai"), Locale.US),
        ).isEqualTo("Aug 15, 2006")
        assertThat(
            formatDetailDate(DateKind.Created, released, ZoneId.of("America/Los_Angeles"), Locale.US),
        ).isEqualTo("Aug 14, 2006")
    }
}
