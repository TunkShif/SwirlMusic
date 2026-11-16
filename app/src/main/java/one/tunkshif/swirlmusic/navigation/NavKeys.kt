package one.tunkshif.swirlmusic.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import one.tunkshif.swirlmusic.data.model.CommentTargetKind

/**
 * Navigation keys. Serializable, because `rememberNavBackStack` saves the stack through
 * process death with kotlinx.serialization.
 */
@Serializable
data object HomeKey : NavKey

@Serializable
data object LibraryKey : NavKey

@Serializable
data object ProfileKey : NavKey

/** Full-screen destination. The bottom bar and mini player hide while it is shown. */
@Serializable
data object PlayingKey : NavKey

@Serializable
data class PlaylistKey(val id: Long) : NavKey

@Serializable
data class AlbumKey(val id: Long) : NavKey

@Serializable
data class ArtistKey(val id: Long) : NavKey

@Serializable
data object SearchKey : NavKey

/** Full-screen comments view (0015); [title] is the track/list name shown under the bar. */
@Serializable
data class CommentsKey(
    val kind: CommentTargetKind,
    val id: Long,
    val title: String,
) : NavKey
