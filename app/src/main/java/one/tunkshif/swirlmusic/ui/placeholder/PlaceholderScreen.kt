package one.tunkshif.swirlmusic.ui.placeholder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import one.tunkshif.swirlmusic.BuildConfig
import one.tunkshif.swirlmusic.R
import one.tunkshif.swirlmusic.ui.theme.Spacing
import one.tunkshif.swirlmusic.ui.theme.SwirlMusicTheme

@Composable
fun PlaceholderRoute(
    modifier: Modifier = Modifier,
    viewModel: PlaceholderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PlaceholderScreen(
        state = state,
        onCheckApi = viewModel::checkApi,
        modifier = modifier,
    )
}

@Composable
fun PlaceholderScreen(
    state: ApiCheckState,
    onCheckApi: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.placeholder_headline),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.placeholder_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onCheckApi) {
                Text(stringResource(R.string.placeholder_check_api))
            }
            Text(
                text = state.describe(),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.placeholder_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ApiCheckState.describe(): String = when (this) {
    ApiCheckState.Idle -> stringResource(R.string.placeholder_api_idle)
    ApiCheckState.Loading -> stringResource(R.string.placeholder_api_loading)
    is ApiCheckState.Reachable -> stringResource(R.string.placeholder_api_reachable, code)
    is ApiCheckState.Failed -> stringResource(R.string.placeholder_api_failed, reason)
}

@Preview(showBackground = true)
@Composable
private fun PlaceholderScreenPreview() {
    SwirlMusicTheme(dynamicColor = false) {
        PlaceholderScreen(state = ApiCheckState.Idle, onCheckApi = {})
    }
}
