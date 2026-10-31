package one.tunkshif.swirlmusic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import one.tunkshif.swirlmusic.R
import one.tunkshif.swirlmusic.data.session.SessionState
import one.tunkshif.swirlmusic.ui.home.HomeViewModel
import one.tunkshif.swirlmusic.ui.library.LibraryViewModel
import one.tunkshif.swirlmusic.ui.login.LoginRoute
import one.tunkshif.swirlmusic.ui.player.PlayerViewModel
import one.tunkshif.swirlmusic.ui.shell.SwirlApp
import one.tunkshif.swirlmusic.ui.theme.Spacing

/**
 * The root: splash while the stored session is checked, the sign-in flow when there is no session,
 * and the shell when there is one.
 *
 * A plain `when` rather than a navigation destination: signed-out and signed-in are different
 * apps, and there is nothing to go back to across that boundary.
 */
@Composable
fun SwirlRoot(
    modifier: Modifier = Modifier,
    viewModel: AppViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val miniPlayer by playerViewModel.state.collectAsStateWithLifecycle()

    // One root surface: without it the window background stays light and a dark scheme draws
    // pale text on white.
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        SessionBranch(
            state = state,
            miniPlayer = miniPlayer,
            playerViewModel = playerViewModel,
            onSignOut = viewModel::signOut,
        )
    }
}

@Composable
private fun SessionBranch(
    state: SessionState,
    miniPlayer: one.tunkshif.swirlmusic.ui.player.MiniPlayerUiState,
    playerViewModel: PlayerViewModel,
    onSignOut: () -> Unit,
) {
    when (state) {
        SessionState.Unknown -> SplashScreen()
        SessionState.SignedOut -> LoginRoute()
        is SessionState.SignedIn -> {
            // Tab ViewModels live at shell level, not inside a NavEntry: a tab switch clears
            // the back stack, and entry-scoped stores would be dropped with it.
            val homeViewModel: HomeViewModel = hiltViewModel()
            val libraryViewModel: LibraryViewModel = hiltViewModel()
            SwirlApp(
                profile = state.profile,
                onSignOut = onSignOut,
                miniPlayer = miniPlayer,
                onPlayPause = playerViewModel::togglePlayPause,
                onPlayTracks = playerViewModel::play,
                homeViewModel = homeViewModel,
                libraryViewModel = libraryViewModel,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SplashScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ContainedLoadingIndicator()
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}
