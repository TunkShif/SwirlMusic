package one.tunkshif.swirlmusic.data.media

import android.content.Context
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import one.tunkshif.swirlmusic.data.api.SystemTimeSource
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The export path needs MediaStore, so it runs on a device. Bytes come from a fake fetcher —
 * no network — and the row is removed afterwards.
 */
@RunWith(AndroidJUnit4::class)
class ArtworkExporterInstrumentedTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun exporter(fetch: ArtworkFetch) = ArtworkExporter(
        context = context,
        fetcher = ArtworkFetcher { fetch },
        timeSource = SystemTimeSource,
        ioDispatcher = Dispatchers.IO,
    )

    private val jpegBytes = byteArrayOf(
        0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte(),
    )

    @Test
    fun saves_the_artwork_into_the_pictures_folder() {
        runBlocking {
            val result = exporter(ArtworkFetch(jpegBytes, "image/jpeg"))
                .save("http://p1.music.126.net/cover.jpg?param=300y300", "OutRun")

            val path = result.getOrThrow()
            assertThat(path).startsWith("Pictures/SwirlMusic/OutRun-")
            assertThat(path).endsWith(".jpg")

            val uri = findSavedImage("OutRun-")
            assertThat(uri).isNotNull()
            context.contentResolver.delete(requireNotNull(uri), null, null)
        }
    }

    @Test
    fun a_failed_write_removes_the_mediastore_row() {
        runBlocking {
            val before = countSavedImages("ArtworkFail-")
            val result = ArtworkExporter(
                context = context,
                fetcher = ArtworkFetcher { throw IOException("cdn down") },
                timeSource = SystemTimeSource,
                ioDispatcher = Dispatchers.IO,
            ).save("https://p1.music.126.net/cover.jpg", "ArtworkFail")

            assertThat(result.isFailure).isTrue()
            assertThat(countSavedImages("ArtworkFail-")).isEqualTo(before)
        }
    }

    @Test
    fun a_blank_url_is_rejected_before_fetching() {
        runBlocking {
            var fetched = false
            val result = ArtworkExporter(
                context = context,
                fetcher = ArtworkFetcher { fetched = true; ArtworkFetch(jpegBytes, null) },
                timeSource = SystemTimeSource,
                ioDispatcher = Dispatchers.IO,
            ).save("   ", "ArtworkBlank")

            assertThat(result.isFailure).isTrue()
            assertThat(fetched).isFalse()
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
