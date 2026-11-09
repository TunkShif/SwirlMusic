package one.tunkshif.swirlmusic.data.media

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** File-name and extension rules for the gallery export; pure functions, no MediaStore. */
class ArtworkExporterTest {

    @Test
    fun `the sanitizer rewrites forbidden characters and trims`() {
        assertThat(sanitizeFileName("a/b\\c:d*e?f\"g<h>i|j")).isEqualTo("a_b_c_d_e_f_g_h_i_j")
        assertThat(sanitizeFileName("  padded  ")).isEqualTo("padded")
        assertThat(sanitizeFileName("ok\tnewline\nend")).isEqualTo("ok_newline_end")
        assertThat(sanitizeFileName("")).isEqualTo("artwork")
        assertThat(sanitizeFileName("   ")).isEqualTo("artwork")
        assertThat(sanitizeFileName("x".repeat(200))).hasLength(80)
        assertThat(sanitizeFileName("Imaginal Disk")).isEqualTo("Imaginal Disk")
    }

    @Test
    fun `png keeps its extension and everything else is jpeg`() {
        assertThat(extension("image/png")).isEqualTo("png")
        assertThat(extension("image/png; charset=binary")).isEqualTo("png")
        assertThat(extension("image/jpeg")).isEqualTo("jpg")
        assertThat(extension("image/webp")).isEqualTo("jpg")
        assertThat(extension(null)).isEqualTo("jpg")
        assertThat(extension("text/plain")).isEqualTo("jpg")
    }

    @Test
    fun `the stored mime is normalised to png or jpeg`() {
        assertThat(mimeType("image/png")).isEqualTo("image/png")
        assertThat(mimeType("image/png; charset=binary")).isEqualTo("image/png")
        assertThat(mimeType("image/jpeg")).isEqualTo("image/jpeg")
        assertThat(mimeType("application/octet-stream")).isEqualTo("image/jpeg")
        assertThat(mimeType(null)).isEqualTo("image/jpeg")
    }
}
