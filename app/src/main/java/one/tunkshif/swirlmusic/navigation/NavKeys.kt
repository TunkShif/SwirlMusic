package one.tunkshif.swirlmusic.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

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
