package one.tunkshif.swirlmusic.data.auth

import com.google.common.truth.Truth.assertThat
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Test

/**
 * Round trip on the JVM: encode the URL, turn the matrix into pixels, decode it again.
 * If this passes, the code on screen is scannable.
 */
class QrCodeEncoderTest {

    private val url = "https://music.163.com/login?codekey=d0415c66-3dfc-4595-885c-c047f6cdc4d9"

    @Test
    fun `the encoded matrix decodes back to the same url`() {
        val matrix = QrCodeEncoder.encode(url, size = 256)

        val pixels = IntArray(matrix.width * matrix.height)
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                pixels[y * matrix.width + x] = if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
        }

        val binary = BinaryBitmap(
            HybridBinarizer(RGBLuminanceSource(matrix.width, matrix.height, pixels)),
        )
        val decoded = QRCodeReader().decode(binary).text

        assertThat(decoded).isEqualTo(url)
    }

    @Test
    fun `a blank url is rejected`() {
        val failure = runCatching { QrCodeEncoder.encode(" ") }.exceptionOrNull()

        assertThat(failure).isInstanceOf(IllegalArgumentException::class.java)
    }
}
