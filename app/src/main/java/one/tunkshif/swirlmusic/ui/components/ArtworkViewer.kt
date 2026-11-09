package one.tunkshif.swirlmusic.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.launch
import one.tunkshif.swirlmusic.R
import one.tunkshif.swirlmusic.data.api.resizedArtwork
import one.tunkshif.swirlmusic.data.media.ArtworkExporter
import one.tunkshif.swirlmusic.ui.theme.Spacing

internal const val ARTWORK_VIEWER_IMAGE_TAG = "artworkViewerImage"

/**
 * Hands routes a MediaStore save without them touching the exporter. Every screen that can
 * open [ArtworkViewer] injects this small view model rather than growing a save method on
 * its own.
 */
@HiltViewModel
class ArtworkSaverViewModel @Inject constructor(
    private val exporter: ArtworkExporter,
) : ViewModel() {

    suspend fun save(url: String, title: String): Boolean =
        exporter.save(url, title).isSuccess
}

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 4f
private const val DOUBLE_TAP_SCALE = 2.5f
private const val DISMISS_FRACTION = 0.2f
private const val DISMISS_FLING_DP_S = 1_000f
private const val SCRIM_ALPHA = 0.92f
private const val VIEWER_ARTWORK_PX = 1600

/**
 * Zoom/pan/dismiss state for [ArtworkViewer]. Plain class so the maths — clamps, the
 * double-tap pivot, the dismiss thresholds — is unit-tested without a composition.
 */
internal class ArtworkViewerState {

    var scale by mutableFloatStateOf(MIN_SCALE)
        private set
    var pan by mutableStateOf(Offset.Zero)
        private set
    /** Pull-down distance while zoomed out; fades the scrim, dismisses past the threshold. */
    var dismissOffset by mutableFloatStateOf(0f)
        private set

    var size = IntSize.Zero
        private set

    fun onSize(size: IntSize) {
        this.size = size
        pan = clamped(pan, scale)
    }

    val zoomed: Boolean get() = scale > MIN_SCALE

    /** Pinch + pan. At 1x a vertical drag becomes the dismiss pull instead of a pan. */
    fun onTransform(pan: Offset, zoom: Float) {
        if (zoom != 1f) {
            scale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
            if (!zoomed) dismissOffset = 0f
        }
        if (zoomed) {
            this.pan = clamped(this.pan + pan, scale)
        } else {
            dismissOffset += pan.y
        }
    }

    /** Double-tap: 1x → [DOUBLE_TAP_SCALE] pivoting on the tap, zoomed → 1x. */
    fun onDoubleTap(tap: Offset) {
        if (zoomed) {
            scale = MIN_SCALE
            pan = Offset.Zero
            return
        }
        scale = DOUBLE_TAP_SCALE
        val center = Offset(size.width / 2f, size.height / 2f)
        pan = clamped((center - tap) * (DOUBLE_TAP_SCALE - 1f), scale)
    }

    fun cancelDismiss() {
        dismissOffset = 0f
    }

    /**
     * Release of the pull-down. Dismisses past 20 % of the height or on a 1,000 dp/s fling
     * ([velocityDpS] is vertical, dp/s, positive downward).
     */
    fun shouldDismiss(velocityDpS: Float): Boolean =
        size.height > 0 &&
            (abs(dismissOffset) > size.height * DISMISS_FRACTION ||
                abs(velocityDpS) > DISMISS_FLING_DP_S)

    fun scrimAlpha(): Float =
        (SCRIM_ALPHA * (1f - abs(dismissOffset) / (size.height * DISMISS_FRACTION * 2f)))
            .coerceIn(0f, SCRIM_ALPHA)

    private fun clamped(offset: Offset, forScale: Float): Offset {
        val maxX = (forScale - 1f) * size.width / 2f
        val maxY = (forScale - 1f) * size.height / 2f
        return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
    }
}

/**
 * Full-screen artwork: pinch-zoom to 4x with bounded pan, double-tap quick zoom, pull down to
 * dismiss at 1x, caption, and a save button that writes the original file to the gallery.
 * [onSave] runs the MediaStore write and returns whether it worked — the toast lives here so
 * the pill is readable on the black scrim.
 */
@Composable
fun ArtworkViewer(
    url: String,
    title: String,
    onDismiss: () -> Unit,
    onSave: suspend () -> Boolean,
    modifier: Modifier = Modifier,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        ArtworkViewerContent(
            url = url,
            title = title,
            onDismiss = onDismiss,
            onSave = onSave,
            modifier = modifier,
        )
    }
}

/** The viewer's body, split out so goldens and tests can render it without a window. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ArtworkViewerContent(
    url: String,
    title: String,
    onDismiss: () -> Unit,
    onSave: suspend () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val state = remember { ArtworkViewerState() }
    val toastState = rememberToastPillState()
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = state.scrimAlpha())),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                ViewerImage(
                    url = url,
                    state = state,
                    onDismiss = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .testTag(ARTWORK_VIEWER_IMAGE_TAG),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xl),
            )
            FilledTonalButton(
                onClick = {
                    if (saving) return@FilledTonalButton
                    saving = true
                    scope.launch {
                        val saved = runCatching { onSave() }.getOrDefault(false)
                        toastState.show(
                            R.drawable.ic_download,
                            if (saved) R.string.artwork_saved_toast
                            else R.string.artwork_save_failed_toast,
                        )
                        saving = false
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = Spacing.xl),
            ) {
                if (saving) {
                    LoadingIndicator(modifier = Modifier.padding(end = Spacing.sm))
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_download),
                        contentDescription = null,
                        modifier = Modifier.padding(end = Spacing.sm),
                    )
                }
                Text(stringResource(R.string.artwork_save))
            }
        }
        ToastPillHost(
            state = toastState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.xl),
        )
    }
}

@Composable
private fun ViewerImage(
    url: String,
    state: ArtworkViewerState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .onSizeChanged { state.onSize(it) }
            .graphicsLayer {
                scaleX = state.scale
                scaleY = state.scale
                translationX = state.pan.x
                translationY = state.pan.y + state.dismissOffset
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { tap -> state.onDoubleTap(tap) })
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val tracker = VelocityTracker()
                    var moved = false
                    var travelled = 0f
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        tracker.addPosition(
                            event.changes.first().uptimeMillis,
                            event.calculateCentroid(),
                        )
                        travelled += pan.getDistance()
                        moved = moved || zoom != 1f ||
                            travelled > viewConfiguration.touchSlop
                        if (moved) {
                            if (pressed.size >= 2 || state.zoomed) {
                                state.onTransform(pan, zoom)
                            } else {
                                // 1x + one finger: a vertical pull is the dismiss gesture.
                                state.onTransform(pan = Offset(0f, pan.y), zoom = 1f)
                            }
                            event.changes.forEach { it.consume() }
                        }
                    }
                    if (moved && !state.zoomed) {
                        val velocityDpS = tracker.calculateVelocity().y / density
                        if (state.shouldDismiss(velocityDpS)) onDismiss()
                        else state.cancelDismiss()
                    } else if (!state.zoomed) {
                        state.cancelDismiss()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val override = LocalArtworkPainterResolver.current?.invoke(url)
            ?: LocalArtworkPainter.current
        val fullUrl = resizedArtwork(url, VIEWER_ARTWORK_PX)
        when {
            override != null -> Image(
                painter = override,
                contentDescription = stringResource(R.string.artwork_view),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            else -> AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(fullUrl)
                    .placeholderMemoryCacheKey(url)
                    .build(),
                contentDescription = stringResource(R.string.artwork_view),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
