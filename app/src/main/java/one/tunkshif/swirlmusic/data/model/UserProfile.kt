package one.tunkshif.swirlmusic.data.model

data class UserProfile(
    val userId: Long,
    val nickname: String,
    val avatarUrl: String?,
    val vipType: Int,
) {
    val isVip: Boolean get() = vipType > 0

    /** Fallback for the avatar slot when there is no image or it has not loaded yet. */
    val initials: String
        get() = nickname.trim().take(1).uppercase().ifBlank { "?" }
}
