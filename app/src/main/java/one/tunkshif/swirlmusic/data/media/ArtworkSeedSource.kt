package one.tunkshif.swirlmusic.data.media

import android.content.Context
import android.util.LruCache
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import one.tunkshif.swirlmusic.data.api.resizedArtwork
import one.tunkshif.swirlmusic.di.DefaultDispatcher
import one.tunkshif.swirlmusic.ui.theme.seedFromPixels

/** The colour seed for a piece of artwork, or null when it can't be read. */
interface ArtworkSeedSource {
    suspend fun seed(artworkUrl: String): Int?
}

/**
 * Pulls a 128 px cover through the app's Coil loader and quantizes it. Successes are cached
 * in memory; failures aren't, so the next time the URL is current it retries.
 */
@Singleton
class CoilArtworkSeedSource @Inject constructor(
    @ApplicationContext private val context: Context,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : ArtworkSeedSource {

    private val cache = LruCache<String, Int>(64)

    override suspend fun seed(artworkUrl: String): Int? {
        cache.get(artworkUrl)?.let { return it }
        val request = ImageRequest.Builder(context)
            .data(resizedArtwork(artworkUrl, 128))
            .size(128)
            .allowHardware(false)
            .build()
        val result = SingletonImageLoader.get(context).execute(request)
        if (result !is SuccessResult) return null
        val bitmap = result.image.toBitmap()
        val seed = withContext(dispatcher) {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            seedFromPixels(pixels)
        }
        if (seed != null) cache.put(artworkUrl, seed)
        return seed
    }
}
