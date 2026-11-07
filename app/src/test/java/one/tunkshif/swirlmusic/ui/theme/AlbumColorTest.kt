package one.tunkshif.swirlmusic.ui.theme

import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.google.common.truth.Truth.assertThat
import com.materialkolor.hct.Hct
import kotlin.math.abs
import kotlin.math.min
import org.junit.Test

/** The seed picker and scheme mapping. Pure JVM: material-color-utilities is plain Kotlin. */
class AlbumColorTest {

    private fun hueDistance(a: Double, b: Double): Double {
        val d = abs(a - b) % 360.0
        return min(d, 360.0 - d)
    }

    @Test
    fun `the dominant colour becomes the seed`() {
        val red = 0xFFD32F2F.toInt()
        val blue = 0xFF1976D2.toInt()
        val pixels = IntArray(400) { if (it < 320) red else blue }

        val seed = seedFromPixels(pixels)

        assertThat(seed).isNotNull()
        assertThat(hueDistance(Hct.fromInt(seed!!).hue, Hct.fromInt(red).hue)).isLessThan(20.0)
    }

    @Test
    fun `a greyscale cover keeps a grey seed`() {
        val seed = seedFromPixels(IntArray(200) { 0xFF808080.toInt() })

        assertThat(seed).isNotNull()
        assertThat(Hct.fromInt(seed!!).chroma).isLessThan(8.0)
    }

    @Test
    fun `no pixels means no seed`() {
        assertThat(seedFromPixels(IntArray(0))).isNull()
    }

    @Test
    fun `a light seed scheme is light, a dark one dark`() {
        assertThat(albumColorScheme(0xFF9AA0C8.toInt(), isDark = false).background.luminance())
            .isGreaterThan(0.5f)
        assertThat(albumColorScheme(0xFF9AA0C8.toInt(), isDark = true).background.luminance())
            .isLessThan(0.5f)
    }

    @Test
    fun `primary follows the seed hue`() {
        val seed = 0xFF2F6FDB.toInt()
        val scheme = albumColorScheme(seed, isDark = false)

        assertThat(hueDistance(Hct.fromInt(scheme.primary.toArgb()).hue, Hct.fromInt(seed).hue))
            .isLessThan(30.0)
    }

    @Test
    fun `onPrimary contrasts with primary`() {
        val scheme = albumColorScheme(0xFF2F6FDB.toInt(), isDark = false)

        val lighter = maxOf(scheme.primary.luminance(), scheme.onPrimary.luminance())
        val darker = minOf(scheme.primary.luminance(), scheme.onPrimary.luminance())

        assertThat((lighter + 0.05f) / (darker + 0.05f)).isAtLeast(4.5f)
    }
}
