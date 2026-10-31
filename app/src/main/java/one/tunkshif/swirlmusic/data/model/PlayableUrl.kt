package one.tunkshif.swirlmusic.data.model

/**
 * A signed media URL, fetched from `/song/url/v1`.
 *
 * The URL expires: `expi` is 20 minutes on the deployment. [expiresAtMillis] is when it should be
 * refetched, not when the HTTP response stops being valid to the second.
 */
data class PlayableUrl(
    val trackId: Long,
    val url: String?,
    val expiresAtMillis: Long,
    val isTrial: Boolean,
    val level: String,
    val bitrate: Int,
) {
    val isPlayable: Boolean get() = !url.isNullOrBlank()
}
