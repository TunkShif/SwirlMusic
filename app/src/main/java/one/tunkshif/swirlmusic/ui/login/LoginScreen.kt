package one.tunkshif.swirlmusic.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import one.tunkshif.swirlmusic.R
import one.tunkshif.swirlmusic.data.auth.LoginError
import one.tunkshif.swirlmusic.data.auth.QrCodeEncoder
import one.tunkshif.swirlmusic.data.media.toBitmap
import one.tunkshif.swirlmusic.ui.theme.Spacing
import one.tunkshif.swirlmusic.ui.theme.SwirlMusicTheme

@Composable
fun LoginRoute(
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.onEnter() }
    val snackbar = remember { SnackbarHostState() }
    val savedMessage = stringResource(R.string.login_saved_fmt)
    val failedMessage = stringResource(R.string.login_save_failed)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            snackbar.showSnackbar(
                when (event) {
                    is LoginUiEvent.QrSaved -> savedMessage.format(event.path)
                    LoginUiEvent.QrSaveFailed -> failedMessage
                },
            )
        }
    }

    LoginScreen(
        state = state,
        snackbarHostState = snackbar,
        onStart = viewModel::start,
        onCancel = viewModel::cancel,
        onSaveQr = viewModel::saveQrToGallery,
        modifier = modifier,
    )
}

@Composable
fun LoginScreen(
    state: LoginUiState,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onSaveQr: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Spacing.xl),
            contentAlignment = Alignment.Center,
        ) {
            when (state.phase) {
                LoginPhase.Idle -> WelcomeContent(onStart = onStart)
                LoginPhase.Loading, LoginPhase.Completing -> LoadingContent()
                LoginPhase.Waiting, LoginPhase.Scanned -> QrContent(
                    qrUrl = state.qrUrl,
                    scanned = state.phase == LoginPhase.Scanned,
                    onSaveQr = onSaveQr,
                    onCancel = onCancel,
                )

                LoginPhase.Expired -> MessageContent(
                    title = stringResource(R.string.login_expired_title),
                    body = stringResource(R.string.login_expired_body),
                    actionLabel = stringResource(R.string.login_retry),
                    onAction = onStart,
                    secondaryLabel = stringResource(R.string.login_back),
                    onSecondary = onCancel,
                )

                LoginPhase.Failed -> MessageContent(
                    title = stringResource(R.string.login_failed_title),
                    body = state.error.orDefault().message(),
                    actionLabel = stringResource(R.string.login_retry),
                    onAction = onStart,
                    secondaryLabel = stringResource(R.string.login_back),
                    onSecondary = onCancel,
                )
            }
        }
    }
}

@Composable
private fun WelcomeContent(onStart: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(112.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Text(
            text = stringResource(R.string.welcome_title),
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onStart,
            shape = MaterialTheme.shapes.extraLarge,
            contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.md),
        ) {
            Text(stringResource(R.string.welcome_action))
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.login_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun QrContent(
    qrUrl: String?,
    scanned: Boolean,
    onSaveQr: () -> Unit,
    onCancel: () -> Unit,
) {
    val bitmap = remember(qrUrl) {
        qrUrl?.let { QrCodeEncoder.encode(it).toBitmap() }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(
            text = stringResource(
                if (scanned) R.string.login_scanned_title else R.string.login_waiting_title,
            ),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )

        if (bitmap == null) {
            CircularProgressIndicator()
        } else {
            Box {
                Surface(
                    color = Color.White,
                    shape = MaterialTheme.shapes.large,
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.login_qr_description),
                        filterQuality = FilterQuality.None,
                        modifier = Modifier
                            .size(240.dp)
                            .padding(Spacing.md),
                    )
                }
                if (scanned) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                MaterialTheme.shapes.large,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        Text(
            text = stringResource(R.string.login_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.login_back))
            }
            Button(onClick = onSaveQr, enabled = qrUrl != null) {
                Text(stringResource(R.string.login_save_qr))
            }
        }
    }
}

@Composable
private fun MessageContent(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onAction) { Text(actionLabel) }
        TextButton(onClick = onSecondary) { Text(secondaryLabel) }
    }
}

@Composable
private fun LoginError?.message(): String = when (this) {
    LoginError.Network -> stringResource(R.string.login_failed_network)
    LoginError.Server -> stringResource(R.string.login_failed_server)
    null -> stringResource(R.string.login_failed_server)
}

private fun LoginError?.orDefault(): LoginError = this ?: LoginError.Server

@Preview(showBackground = true)
@Composable
private fun WelcomePreview() {
    SwirlMusicTheme(dynamicColor = false) {
        LoginScreen(
            state = LoginUiState(),
            onStart = {},
            onCancel = {},
            onSaveQr = {},
        )
    }
}
