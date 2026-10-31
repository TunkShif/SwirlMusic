package one.tunkshif.swirlmusic.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import one.tunkshif.swirlmusic.data.api.NcmPlaybackApi
import one.tunkshif.swirlmusic.data.api.TimeSource
import one.tunkshif.swirlmusic.player.ScrobbleTracker
import one.tunkshif.swirlmusic.player.SongUrlResolver

@Module
@InstallIn(SingletonComponent::class)
object PlaybackModule {

    @Provides
    @Singleton
    fun songUrlResolver(
        api: NcmPlaybackApi,
        timeSource: TimeSource,
    ): SongUrlResolver = SongUrlResolver(api, timeSource)

    @Provides
    @Singleton
    fun scrobbleTracker(): ScrobbleTracker = ScrobbleTracker()
}
