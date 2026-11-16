package one.tunkshif.swirlmusic.data.api

import one.tunkshif.swirlmusic.data.api.dto.CommentDto
import one.tunkshif.swirlmusic.data.api.dto.CommentPageDto
import one.tunkshif.swirlmusic.data.model.Comment
import one.tunkshif.swirlmusic.data.model.CommentPage
import one.tunkshif.swirlmusic.data.model.CommentPageKey
import one.tunkshif.swirlmusic.data.model.CommentQuote

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
    avatarUrl = user?.avatarUrl,
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
