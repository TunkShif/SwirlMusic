package one.tunkshif.swirlmusic.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
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
fun ProfileScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.profile_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(
                start = Spacing.xl,
                end = Spacing.xl,
                top = Spacing.lg,
            ),
        )
        EmptyState(
            icon = Icons.Default.Person,
            title = stringResource(R.string.profile_empty_title),
            body = stringResource(R.string.profile_empty_body),
            modifier = Modifier.weight(1f),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    SwirlMusicTheme(dynamicColor = false) {
        ProfileScreen()
    }
}
