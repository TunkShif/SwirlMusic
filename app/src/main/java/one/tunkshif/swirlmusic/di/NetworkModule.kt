package one.tunkshif.swirlmusic.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.json.Json
import one.tunkshif.swirlmusic.BuildConfig
import one.tunkshif.swirlmusic.data.api.ApiConfig
import one.tunkshif.swirlmusic.data.api.NcmAuthApi
import one.tunkshif.swirlmusic.data.api.NcmHomeApi
import one.tunkshif.swirlmusic.data.api.NcmLibraryApi
import one.tunkshif.swirlmusic.data.api.NcmPlaybackApi
import one.tunkshif.swirlmusic.data.api.NcmApi
import one.tunkshif.swirlmusic.data.api.SwirlApiInterceptor
import one.tunkshif.swirlmusic.data.api.SystemTimeSource
import one.tunkshif.swirlmusic.data.api.TimeSource
import one.tunkshif.swirlmusic.data.auth.LoginTuning
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun apiConfig(): ApiConfig = ApiConfig(
        baseUrl = BuildConfig.API_BASE_URL,
        token = BuildConfig.API_TOKEN,
    )

    @Provides
    @Singleton
    fun timeSource(): TimeSource = SystemTimeSource

    @Provides
    @Singleton
    fun okHttpClient(interceptor: SwirlApiInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(interceptor)
            .apply {
                if (BuildConfig.DEBUG) {
                    // BASIC prints method, URL, and status. Never headers: the token and cookie
                    // live there.
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BASIC
                        },
                    )
                }
            }
            .build()

    @Provides
    @Singleton
    fun authApi(api: NcmApi): NcmAuthApi = api

    @Provides
    @Singleton
    fun homeApi(api: NcmApi): NcmHomeApi = api

    @Provides
    @Singleton
    fun playbackApi(api: NcmApi): NcmPlaybackApi = api

    @Provides
    @Singleton
    fun libraryApi(api: NcmApi): NcmLibraryApi = api

    @Provides
    @Singleton
    fun loginTuning(): LoginTuning = LoginTuning()

    @Provides
    @Singleton
    fun ncmApi(
        client: OkHttpClient,
        json: Json,
        config: ApiConfig,
        timeSource: TimeSource,
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ): NcmApi = NcmApi(client, json, config, timeSource, dispatcher)
}
