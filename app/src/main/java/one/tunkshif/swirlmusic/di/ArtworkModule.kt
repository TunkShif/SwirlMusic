package one.tunkshif.swirlmusic.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import one.tunkshif.swirlmusic.data.media.ArtworkSeedSource
import one.tunkshif.swirlmusic.data.media.CoilArtworkSeedSource

@Module
@InstallIn(SingletonComponent::class)
abstract class ArtworkModule {

    @Binds
    abstract fun artworkSeedSource(impl: CoilArtworkSeedSource): ArtworkSeedSource
}
