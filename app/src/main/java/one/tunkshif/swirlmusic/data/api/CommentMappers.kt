package one.tunkshif.swirlmusic.data.api

import one.tunkshif.swirlmusic.data.api.dto.CommentDto
import one.tunkshif.swirlmusic.data.api.dto.CommentPageDto
import one.tunkshif.swirlmusic.data.model.Comment
import one.tunkshif.swirlmusic.data.model.CommentPage
import one.tunkshif.swirlmusic.data.model.CommentPageKey
import one.tunkshif.swirlmusic.data.model.CommentQuote

/** 40 dp avatar × 3 density buckets: matches what the comment rows draw. */
private const val COMMENT_AVATAR_PX = 120

internal fun CommentPageDto.toCommentPage(pageNo: Int): CommentPage {
    val mapped = comments.map { it.toDomain() }
    return CommentPage(
        comments = mapped,
        totalCount = totalCount,
        next = if (hasMore && mapped.isNotEmpty()) {
            CommentPageKey(pageNo = pageNo + 1, cursor = cursor)
        } else {
            null
        },
    )
}

internal fun CommentDto.toDomain(): Comment = Comment(
    id = commentId,
    userId = user?.userId ?: 0,
    userName = user?.nickname.orEmpty(),
    // NetEase hands out http:// avatars; sizedArtwork upgrades to https and pins the size.
    avatarUrl = user?.avatarUrl?.takeIf { it.isNotBlank() }?.let { sizedArtwork(it, COMMENT_AVATAR_PX) },
    text = CommentEmoji.mapCommentEmoji(content.orEmpty()),
    timeMillis = time,
    likedCount = likedCount,
    replyCount = replyCount,
    location = ipLocation?.location?.takeIf { it.isNotBlank() },
    replyTo = beReplied?.firstOrNull()?.let { quoted ->
        CommentQuote(
            userName = quoted.user?.nickname.orEmpty(),
            text = CommentEmoji.mapCommentEmoji(quoted.content.orEmpty()),
        )
    },
)
