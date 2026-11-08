package one.tunkshif.swirlmusic.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import one.tunkshif.swirlmusic.ui.theme.Spacing

/** The header's collection action while its state is unknown, known, or unreadable (D45). */
enum class CollectionSlotState { Loading, Ready }

private val CollectionPillHeight = 40.dp
private val CollectionSkeletonWidth = 104.dp

/**
 * The collection pill of a detail header: [checked] picks icon and label; the M3 Expressive
 * toggle morphs its shape on check. Same height as the Play-all pill.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CollectionToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    @DrawableRes checkedIcon: Int,
    @DrawableRes uncheckedIcon: Int,
    @StringRes checkedLabel: Int,
    @StringRes uncheckedLabel: Int,
    modifier: Modifier = Modifier,
) {
    FilledTonalToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.height(CollectionPillHeight),
    ) {
        Icon(
            painter = painterResource(if (checked) checkedIcon else uncheckedIcon),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(if (checked) checkedLabel else uncheckedLabel),
            modifier = Modifier.padding(start = Spacing.xs),
        )
    }
}

/**
 * The whole slot: a skeleton stadium while the state read is in flight, the toggle once it
 * lands, nothing if it failed — Play all is anchored at the end, so nothing else moves.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CollectionAction(
    state: CollectionSlotState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val spec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AnimatedContent(
        targetState = state,
        transitionSpec = {
            fadeIn(spec) togetherWith fadeOut(spec)
        },
        modifier = modifier,
        label = "collectionAction",
    ) { slot ->
        when (slot) {
            CollectionSlotState.Loading -> Box(
                modifier = Modifier
                    .width(CollectionSkeletonWidth)
                    .height(CollectionPillHeight)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
            CollectionSlotState.Ready -> content()
        }
    }
}
