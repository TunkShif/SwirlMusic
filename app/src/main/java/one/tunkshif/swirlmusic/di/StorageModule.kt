package one.tunkshif.swirlmusic.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import one.tunkshif.swirlmusic.data.local.SearchHistory
import one.tunkshif.swirlmusic.data.local.SearchHistoryStore
import one.tunkshif.swirlmusic.data.session.DataStoreSessionStorage
import one.tunkshif.swirlmusic.data.session.KeystoreSessionCipher
import one.tunkshif.swirlmusic.data.session.SessionCipher
import one.tunkshif.swirlmusic.data.session.SessionStorage

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @Singleton
    fun sessionDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create {
        context.preferencesDataStoreFile("session")
    }

    @Provides
    @Singleton
    fun sessionStorage(dataStore: DataStore<Preferences>): SessionStorage =
        DataStoreSessionStorage(dataStore)

    @Provides
    @Singleton
    fun sessionCipher(): SessionCipher = KeystoreSessionCipher()

    @Provides
    @Singleton
    fun searchHistory(store: SearchHistoryStore): SearchHistory = store
}
