package one.tunkshif.swirlmusic.data.api

import one.tunkshif.swirlmusic.data.model.AlbumSummary
import one.tunkshif.swirlmusic.data.model.ArtistSummary
import one.tunkshif.swirlmusic.data.model.Page
import one.tunkshif.swirlmusic.data.model.PlayedEntry
import one.tunkshif.swirlmusic.data.model.PlaylistDetail
import one.tunkshif.swirlmusic.data.model.PlaylistSummary
import one.tunkshif.swirlmusic.data.model.SearchResults
import one.tunkshif.swirlmusic.data.model.SearchType
import one.tunkshif.swirlmusic.data.model.Track
import one.tunkshif.swirlmusic.data.api.dto.AlbumDetailResponse
import one.tunkshif.swirlmusic.data.model.AlbumDetail
import one.tunkshif.swirlmusic.data.model.ArtistDetail

/**
 * Library, detail, and search endpoints. `uid` is passed in because it comes from the stored
 * session, not from the API client.
 */
interface NcmLibraryApi {
    suspend fun userPlaylists(uid: Long, limit: Int, offset: Int): ApiResult<Page<PlaylistSummary>>

    suspend fun playlistDetail(id: Long): ApiResult<PlaylistDetail>

    suspend fun playlistTracks(id: Long, limit: Int, offset: Int): ApiResult<List<Track>>

    suspend fun likedIds(uid: Long): ApiResult<List<Long>>

    suspend fun songsByIds(ids: List<Long>): ApiResult<List<Track>>

    suspend fun recentlyPlayed(uid: Long, limit: Int): ApiResult<List<PlayedEntry>>

    suspend fun followedAlbums(limit: Int, offset: Int): ApiResult<Page<AlbumSummary>>

    suspend fun albumDetail(id: Long): ApiResult<AlbumDetail>

    suspend fun followedArtists(limit: Int, offset: Int): ApiResult<Page<ArtistSummary>>

    suspend fun artistDetail(id: Long): ApiResult<ArtistDetail>

    suspend fun search(
        keywords: String,
        type: SearchType,
        limit: Int,
        offset: Int,
    ): ApiResult<SearchResults>
}

/** Not used yet; kept next to the interface so the mappers stay in one file. */
internal fun AlbumDetailResponse.toDomain(): AlbumDetail = AlbumDetail(
    id = album.id,
    name = album.name,
    artistLine = album.artists.orEmpty().joinToString(" / ") { it.name },
    artworkUrl = album.picUrl?.let { sizedArtwork(it, 400) },
    publishYear = album.publishTime.takeIf { it > 0 }?.let { publishYearOf(it) },
    tracks = songs.map { it.toDomain() },
)

internal fun one.tunkshif.swirlmusic.data.api.dto.SearchResultDto.toResults(
    type: SearchType,
    offset: Int,
): SearchResults {
    val pageSize = when (type) {
        SearchType.Songs -> songCount
        SearchType.Albums -> albumCount
        SearchType.Artists -> artistCount
        SearchType.Playlists -> playlistCount
    }
    val loaded = when (type) {
        SearchType.Songs -> songs.size
        SearchType.Albums -> albums.size
        SearchType.Artists -> artists.size
        SearchType.Playlists -> playlists.size
    }
    return SearchResults(
        songs = this.songs.map { song -> song.toDomain() },
        albums = this.albums.map { album -> album.toSummary() },
        artists = this.artists.map { artist -> artist.toSummary() },
        playlists = this.playlists.map { playlist -> playlist.toDomain() },
        hasMore = pageSize > offset + loaded,
    )
}
