package one.tunkshif.swirlmusic.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import one.tunkshif.swirlmusic.ui.theme.Spacing

private const val TOAST_MS = 1_500L
private val ToastIconSize = 18.dp

/** Transient pill state. Each [show] replaces the current toast and restarts its timer. */
@Stable
class ToastPillState {

    var current: ToastPillMessage? by mutableStateOf(null)
        private set

    private var nextId = 0L

    fun show(@DrawableRes icon: Int, @StringRes text: Int) {
        current = ToastPillMessage(id = nextId++, icon = icon, text = text)
    }

    internal fun dismiss(id: Long) {
        if (current?.id == id) current = null
    }
}

data class ToastPillMessage(
    val id: Long,
    @DrawableRes val icon: Int,
    @StringRes val text: Int,
)

@Composable
fun rememberToastPillState(): ToastPillState = remember { ToastPillState() }

/** Overlay host; place it inside the parent Box and align it. TalkBack announces via liveRegion. */
@Composable
fun ToastPillHost(state: ToastPillState, modifier: Modifier = Modifier) {
    // Keep the last message so the exit animation still has content.
    var shown by remember { mutableStateOf<ToastPillMessage?>(null) }
    val message = state.current
    LaunchedEffect(message?.id) {
        if (message != null) {
            shown = message
            delay(TOAST_MS)
            state.dismiss(message.id)
        }
    }

    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut() + scaleOut(targetScale = 0.8f),
        modifier = modifier,
    ) {
        shown?.let { msg ->
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.padding(
                        horizontal = Spacing.lg,
                        vertical = Spacing.sm,
                    ),
                ) {
                    Icon(
                        painter = painterResource(msg.icon),
                        contentDescription = null,
                        modifier = Modifier.size(ToastIconSize),
                    )
                    Text(
                        text = stringResource(msg.text),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}
