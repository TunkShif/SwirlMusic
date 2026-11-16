package one.tunkshif.swirlmusic.data.model

enum class CommentTargetKind(val apiType: Int) { Song(0), Playlist(2), Album(3) }

data class CommentTarget(val kind: CommentTargetKind, val id: Long)

enum class CommentSort(val apiSort: Int) { Hot(2), Newest(3) }

/** Opaque continuation. Hot uses pageNo; Newest uses cursor. */
data class CommentPageKey(val pageNo: Int, val cursor: String)

data class CommentQuote(val userName: String, val text: String)

data class Comment(
    val id: Long,
    val userId: Long,
    val userName: String,
    val avatarUrl: String?,
    /** Emoji tokens already mapped (D67). */
    val text: String,
    val timeMillis: Long,
    val likedCount: Long,
    val replyCount: Int,
    /** `ipLocation.location`, null when blank. */
    val location: String?,
    /** First beReplied entry, text emoji-mapped. */
    val replyTo: CommentQuote?,
)

data class CommentPage(
    val comments: List<Comment>,
    val totalCount: Long,
    val next: CommentPageKey?,
)
