package one.tunkshif.swirlmusic.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class SongUrlResponse(
    val code: Int = 0,
    val data: List<SongUrlDto> = emptyList(),
)

@Serializable
data class SongUrlDto(
    val id: Long = 0,
    val url: String? = null,
    val br: Int = 0,
    val size: Long = 0,
    val code: Int = 0,
    /** Seconds until the signed URL stops working. */
    val expi: Long = 0,
    val type: String? = null,
    val level: String? = null,
    val fee: Int = 0,
    /**
     * Present when the listener may only hear a preview. Without VIP the URL plays 45 seconds.
     * Shape: `{"start": 0, "end": 45, ...}`.
     */
    val freeTrialInfo: FreeTrialInfoDto? = null,
)

@Serializable
data class FreeTrialInfoDto(
    val start: Int = 0,
    val end: Int = 0,
)

@Serializable
data class ScrobbleResponse(
    val code: Int = 0,
)
