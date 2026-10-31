package one.tunkshif.swirlmusic.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import one.tunkshif.swirlmusic.data.cache.AccountScopedCache
import one.tunkshif.swirlmusic.data.cache.RoomAccountScopedCache
import one.tunkshif.swirlmusic.data.cache.SwirlDatabase
import one.tunkshif.swirlmusic.data.cache.TrackListDao

@Module
@InstallIn(SingletonComponent::class)
object CacheModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): SwirlDatabase =
        Room.databaseBuilder(context, SwirlDatabase::class.java, "swirl.db").build()

    @Provides
    fun trackListDao(db: SwirlDatabase): TrackListDao = db.trackListDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CacheBindings {
    @Binds
    abstract fun accountScopedCache(impl: RoomAccountScopedCache): AccountScopedCache
}
