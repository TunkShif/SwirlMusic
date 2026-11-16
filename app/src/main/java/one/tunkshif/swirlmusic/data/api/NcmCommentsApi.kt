package one.tunkshif.swirlmusic.data.api

import one.tunkshif.swirlmusic.data.model.CommentPage
import one.tunkshif.swirlmusic.data.model.CommentPageKey
import one.tunkshif.swirlmusic.data.model.CommentSort
import one.tunkshif.swirlmusic.data.model.CommentTarget

/** Read-only comments (decision 0015: hot and newest sorts, no writes). */
interface NcmCommentsApi {

    /** `/comment/new`. [key] null = first page. */
    suspend fun comments(
        target: CommentTarget,
        sort: CommentSort,
        key: CommentPageKey?,
    ): ApiResult<CommentPage>
}
