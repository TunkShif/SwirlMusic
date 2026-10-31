package one.tunkshif.swirlmusic.data.api

import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import one.tunkshif.swirlmusic.data.api.dto.AccountStatusBody
import one.tunkshif.swirlmusic.data.api.dto.LoginStatusResponse
import one.tunkshif.swirlmusic.data.api.dto.LogoutResponse
import one.tunkshif.swirlmusic.data.api.dto.QrCheckResponse
import one.tunkshif.swirlmusic.data.api.dto.QrCreateBody
import one.tunkshif.swirlmusic.data.api.dto.QrCreateResponse
import one.tunkshif.swirlmusic.data.api.dto.QrKeyBody
import one.tunkshif.swirlmusic.data.api.dto.QrKeyResponse
import one.tunkshif.swirlmusic.data.api.dto.AlbumDetailResponse
import one.tunkshif.swirlmusic.data.api.dto.AlbumSublistResponse
import one.tunkshif.swirlmusic.data.api.dto.ArtistDetailResponse
import one.tunkshif.swirlmusic.data.api.dto.ArtistSublistResponse
import one.tunkshif.swirlmusic.data.api.dto.LikeResponse
import one.tunkshif.swirlmusic.data.api.dto.LikelistResponse
import one.tunkshif.swirlmusic.data.api.dto.PlaylistDetailResponse
import one.tunkshif.swirlmusic.data.api.dto.RecommendResourceResponse
import one.tunkshif.swirlmusic.data.api.dto.SearchResponse
import one.tunkshif.swirlmusic.data.api.dto.SongDetailResponse
import one.tunkshif.swirlmusic.data.api.dto.UserPlaylistResponse
import one.tunkshif.swirlmusic.data.api.dto.UserRecordResponse
import one.tunkshif.swirlmusic.data.api.dto.ScrobbleResponse
import one.tunkshif.swirlmusic.data.api.dto.SongUrlResponse
import one.tunkshif.swirlmusic.data.api.dto.RecommendSongsResponse
import one.tunkshif.swirlmusic.data.api.dto.ToplistResponse
import one.tunkshif.swirlmusic.data.api.dto.UserAccountResponse
import one.tunkshif.swirlmusic.data.model.AlbumDetail
import one.tunkshif.swirlmusic.data.model.AlbumSummary
import one.tunkshif.swirlmusic.data.model.ArtistDetail
import one.tunkshif.swirlmusic.data.model.ArtistSummary
import one.tunkshif.swirlmusic.data.model.ChartSummary
import one.tunkshif.swirlmusic.data.model.Page
import one.tunkshif.swirlmusic.data.model.PlayedEntry
import one.tunkshif.swirlmusic.data.model.PlaylistContents
import one.tunkshif.swirlmusic.data.model.SearchResults
import one.tunkshif.swirlmusic.data.model.SearchType
import one.tunkshif.swirlmusic.data.model.PlayableUrl
import one.tunkshif.swirlmusic.data.model.PlaylistSummary
import one.tunkshif.swirlmusic.data.model.Track
import one.tunkshif.swirlmusic.di.IoDispatcher
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The NetEase endpoints this app calls. One method per endpoint; DTOs stay behind it.
 */
class NcmApi @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val config: ApiConfig,
    private val timeSource: TimeSource,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : NcmAuthApi, NcmHomeApi, NcmPlaybackApi, NcmLibraryApi {

    suspend fun loginStatus(): ApiResult<AccountStatusBody> =
        request("/login/status", LoginStatusResponse.serializer()).map { it.data }

    override suspend fun userAccount(): ApiResult<UserAccountResponse> =
        request("/user/account", UserAccountResponse.serializer())

    override suspend fun qrKey(): ApiResult<QrKeyBody> =
        request("/login/qr/key", QrKeyResponse.serializer()).map { it.data }

    override suspend fun qrCreate(key: String): ApiResult<QrCreateBody> =
        request("/login/qr/create", QrCreateResponse.serializer(), mapOf("key" to key))
            .map { it.data }

    override suspend fun qrCheck(key: String): ApiResult<QrCheckResponse> =
        request("/login/qr/check", QrCheckResponse.serializer(), mapOf("key" to key))

    override suspend fun logout(): ApiResult<LogoutResponse> =
        request("/logout", LogoutResponse.serializer())

    override suspend fun dailySongs(): ApiResult<List<Track>> =
        request("/recommend/songs", RecommendSongsResponse.serializer())
            .map { response -> response.data.dailySongs.map { it.toDomain() } }

    override suspend fun recommendedPlaylists(): ApiResult<List<PlaylistSummary>> =
        request("/recommend/resource", RecommendResourceResponse.serializer())
            .map { response -> response.recommend.map { it.toDomain() } }

    override suspend fun charts(): ApiResult<List<ChartSummary>> =
        request("/toplist/detail", ToplistResponse.serializer())
            .map { response -> response.list.take(CHART_PREVIEW_COUNT).map { it.toDomain() } }

    override suspend fun songUrls(ids: List<Long>, level: String): ApiResult<List<PlayableUrl>> {
        if (ids.isEmpty()) return ApiResult.Success(emptyList())
        return request(
            "/song/url/v1",
            SongUrlResponse.serializer(),
            mapOf("id" to ids.joinToString(","), "level" to level),
        ).map { response -> response.data.map { it.toDomain(timeSource.nowMillis()) } }
    }

    override suspend fun scrobble(trackId: Long, playedSeconds: Int): ApiResult<Unit> =
        request(
            "/scrobble/v1",
            ScrobbleResponse.serializer(),
            mapOf("id" to trackId.toString(), "time" to playedSeconds.toString()),
        ).map { }

    override suspend fun userPlaylists(
        uid: Long,
        limit: Int,
        offset: Int,
    ): ApiResult<Page<PlaylistSummary>> =
        request(
            "/user/playlist",
            UserPlaylistResponse.serializer(),
            mapOf("uid" to uid.toString(), "limit" to limit.toString(), "offset" to offset.toString()),
        ).map { Page(it.playlist.map { playlist -> playlist.toDomain() }, it.more) }

    override suspend fun playlistDetail(id: Long): ApiResult<PlaylistContents> =
        request(
            "/playlist/detail",
            PlaylistDetailResponse.serializer(),
            mapOf("id" to id.toString(), "n" to "50", "s" to "0"),
        ).map { response ->
            PlaylistContents(
                detail = response.playlist.toDetail(),
                trackIds = response.playlist.trackIds.orEmpty().map { it.id },
                firstTracks = response.playlist.tracks.orEmpty().map { it.toDomain() },
                trackUpdateTime = response.playlist.trackUpdateTime,
            )
        }

    override suspend fun likedIds(uid: Long): ApiResult<List<Long>> =
        request("/likelist", LikelistResponse.serializer(), mapOf("uid" to uid.toString()))
            .map { it.ids }

    override suspend fun songsByIds(ids: List<Long>): ApiResult<List<Track>> {
        if (ids.isEmpty()) return ApiResult.Success(emptyList())
        return request(
            "/song/detail",
            SongDetailResponse.serializer(),
            mapOf("ids" to ids.joinToString(",")),
        ).map { response ->
            response.songs.map { it.toDomain() }.inIdOrder(ids)
        }
    }

    override suspend fun recentlyPlayed(uid: Long, limit: Int): ApiResult<List<PlayedEntry>> =
        request(
            "/user/record",
            UserRecordResponse.serializer(),
            mapOf("uid" to uid.toString(), "type" to "0"),
        ).map { response -> response.allData.mapNotNull { it.toEntry() }.take(limit) }

    override suspend fun followedAlbums(limit: Int, offset: Int): ApiResult<Page<AlbumSummary>> =
        request(
            "/album/sublist",
            AlbumSublistResponse.serializer(),
            mapOf("limit" to limit.toString(), "offset" to offset.toString()),
        ).map { response ->
            val items = (response.subList + response.sublist + response.data)
                .mapNotNull { it.toSummary() }
            Page(items, response.hasMore)
        }

    override suspend fun albumDetail(id: Long): ApiResult<AlbumDetail> =
        request("/album", AlbumDetailResponse.serializer(), mapOf("id" to id.toString()))
            .map { it.toDomain() }

    override suspend fun followedArtists(limit: Int, offset: Int): ApiResult<Page<ArtistSummary>> =
        request(
            "/artist/sublist",
            ArtistSublistResponse.serializer(),
            mapOf("limit" to limit.toString(), "offset" to offset.toString()),
        ).map { response -> Page(response.data.map { it.toSummary() }, response.hasMore) }

    override suspend fun artistDetail(id: Long): ApiResult<ArtistDetail> =
        request("/artists", ArtistDetailResponse.serializer(), mapOf("id" to id.toString()))
            .map { response ->
                ArtistDetail(
                    id = response.artist.id,
                    name = response.artist.name,
                    artworkUrl = (response.artist.picUrl ?: response.artist.img1v1Url)
                        ?.let { sizedArtwork(it, 400) },
                    tracks = response.hotSongs.map { it.toDomain() },
                )
            }

    override suspend fun search(
        keywords: String,
        type: SearchType,
        limit: Int,
        offset: Int,
    ): ApiResult<SearchResults> =
        request(
            "/search",
            SearchResponse.serializer(),
            mapOf(
                "keywords" to keywords,
                "type" to type.apiValue.toString(),
                "limit" to limit.toString(),
                "offset" to offset.toString(),
            ),
        ).map { response -> response.result.toResults(type, offset) }

    private suspend fun <T> request(
        path: String,
        deserializer: DeserializationStrategy<T>,
        params: Map<String, String> = emptyMap(),
    ): ApiResult<T> = withContext(ioDispatcher) {
        val url = config.urlOf(path).toHttpUrl().newBuilder()
            .apply { params.forEach { (name, value) -> addQueryParameter(name, value) } }
            .build()
        val request = Request.Builder().url(url).get().build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body.string()

                when {
                    response.code == 401 -> ApiResult.Unauthorized(body)
                    !response.isSuccessful -> ApiResult.HttpError(response.code, body)

                    runCatching { json.parseToJsonElement(body) }
                        .getOrNull()
                        ?.isExpiredSession() == true -> ApiResult.Unauthorized(body)

                    else -> runCatching { json.decodeFromString(deserializer, body) }
                        .fold(
                            onSuccess = { ApiResult.Success(it) },
                            onFailure = { ApiResult.NetworkError(it) },
                        )
                }
            }
        } catch (error: IOException) {
            ApiResult.NetworkError(error)
        }
    }
}

/**
 * NetEase reports an expired session as `code: 301` inside a 200 response. `/login/status` nests
 * the same body one level deeper under `data`.
 */
private fun JsonElement.isExpiredSession(): Boolean {
    val top = this as? JsonObject ?: return false
    val code = top["code"].asIntOrNull()
        ?: (top["data"] as? JsonObject)?.get("code").asIntOrNull()
    return code == 301
}

private fun JsonElement?.asIntOrNull(): Int? =
    (this as? JsonPrimitive)?.content?.toIntOrNull()
