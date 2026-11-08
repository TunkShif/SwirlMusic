package one.tunkshif.swirlmusic.data.library

import kotlinx.coroutines.CompletableDeferred
import one.tunkshif.swirlmusic.data.api.ApiResult
import one.tunkshif.swirlmusic.data.api.NcmArtistApi
import one.tunkshif.swirlmusic.data.api.NcmCollectionApi
import one.tunkshif.swirlmusic.data.api.WriteOutcome

/** Canned collection reads/writes; [writeGate] suspends a write for in-flight tests. */
class FakeCollectionApi : NcmCollectionApi {

    val calls = mutableListOf<String>()

    var albumSavedResult: ApiResult<Boolean> = ApiResult.Success(false)
    var setAlbumSavedResult: ApiResult<WriteOutcome> = ApiResult.Success(WriteOutcome.Done)
    var artistFollowedResult: ApiResult<Boolean> = ApiResult.Success(false)
    var setArtistFollowedResult: ApiResult<WriteOutcome> = ApiResult.Success(WriteOutcome.Done)
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
}

/** Minimal avatar endpoint for view-model tests. */
class FakeArtistApi : NcmArtistApi {
    val calls = mutableListOf<Long>()
    var answers = mutableMapOf<Long, ApiResult<String?>>()

    override suspend fun artistAvatar(id: Long): ApiResult<String?> {
        calls += id
        return answers[id] ?: ApiResult.Success(null)
    }
}
