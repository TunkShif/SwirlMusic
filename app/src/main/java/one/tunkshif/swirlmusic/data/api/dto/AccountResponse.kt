package one.tunkshif.swirlmusic.data.api.dto

import kotlinx.serialization.Serializable

/**
 * `/login/status` wraps its payload in `data`. `/user/account` does not. Same body shape.
 */
@Serializable
data class LoginStatusResponse(
    val data: AccountStatusBody = AccountStatusBody(),
)

@Serializable
data class UserAccountResponse(
    val code: Int = 0,
    val account: AccountDto? = null,
    val profile: ProfileDto? = null,
)

@Serializable
data class AccountStatusBody(
    val code: Int = 0,
    val account: AccountDto? = null,
    val profile: ProfileDto? = null,
)

@Serializable
data class AccountDto(
    val id: Long = 0,
    val status: Int = 0,
    val vipType: Int = 0,
)

/**
 * Only the fields the app uses. NetEase sends dozens more; the JSON parser ignores them, which
 * is also why a new NetEase field cannot break the app.
 */
@Serializable
data class ProfileDto(
    val userId: Long = 0,
    val nickname: String = "",
    val avatarUrl: String? = null,
    val signature: String? = null,
    val vipType: Int = 0,
)
