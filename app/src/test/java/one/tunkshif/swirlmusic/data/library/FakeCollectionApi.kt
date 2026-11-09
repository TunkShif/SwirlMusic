package one.tunkshif.swirlmusic.data.library

import kotlinx.coroutines.CompletableDeferred
import one.tunkshif.swirlmusic.data.api.ApiResult
import one.tunkshif.swirlmusic.data.api.LikeWrite
import one.tunkshif.swirlmusic.data.api.NcmArtistApi
import one.tunkshif.swirlmusic.data.api.NcmCollectionApi
import one.tunkshif.swirlmusic.data.api.WriteOutcome
import one.tunkshif.swirlmusic.data.model.ArtistDescription
import one.tunkshif.swirlmusic.data.model.ArtistRelease
import one.tunkshif.swirlmusic.data.model.Page

/** Canned collection reads/writes; [writeGate] suspends a write for in-flight tests. */
class FakeCollectionApi : NcmCollectionApi {

    val calls = mutableListOf<String>()

    var albumSavedResult: ApiResult<Boolean> = ApiResult.Success(false)
    var setAlbumSavedResult: ApiResult<WriteOutcome> = ApiResult.Success(WriteOutcome.Done)
    var artistFollowedResult: ApiResult<Boolean> = ApiResult.Success(false)
    var setArtistFollowedResult: ApiResult<WriteOutcome> = ApiResult.Success(WriteOutcome.Done)
    var setTrackLikedResult: ApiResult<LikeWrite> =
        ApiResult.Success(LikeWrite(WriteOutcome.Done, playlistId = 99))
    var writeGate: CompletableDeferred<Unit>? = null

    override suspend fun albumSaved(id: Long): ApiResult<Boolean> {
        calls += "albumSaved:$id"
        return albumSavedResult
    }

    override suspend fun setAlbumSaved(id: Long, saved: Boolean): ApiResult<WriteOutcome> {
        calls += "setAlbumSaved:$id:${if (saved) 1 else 0}"
        writeGate?.await()
        return setAlbumSavedResult
    }

    override suspend fun artistFollowed(id: Long): ApiResult<Boolean> {
        calls += "artistFollowed:$id"
        return artistFollowedResult
    }

    override suspend fun setArtistFollowed(id: Long, followed: Boolean): ApiResult<WriteOutcome> {
        calls += "setArtistFollowed:$id:${if (followed) 1 else 0}"
        writeGate?.await()
        return setArtistFollowedResult
    }

    override suspend fun setTrackLiked(id: Long, liked: Boolean): ApiResult<LikeWrite> {
        calls += "setTrackLiked:$id:${if (liked) 1 else 0}"
        writeGate?.await()
        return setTrackLikedResult
    }
}

/** Minimal artist endpoints for view-model tests. */
class FakeArtistApi : NcmArtistApi {
    val calls = mutableListOf<String>()
    var answers = mutableMapOf<Long, ApiResult<String?>>()
    var albumPages = mutableMapOf<Int, ApiResult<Page<ArtistRelease>>>()
    var descriptionResult: ApiResult<ArtistDescription>? = null

    override suspend fun artistAvatar(id: Long): ApiResult<String?> {
        calls += "avatar:$id"
        return answers[id] ?: ApiResult.Success(null)
    }

    override suspend fun artistAlbums(
        id: Long,
        limit: Int,
        offset: Int,
    ): ApiResult<Page<ArtistRelease>> {
        calls += "albums:$id:$offset"
        return albumPages[offset]
            ?: ApiResult.Success(Page(emptyList(), hasMore = false))
    }

    override suspend fun artistDescription(id: Long): ApiResult<ArtistDescription> {
        calls += "desc:$id"
        return descriptionResult
            ?: ApiResult.Success(ArtistDescription(brief = null, sections = emptyList()))
    }

    fun setAlbums(pages: List<Pair<Int, List<ArtistRelease>>>) {
        albumPages.clear()
        pages.forEachIndexed { index, (offset, releases) ->
            albumPages[offset] = ApiResult.Success(Page(releases, hasMore = index < pages.size - 1))
        }
    }
}
