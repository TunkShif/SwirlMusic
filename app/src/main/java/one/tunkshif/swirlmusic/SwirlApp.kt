package one.tunkshif.swirlmusic

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SwirlApp : Application(), SingletonImageLoader.Factory {

    /**
     * One loader for the app. The network fetcher is registered explicitly rather than relying on
     * service discovery, so a missing dependency fails at build time, not at runtime.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .crossfade(true)
            .build()
}
