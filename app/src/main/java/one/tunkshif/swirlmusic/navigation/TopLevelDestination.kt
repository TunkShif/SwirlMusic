package one.tunkshif.swirlmusic.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import one.tunkshif.swirlmusic.R

/**
 * The three destinations in the bottom bar. Order here is the order on screen.
 */
enum class TopLevelDestination(
    val key: NavKey,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Home(HomeKey, R.string.nav_home, Icons.Default.Home),
    Library(LibraryKey, R.string.nav_library, Icons.AutoMirrored.Filled.List),
    Profile(ProfileKey, R.string.nav_profile, Icons.Default.Person),
}
