package one.tunkshif.swirlmusic.ui.tracks

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HeaderGeometryTest {

    private val dims = HeaderDims(
        barHeight = 200f,
        artworkExpandedSize = 336f,
        artworkExpandedX = 72f,
        artworkExpandedY = 224f,
        artworkCollapsedSize = 120f,
        artworkCollapsedX = 240f,
        artworkCollapsedY = 40f,
        contentExpandedY = 500f,
    )
    private val expandedHeight = 900f

    @Test
    fun `expanded shows the big title and hides the bar title and play button`() {
        val g = headerGeometry(0f, dims, expandedHeight)
        assertThat(g.height).isEqualTo(900f)
        assertThat(g.artworkSize).isEqualTo(336f)
        assertThat(g.artworkX).isEqualTo(72f)
        assertThat(g.artworkY).isEqualTo(224f)
        assertThat(g.bigTitleAlpha).isEqualTo(1f)
        assertThat(g.barTitleAlpha).isEqualTo(0f)
        assertThat(g.contentAlpha).isEqualTo(1f)
        assertThat(g.playButtonAlpha).isEqualTo(0f)
        assertThat(g.playButtonEnabled).isFalse()
    }

    @Test
    fun `mid collapse fades the expanded content out`() {
        val g = headerGeometry(0.5f, dims, expandedHeight)
        assertThat(g.height).isEqualTo(550f)
        assertThat(g.artworkSize).isEqualTo(228f)
        assertThat(g.bigTitleAlpha).isEqualTo(0f)
        assertThat(g.contentAlpha).isEqualTo(0f)
        assertThat(g.barTitleAlpha).isEqualTo(0f)
        assertThat(g.playButtonAlpha).isEqualTo(0f)
        assertThat(g.playButtonEnabled).isFalse()
    }

    @Test
    fun `collapsed shows the bar title and the play button`() {
        val g = headerGeometry(1f, dims, expandedHeight)
        assertThat(g.height).isEqualTo(200f)
        assertThat(g.artworkSize).isEqualTo(120f)
        assertThat(g.artworkX).isEqualTo(240f)
        assertThat(g.artworkY).isEqualTo(40f)
        assertThat(g.artworkCorner).isEqualTo(10f)
        assertThat(g.barTitleAlpha).isWithin(0.001f).of(1f)
        assertThat(g.playButtonAlpha).isWithin(0.001f).of(1f)
        assertThat(g.playButtonScale).isWithin(0.001f).of(1f)
        assertThat(g.playButtonEnabled).isTrue()
    }

    @Test
    fun `out of range fractions clamp`() {
        assertThat(headerGeometry(-1f, dims, expandedHeight).artworkSize).isEqualTo(336f)
        assertThat(headerGeometry(2f, dims, expandedHeight).artworkSize).isEqualTo(120f)
    }
}
