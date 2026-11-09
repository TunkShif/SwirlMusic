package one.tunkshif.swirlmusic.data.media

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import one.tunkshif.swirlmusic.data.api.TimeSource
import one.tunkshif.swirlmusic.di.IoDispatcher
import okhttp3.OkHttpClient
import okhttp3.Request

/** Raw bytes plus the server's content type; the type picks the file extension. */
data class ArtworkFetch(val bytes: ByteArray, val contentType: String?) {
    override fun equals(other: Any?): Boolean = other is ArtworkFetch &&
        bytes.contentEquals(other.bytes) && contentType == other.contentType

    override fun hashCode(): Int = 31 * bytes.contentHashCode() + contentType.hashCode()
}

/**
 * The fetch seam for [ArtworkExporter]. The production fetcher uses its own plain
 * `OkHttpClient` — the API client carries `SwirlApiInterceptor`, and the X-Api-Token/Cookie
 * must never leave for the NetEase CDN.
 */
fun interface ArtworkFetcher {
    suspend fun fetch(url: String): ArtworkFetch
}

/** Plain client image fetch. No API interceptor, no cookie. */
@Singleton
class OkHttpArtworkFetcher @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ArtworkFetcher {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    override suspend fun fetch(url: String): ArtworkFetch = withContext(ioDispatcher) {
        client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            ArtworkFetch(response.body.bytes(), response.body.contentType()?.toString())
        }
    }
}

/**
 * Writes the original-resolution artwork to `Pictures/SwirlMusic`. The URL's `?param=WxH`
 * (and any other query) is stripped and `http` upgraded to `https`, the same way
 * [one.tunkshif.swirlmusic.ui.components.sizedArtwork] treats CDN URLs. Bytes are stored
 * unchanged; no storage permission is needed from API 29 up.
 */
@Singleton
class ArtworkExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fetcher: ArtworkFetcher,
    private val timeSource: TimeSource,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun save(url: String, title: String): Result<String> = withContext(ioDispatcher) {
        runCatching {
            require(url.isNotBlank()) { "no artwork url" }
            val originalUrl = url.substringBefore('?').replaceFirst("http://", "https://")
            val fetched = fetcher.fetch(originalUrl)

            val folder = "${Environment.DIRECTORY_PICTURES}/$FOLDER"
            val fileName =
                "${sanitizeFileName(title)}-${timeSource.nowMillis()}.${extension(fetched.contentType)}"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType(fetched.contentType))
                put(MediaStore.Images.Media.RELATIVE_PATH, folder)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStore refused the insert")
            try {
                resolver.openOutputStream(uri)?.use { stream ->
                    stream.write(fetched.bytes)
                } ?: error("no output stream for $uri")

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

/** `image/png` keeps its format; everything else (jpeg, missing, odd types) becomes `.jpg`. */
internal fun extension(contentType: String?): String =
    if (contentType?.startsWith("image/png") == true) "png" else "jpg"

/**
 * The MediaStore MIME for the same content type — normalised so parameters or odd headers
 * (`application/octet-stream`) never reach the row.
 */
internal fun mimeType(contentType: String?): String =
    if (contentType?.startsWith("image/png") == true) "image/png" else "image/jpeg"

/** Gallery-safe name: forbidden characters become `_`, capped at 80, "artwork" as fallback. */
internal fun sanitizeFileName(title: String): String {
    val cleaned = title
        .map { if (it in "/\\:*?\"<>|" || it.isISOControl()) '_' else it }
        .joinToString("")
        .trim()
        .take(80)
    return cleaned.ifBlank { "artwork" }
}
