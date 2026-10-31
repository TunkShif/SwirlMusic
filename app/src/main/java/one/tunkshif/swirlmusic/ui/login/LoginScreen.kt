package one.tunkshif.swirlmusic.ui.login

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import one.tunkshif.swirlmusic.R
import one.tunkshif.swirlmusic.data.auth.LoginError
import one.tunkshif.swirlmusic.data.auth.QrCodeEncoder
import one.tunkshif.swirlmusic.data.media.toBitmap
import one.tunkshif.swirlmusic.ui.components.ShapeCollage
import one.tunkshif.swirlmusic.ui.theme.HeroTitleStyle
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

/** Phases that share one body layout and one pair of bar actions. */
private enum class PhaseGroup { Welcome, Loading, Qr, Message }

private val LoginPhase.group: PhaseGroup
    get() = when (this) {
        LoginPhase.Idle -> PhaseGroup.Welcome
        LoginPhase.Loading, LoginPhase.Completing -> PhaseGroup.Loading
        LoginPhase.Waiting, LoginPhase.Scanned -> PhaseGroup.Qr
        LoginPhase.Expired, LoginPhase.Failed -> PhaseGroup.Message
    }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
        bottomBar = {
            LoginBottomBar(
                group = state.phase.group,
                qrReady = state.qrUrl != null,
                onStart = onStart,
                onCancel = onCancel,
                onSaveQr = onSaveQr,
            )
        },
    ) { innerPadding ->
        val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
        AnimatedContent(
            targetState = state.phase.group,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            transitionSpec = {
                fadeIn(effectsSpec) +
                    scaleIn(initialScale = 0.92f, animationSpec = spatialSpec) togetherWith
                    fadeOut(effectsSpec) +
                    scaleOut(targetScale = 0.92f, animationSpec = spatialSpec)
            },
            label = "loginBody",
        ) { group ->
            when (group) {
                PhaseGroup.Welcome -> WelcomeBody()
                PhaseGroup.Loading -> LoadingBody()
                PhaseGroup.Qr -> QrBody(
                    qrUrl = state.qrUrl,
                    scanned = state.phase == LoginPhase.Scanned,
                )

                PhaseGroup.Message -> MessageBody(
                    phase = state.phase,
                    error = state.error,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoginBottomBar(
    group: PhaseGroup,
    qrReady: Boolean,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onSaveQr: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .fillMaxWidth()
                .heightIn(min = 88.dp)
                .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                AnimatedContent(targetState = group, label = "loginBarLeading") { target ->
                    when (target) {
                        PhaseGroup.Welcome -> Text(
                            text = stringResource(R.string.welcome_lets_go),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )

                        PhaseGroup.Loading -> Text(
                            text = stringResource(R.string.login_loading),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        PhaseGroup.Qr, PhaseGroup.Message -> BackButton(onCancel)
                    }
                }
            }
            AnimatedContent(targetState = group, label = "loginBarTrailing") { target ->
                when (target) {
                    PhaseGroup.Welcome -> LoginAction(
                        labelRes = R.string.welcome_action,
                        iconRes = R.drawable.ic_qr_code,
                        onClick = onStart,
                    )

                    PhaseGroup.Loading -> Unit

                    PhaseGroup.Qr -> LoginAction(
                        labelRes = R.string.login_save_qr,
                        iconRes = R.drawable.ic_download,
                        onClick = onSaveQr,
                        enabled = qrReady,
                    )

                    PhaseGroup.Message -> LoginAction(
                        labelRes = R.string.login_retry,
                        iconRes = R.drawable.ic_refresh,
                        onClick = onStart,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoginAction(
    labelRes: Int,
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val height = ButtonDefaults.MediumContainerHeight
    Button(
        onClick = onClick,
        modifier = modifier.height(height),
        enabled = enabled,
        shapes = ButtonDefaults.shapes(),
        contentPadding = ButtonDefaults.contentPaddingFor(height),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)),
        )
        Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(height)))
        Text(
            text = stringResource(labelRes),
            style = ButtonDefaults.textStyleFor(height),
        )
    }
}

@Composable
private fun BackButton(onCancel: () -> Unit) {
    FilledTonalIconButton(
        onClick = onCancel,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_arrow_back),
            contentDescription = stringResource(R.string.login_back),
        )
    }
}

@Composable
private fun LoginBody(content: @Composable ColumnScope.() -> Unit) {
    // SpaceBetween over a viewport-min column: content spreads across the screen and still
    // scrolls on small displays. No weight inside the scrolling column.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = Spacing.xl)
                .padding(top = Spacing.lg, bottom = Spacing.xl),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            content()
        }
    }
}

@Composable
private fun WelcomeBody() {
    LoginBody {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.welcome_kicker),
                style = HeroTitleStyle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.welcome_title),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            ShapeCollage()
        }
        Text(
            text = stringResource(R.string.welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingBody() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ContainedLoadingIndicator(modifier = Modifier.size(96.dp))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QrBody(qrUrl: String?, scanned: Boolean) {
    val bitmap = remember(qrUrl) {
        qrUrl?.let { QrCodeEncoder.encode(it).toBitmap() }
    }

    LoginBody {
        Text(
            text = stringResource(R.string.login_qr_heading),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.fillMaxWidth(),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(300.dp)
                        .clip(MaterialShapes.Cookie12Sided.toShape())
                        .background(MaterialTheme.colorScheme.primaryContainer),
                )
                if (bitmap == null) {
                    LoadingIndicator(modifier = Modifier.size(96.dp))
                } else {
                    Box {
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(28.dp),
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = stringResource(R.string.login_qr_description),
                                filterQuality = FilterQuality.None,
                                modifier = Modifier
                                    .size(200.dp)
                                    .padding(Spacing.lg),
                            )
                        }
                        if (scanned) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                                        RoundedCornerShape(28.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Surface(
                                    shape = MaterialShapes.Sunny.toShape(),
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    modifier = Modifier.size(72.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_check),
                                            contentDescription = null,
                                            modifier = Modifier.size(36.dp),
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Surface(
                shape = CircleShape,
                color = if (scanned) {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (scanned) {
                        Icon(
                            painter = painterResource(R.drawable.ic_check),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    } else {
                        LoadingIndicator(modifier = Modifier.size(20.dp))
                    }
                    Text(
                        text = stringResource(
                            if (scanned) R.string.login_scanned_title else R.string.login_waiting_title,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (scanned) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                StepRow(number = 1, textRes = R.string.login_step_save)
                StepRow(number = 2, textRes = R.string.login_step_scan)
                StepRow(number = 3, textRes = R.string.login_step_pick)
            }
        }
    }
}

@Composable
private fun StepRow(number: Int, textRes: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(28.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.login_step_number, number),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Text(
            text = stringResource(textRes),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MessageBody(phase: LoginPhase, error: LoginError?) {
    val expired = phase == LoginPhase.Expired
    val shape: Shape
    val containerColor: Color
    val iconTint: Color
    val iconRes: Int
    if (expired) {
        shape = MaterialShapes.Cookie6Sided.toShape()
        containerColor = MaterialTheme.colorScheme.secondaryContainer
        iconTint = MaterialTheme.colorScheme.onSecondaryContainer
        iconRes = R.drawable.ic_timer
    } else {
        shape = MaterialShapes.Burst.toShape()
        containerColor = MaterialTheme.colorScheme.errorContainer
        iconTint = MaterialTheme.colorScheme.onErrorContainer
        iconRes = when (error.orDefault()) {
            LoginError.Network -> R.drawable.ic_cloud_off
            LoginError.Server -> R.drawable.ic_error
        }
    }
    val title = stringResource(
        if (expired) R.string.login_expired_title else R.string.login_failed_title,
    )
    val body = if (expired) {
        stringResource(R.string.login_expired_body)
    } else {
        error.orDefault().message()
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
    ) {
        Surface(
            shape = shape,
            color = containerColor,
            modifier = Modifier.size(120.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                    tint = iconTint,
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Spacing.xl),
        )
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
