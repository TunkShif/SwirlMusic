package one.tunkshif.swirlmusic.data.api

/** The result of a collection write: applied, or refused by NetEase (locked/delisted). */
enum class WriteOutcome { Done, Refused }

/**
 * A `/like` write plus the `playlistId` the response carries — the account's liked-songs
 * playlist, needed to invalidate its cached order.
 */
data class LikeWrite(val outcome: WriteOutcome, val playlistId: Long?)

/**
 * Saved/followed state and the writes that change it (decision 0010-D36/D37). Every call is
 * fresh: the deployment caches 200 answers by URL for two minutes, so a stale read or a
 * cached write would lie about the collection.
 */
interface NcmCollectionApi {

    /** `/album/detail/dynamic` → `isSub`. */
    suspend fun albumSaved(id: Long): ApiResult<Boolean>

    /** `/album/sub`, `t=1` save / `t=0` unsave. */
    suspend fun setAlbumSaved(id: Long, saved: Boolean): ApiResult<WriteOutcome>

    /** `/artist/detail/dynamic` → `followed`. */
    suspend fun artistFollowed(id: Long): ApiResult<Boolean>

    /** `/artist/sub`, `t=1` follow / `t=0` unfollow. */
    suspend fun setArtistFollowed(id: Long, followed: Boolean): ApiResult<WriteOutcome>

    /** `/like`, `like=true` like / `like=false` unlike; idempotent on the server. */
    suspend fun setTrackLiked(id: Long, liked: Boolean): ApiResult<LikeWrite>
}
