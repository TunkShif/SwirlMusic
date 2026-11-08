package one.tunkshif.swirlmusic.ui.library

import one.tunkshif.swirlmusic.data.api.ApiResult
import one.tunkshif.swirlmusic.data.api.inIdOrder
import one.tunkshif.swirlmusic.data.api.NcmAuthApi
import one.tunkshif.swirlmusic.data.api.NcmLibraryApi
import one.tunkshif.swirlmusic.data.api.dto.AccountDto
import one.tunkshif.swirlmusic.data.api.dto.LogoutResponse
import one.tunkshif.swirlmusic.data.api.dto.ProfileDto
import one.tunkshif.swirlmusic.data.api.dto.QrCheckResponse
import one.tunkshif.swirlmusic.data.api.dto.QrCreateBody
import one.tunkshif.swirlmusic.data.api.dto.QrKeyBody
import one.tunkshif.swirlmusic.data.api.dto.UserAccountResponse
import one.tunkshif.swirlmusic.data.model.AlbumDetail
import one.tunkshif.swirlmusic.data.model.AlbumSummary
import one.tunkshif.swirlmusic.data.model.ArtistDetail
import one.tunkshif.swirlmusic.data.model.ArtistSummary
import one.tunkshif.swirlmusic.data.model.Page
import one.tunkshif.swirlmusic.data.model.PlayedEntry
import one.tunkshif.swirlmusic.data.model.PlaylistContents
import one.tunkshif.swirlmusic.data.model.PlaylistDetail
import one.tunkshif.swirlmusic.data.model.PlaylistSummary
import one.tunkshif.swirlmusic.data.model.SearchResults
import one.tunkshif.swirlmusic.data.model.SearchType
import one.tunkshif.swirlmusic.data.model.Track
import one.tunkshif.swirlmusic.data.session.SessionCache
import one.tunkshif.swirlmusic.data.session.SessionRepository
import one.tunkshif.swirlmusic.data.session.SessionStore
import kotlinx.coroutines.runBlocking

/** Shared fakes for the library screens. One place, so each test says only what it cares about. */
class FakeLibraryApi : NcmLibraryApi {

    val calls = mutableListOf<String>()
    /** Calls that asked for a fresh (timestamped) response, same format as [calls]. */
    val freshCalls = mutableListOf<String>()
    var failWith: ApiResult<Nothing>? = null

    var playlists: List<PlaylistSummary> = List(3) { playlist((it + 1).toLong()) }
    var hasMorePlaylists: Boolean = true
    var albums: List<AlbumSummary> = listOf(album(1))
    var artists: List<ArtistSummary> = listOf(artist(1))
    var recent: List<PlayedEntry> = listOf(PlayedEntry(track(1), playCount = 7))
    var detail: PlaylistDetail = PlaylistDetail(
        id = 1,
        name = "Late night drive",
        artworkUrl = null,
        trackCount = 2,
        playCount = 10,
        creatorName = "tunkshif",
        description = null,
    )
    var detailTracks: List<Track> = listOf(track(1), track(2))
    var albumDetail: AlbumDetail = AlbumDetail(
        id = 1,
        name = "OutRun",
        artistLine = "Kavinsky",
        artworkUrl = null,
        publishYear = 2013,
        tracks = listOf(track(1)),
    )
    var artistDetail: ArtistDetail = ArtistDetail(
        id = 1,
        name = "Kavinsky",
        artworkUrl = null,
        tracks = listOf(track(1)),
    )
    var searchResults: SearchResults = SearchResults(songs = listOf(track(1), track(2)))

    override suspend fun userPlaylists(uid: Long, limit: Int, offset: Int): ApiResult<Page<PlaylistSummary>> {
        calls += "playlists:$offset"
        failWith?.let { return it }
        return ApiResult.Success(Page(playlists, hasMorePlaylists))
    }

    override suspend fun playlistDetail(id: Long): ApiResult<PlaylistContents> {
        calls += "playlistDetail:$id"
        detailGate?.await()
        failWith?.let { return it }
        return ApiResult.Success(
            PlaylistContents(
                detail = detail,
                trackIds = contentsTrackIds.ifEmpty { detailTracks.map { it.id } },
                firstTracks = contentsFirstTracks.ifEmpty { detailTracks },
                trackUpdateTime = contentsTrackUpdateTime,
            ),
        )
    }

    override suspend fun likedIds(uid: Long): ApiResult<List<Long>> = ApiResult.Success(emptyList())

    var contentsTrackIds: List<Long> = emptyList()
    var contentsFirstTracks: List<Track> = emptyList()
    var contentsTrackUpdateTime: Long? = null
    /** When set, `playlistDetail` suspends on it — for warm-load ordering tests. */
    var detailGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    /** If set, `songsByIds` returns this instead of `track(it)` per id — for order/drop tests. */
    var songsOverride: ((List<Long>) -> List<Track>)? = null
    var failSongsWith: ApiResult<Nothing>? = null
    /** When set, `songsByIds` suspends on it — for in-flight race tests. */
    var songGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    override suspend fun songsByIds(ids: List<Long>): ApiResult<List<Track>> {
        calls += "songs:${ids.joinToString(",")}"
        songGate?.await()
        failSongsWith?.let { return it }
        // Same contract as the real API: results come back in requested order, missing dropped.
        return ApiResult.Success(
            (songsOverride?.invoke(ids) ?: ids.map { track(it) }).inIdOrder(ids),
        )
    }

    override suspend fun recentlyPlayed(uid: Long, limit: Int): ApiResult<List<PlayedEntry>> {
        calls += "recent"
        failWith?.let { return it }
        return ApiResult.Success(recent)
    }

    override suspend fun followedAlbums(
        limit: Int,
        offset: Int,
        fresh: Boolean,
    ): ApiResult<Page<AlbumSummary>> {
        calls += "albums:$offset"
        if (fresh) freshCalls += "albums:$offset"
        failWith?.let { return it }
        return ApiResult.Success(Page(albums, hasMore = false))
    }

    override suspend fun albumDetail(id: Long): ApiResult<AlbumDetail> {
        calls += "albumDetail:$id"
        failWith?.let { return it }
        return ApiResult.Success(albumDetail)
    }

    override suspend fun followedArtists(
        limit: Int,
        offset: Int,
        fresh: Boolean,
    ): ApiResult<Page<ArtistSummary>> {
        calls += "artists:$offset"
        if (fresh) freshCalls += "artists:$offset"
        failWith?.let { return it }
        return ApiResult.Success(Page(artists, hasMore = false))
    }

    override suspend fun artistDetail(id: Long): ApiResult<ArtistDetail> {
        calls += "artistDetail:$id"
        failWith?.let { return it }
        return ApiResult.Success(artistDetail)
    }

    override suspend fun search(
        keywords: String,
        type: SearchType,
        limit: Int,
        offset: Int,
    ): ApiResult<SearchResults> {
        calls += "search:$keywords:${type.name}"
        failWith?.let { return it }
        return ApiResult.Success(searchResults)
    }
}

class FakeAuthApi : NcmAuthApi {
    override suspend fun qrKey(): ApiResult<QrKeyBody> = ApiResult.Success(QrKeyBody())
    override suspend fun qrCreate(key: String): ApiResult<QrCreateBody> =
        ApiResult.Success(QrCreateBody())
    override suspend fun qrCheck(key: String): ApiResult<QrCheckResponse> =
        ApiResult.Success(QrCheckResponse())
    override suspend fun userAccount(): ApiResult<UserAccountResponse> = ApiResult.Success(
        UserAccountResponse(
            code = 200,
            account = AccountDto(id = TEST_USER_ID),
            profile = ProfileDto(userId = TEST_USER_ID, nickname = "tunkshif"),
        ),
    )
    override suspend fun logout(): ApiResult<LogoutResponse> = ApiResult.Success(LogoutResponse())
}

/** A signed-in session, so the library knows which user to ask about. */
fun signedInSession(): SessionRepository {
    val repository = SessionRepository(
        FakeAuthApi(),
        SessionStore(
            object : one.tunkshif.swirlmusic.data.session.SessionStorage {
                var value: String? = null
                override suspend fun read(): String? = value
                override suspend fun write(value: String?) {
                    this.value = value
                }
            },
            object : one.tunkshif.swirlmusic.data.session.SessionCipher {
                override fun encrypt(plaintext: String): String = plaintext
                override fun decrypt(ciphertext: String): String = ciphertext
            },
        ),
        SessionCache(),
        one.tunkshif.swirlmusic.data.cache.AccountScopedCache {},
    )
    runBlocking { repository.signIn("MUSIC_U=test") }
    return repository
}

const val TEST_USER_ID = 42L

fun track(id: Long) = Track(
    id = id,
    title = "Track $id",
    artistLine = "Artist $id",
    artworkUrl = null,
    durationMs = 180_000,
)

fun playlist(
    id: Long,
    liked: Boolean = false,
    saved: Boolean = false,
) = PlaylistSummary(
    id = id,
    name = if (liked) "fixture-user喜欢的音乐" else "Playlist $id",
    artworkUrl = null,
    playCount = 1_000L * id,
    trackCount = 10,
    subscribed = saved,
    creatorName = if (saved) "curator-$id" else null,
    isLiked = liked,
)

fun album(id: Long) = AlbumSummary(
    id = id,
    name = "Album $id",
    artistLine = "Artist $id",
    artworkUrl = null,
    trackCount = 12,
)

fun artist(id: Long) = ArtistSummary(
    id = id,
    name = "Artist $id",
    artworkUrl = null,
)
