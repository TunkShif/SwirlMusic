package one.tunkshif.swirlmusic.ui.playing

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import one.tunkshif.swirlmusic.R
import one.tunkshif.swirlmusic.ui.components.EmptyState
import one.tunkshif.swirlmusic.ui.theme.SwirlMusicTheme

/**
 * Full-screen destination. Phase 6 builds the real thing; for now it proves that the chrome hides
 * and that back returns to the tab you came from.
 *
 * The bar takes zero window insets on purpose: the shell's Scaffold already applied them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayingScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.playing_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.playing_back),
                        )
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
    ) { innerPadding ->
        EmptyState(
            icon = Icons.Default.PlayArrow,
            title = stringResource(R.string.playing_empty_title),
            body = stringResource(R.string.playing_empty_body),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PlayingScreenPreview() {
    SwirlMusicTheme(dynamicColor = false) {
        PlayingScreen(onBack = {})
    }
}
