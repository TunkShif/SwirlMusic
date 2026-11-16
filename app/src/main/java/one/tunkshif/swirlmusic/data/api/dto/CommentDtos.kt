package one.tunkshif.swirlmusic.data.api.dto

import kotlinx.serialization.Serializable

/**
 * `/comment/new`: `{code, data{comments, totalCount, hasMore, cursor}}`. A refusal answers
 * HTTP 200 with no `data` (`{"msg":"…","code":400}`).
 */
@Serializable
data class CommentNewResponse(
    val code: Int = 0,
    val data: CommentPageDto? = null,
)

@Serializable
data class CommentPageDto(
    val comments: List<CommentDto> = emptyList(),
    val totalCount: Long = 0,
    val hasMore: Boolean = false,
    /** `normalHot#<offset>` under hot sort, a ms timestamp under newest. */
    val cursor: String = "0",
)

@Serializable
data class CommentDto(
    val commentId: Long = 0,
    val user: CommentUserDto? = null,
    /** Null in theory, never seen empty in samples. */
    val content: String? = null,
    val time: Long = 0,
    val likedCount: Long = 0,
    val replyCount: Int = 0,
    val beReplied: List<BeRepliedDto>? = null,
    val ipLocation: IpLocationDto? = null,
)

@Serializable
data class CommentUserDto(
    val userId: Long = 0,
    val nickname: String = "",
    val avatarUrl: String? = null,
)

@Serializable
data class BeRepliedDto(
    val user: CommentUserDto? = null,
    val content: String? = null,
)

@Serializable
data class IpLocationDto(
    val location: String? = null,
)
