package one.tunkshif.swirlmusic.data.auth

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Encodes the login URL that NetEase's QR flow returns.
 *
 * Pure JVM: no Android classes, so the round trip is testable without a device. The UI turns the
 * matrix into pixels; the exporter turns it into a PNG.
 */
object QrCodeEncoder {

    const val DEFAULT_SIZE = 512

    /** Quiet zone in modules. Four is the spec minimum and what scanners expect. */
    private const val MARGIN = 4

    fun encode(content: String, size: Int = DEFAULT_SIZE): BitMatrix {
        require(content.isNotBlank()) { "QR content must not be blank" }
        require(size > 0) { "QR size must be positive" }

        val hints = mapOf(
            EncodeHintType.MARGIN to MARGIN,
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        )
        return QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    }
}
