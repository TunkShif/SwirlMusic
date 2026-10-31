package one.tunkshif.swirlmusic.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import one.tunkshif.swirlmusic.R
import one.tunkshif.swirlmusic.ui.components.EmptyState
import one.tunkshif.swirlmusic.ui.theme.Spacing
import one.tunkshif.swirlmusic.ui.theme.SwirlMusicTheme

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(
                start = Spacing.xl,
                end = Spacing.xl,
                top = Spacing.lg,
            ),
        )
        EmptyState(
            icon = Icons.Default.Home,
            title = stringResource(R.string.home_empty_title),
            body = stringResource(R.string.home_empty_body),
            modifier = Modifier.weight(1f),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    SwirlMusicTheme(dynamicColor = false) {
        HomeScreen()
    }
}
