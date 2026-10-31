package one.tunkshif.swirlmusic.ui.tracks

import com.google.common.truth.Truth.assertThat
import one.tunkshif.swirlmusic.data.model.Track
import org.junit.Test

class AlbumRowsTest {
    private fun t(id: Long, disc: Int? = null, no: Int? = null) =
        Track(id, "t$id", "a", null, 0, disc, no)

    @Test
    fun `single disc gets no headers and sequential numbers`() {
        val rows = albumRows(listOf(t(1, 1), t(2, 1), t(3)))
        assertThat(rows.filterIsInstance<AlbumRow.Disc>()).isEmpty()
        assertThat(rows.filterIsInstance<AlbumRow.Song>().map { it.number }).containsExactly(1, 2, 3).inOrder()
    }

    @Test
    fun `two discs get a header at the boundary`() {
        val rows = albumRows(listOf(t(1, 1), t(2, 1), t(3, 2), t(4, 2)))
        assertThat(rows[0]).isEqualTo(AlbumRow.Disc(1))
        assertThat(rows[3]).isEqualTo(AlbumRow.Disc(2))
        assertThat(rows.filterIsInstance<AlbumRow.Song>().map { it.index }).containsExactly(0, 1, 2, 3).inOrder()
        // Per-disc fallback numbering: song 3 is position 1 on disc 2.
        assertThat((rows[4] as AlbumRow.Song).number).isEqualTo(1)
    }

    @Test
    fun `explicit track numbers win over the fallback`() {
        val rows = albumRows(listOf(t(1, 1, 7)))
        assertThat((rows[0] as AlbumRow.Song).number).isEqualTo(7)
    }
}
