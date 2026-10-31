package one.tunkshif.swirlmusic.data.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Environment
import android.provider.MediaStore
import com.google.zxing.common.BitMatrix
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import one.tunkshif.swirlmusic.data.api.TimeSource
import one.tunkshif.swirlmusic.data.auth.QrCodeEncoder
import one.tunkshif.swirlmusic.di.IoDispatcher

/** Turns a QR matrix into pixels. Shared by the on-screen code and the saved file. */
fun BitMatrix.toBitmap(): Bitmap {
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        for (x in 0 until width) {
            pixels[y * width + x] = if (get(x, y)) Color.BLACK else Color.WHITE
        }
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}

/**
 * Writes the sign-in code to `Pictures/SwirlMusic`, so the user can open the NetEase app on the
 * same phone and scan it from the gallery.
 *
 * No storage permission: `MediaStore` with `RELATIVE_PATH` needs none from API 29 up, and this app
 * starts at 36.
 */
@Singleton
class QrImageExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val timeSource: TimeSource,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun save(content: String): Result<String> = withContext(ioDispatcher) {
        runCatching {
            val bitmap = QrCodeEncoder.encode(content).toBitmap()
            val folder = "${Environment.DIRECTORY_PICTURES}/$FOLDER"
            val fileName = "swirl-login-${timeSource.nowMillis()}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, folder)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStore refused the insert")
            try {
                resolver.openOutputStream(uri)?.use { stream ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                        "PNG encoding failed"
                    }
                } ?: error("no output stream for $uri")
                bitmap.recycle()

                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                    null,
                    null,
                )
                "$folder/$fileName"
            } catch (error: Throwable) {
                resolver.delete(uri, null, null)
                throw error
            }
        }
    }

    private companion object {
        const val FOLDER = "SwirlMusic"
    }
}
