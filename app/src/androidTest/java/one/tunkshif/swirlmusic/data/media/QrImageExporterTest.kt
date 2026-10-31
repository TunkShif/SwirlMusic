package one.tunkshif.swirlmusic.data.media

import android.content.Context
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import one.tunkshif.swirlmusic.data.api.SystemTimeSource
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The export path needs MediaStore, so it runs on a device. It writes a real PNG, checks that
 * MediaStore knows about it, then removes it.
 */
@RunWith(AndroidJUnit4::class)
class QrImageExporterTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val exporter = QrImageExporter(context, SystemTimeSource, Dispatchers.IO)

    private val content = "https://music.163.com/login?codekey=device-test"

    @Test
    fun saves_the_code_into_the_pictures_folder() {
        runBlocking {
            val result = exporter.save(content)

            val path = result.getOrThrow()
            assertThat(path).startsWith("Pictures/SwirlMusic/swirl-login-")
            assertThat(path).endsWith(".png")

            val uri = findSavedImage(path.substringAfterLast('/'))
            assertThat(uri).isNotNull()
            context.contentResolver.delete(requireNotNull(uri), null, null)
        }
    }

    @Test
    fun a_missing_content_is_rejected_before_writing() {
        runBlocking {
            // The device may hold codes from earlier runs, so compare counts, not existence.
            val before = countSavedImages("swirl-login-")

            val result = exporter.save("   ")

            assertThat(result.isFailure).isTrue()
            assertThat(countSavedImages("swirl-login-")).isEqualTo(before)
        }
    }

    private fun countSavedImages(namePart: String): Int =
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?",
            arrayOf("$namePart%"),
            null,
        )?.use { it.count } ?: 0

    private fun findSavedImage(namePart: String): android.net.Uri? {
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)
        val selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
        return context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            arrayOf("$namePart%"),
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI.buildUpon().appendPath(id.toString()).build()
        }
    }
}
