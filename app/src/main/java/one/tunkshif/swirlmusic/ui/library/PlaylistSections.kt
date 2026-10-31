package one.tunkshif.swirlmusic.ui.library

import one.tunkshif.swirlmusic.data.model.PlaylistSummary

data class PlaylistSections(
    val liked: PlaylistSummary?,
    val created: List<PlaylistSummary>,
    val saved: List<PlaylistSummary>,
    val createdCount: Int?,
    val savedCount: Int?,
)

/**
 * Split `/user/playlist` order into pinned-liked, created, saved. Counts are real only when
 * the last page arrived (`complete`), otherwise headers show no number.
 */
internal fun playlistSections(
    items: List<PlaylistSummary>,
    complete: Boolean,
): PlaylistSections {
    val liked = items.firstOrNull { it.isLiked }
    val created = items.filter { !it.subscribed && !it.isLiked }
    val saved = items.filter { it.subscribed }
    return PlaylistSections(
        liked = liked,
        created = created,
        saved = saved,
        createdCount = if (complete) created.size else null,
        savedCount = if (complete) saved.size else null,
    )
}
