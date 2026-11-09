package one.tunkshif.swirlmusic.ui.tracks

import androidx.compose.foundation.clickable
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import one.tunkshif.swirlmusic.R
import one.tunkshif.swirlmusic.ui.components.Artwork
import one.tunkshif.swirlmusic.ui.theme.Spacing

/**
 * Morphing header: artwork, titles, meta and the play button lerp between the expanded
 * header and a 64dp collapsed bar. `appBarState` is read ONLY inside layout/draw lambdas
 * (Layout, graphicsLayer, drawBehind), so scrolling never triggers recomposition.
 *
 * [subtitle] is a slot so callers can render plain text or interactive content (the album's
 * artist links); [meta] is the "N tracks · Released …" line; [actions] renders round tonal
 * buttons after the Play-all pill (the comments slot, 0010-D38). [circularArtwork] pins the
 * artwork to a circle (artist screen); the default lerps a rounded square.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CollapsingDetailHeader(
    title: String,
    artworkUrl: String?,
    appBarState: TopAppBarState,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    modifier: Modifier = Modifier,
    circularArtwork: Boolean = false,
    /** Expanded-header artwork tap (the collapsed thumbnail ignores it). */
    onArtworkClick: (() -> Unit)? = null,
    subtitle: (@Composable () -> Unit)? = null,
    meta: String? = null,
    description: String? = null,
    onDescriptionClick: () -> Unit = {},
    leadingActions: @Composable RowScope.() -> Unit = {},
    /** Shows skeleton bars/pills instead of text while the first fetch is in flight (D50). */
    loading: Boolean = false,
    /** Whether the screen has a collection action at all (albums/artists only). */
    hasCollectionAction: Boolean = false,
    topInset: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val collapsedPlayVisible = remember { derivedStateOf { appBarState.collapsedFraction > 0.5f } }
    val colorSchemeSurface = MaterialTheme.colorScheme.surface
    val colorSchemeSurfaceContainer = MaterialTheme.colorScheme.surfaceContainer

    Layout(
        modifier = modifier
            .fillMaxWidth()
            .testTag("detailHeader")
            .clipToBounds()
            .drawBehind {
                drawRect(
                    lerp(
                        colorSchemeSurface,
                        colorSchemeSurfaceContainer,
                        appBarState.collapsedFraction,
                    ),
                )
            },
        content = {
            // "back"
            IconButton(onClick = onBack, modifier = Modifier.layoutId("back")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                )
            }
            // "art" — one Artwork morphed between the expanded and collapsed slots.
            Artwork(
                url = artworkUrl,
                contentDescription = null,
                modifier = Modifier
                    .layoutId("art")
                    .then(
                        if (onArtworkClick != null) {
                            Modifier.clickable(
                                onClickLabel = stringResource(R.string.artwork_view),
                                role = Role.Button,
                            ) {
                                // Only the expanded header opens the viewer; the collapsed
                                // bar thumbnail is too small to be a target.
                                if (appBarState.collapsedFraction < 0.5f) onArtworkClick()
                            }
                        } else {
                            Modifier
                        },
                    ),
            )
            // "bigText" — skeleton bars while the first fetch is in flight (D50).
            Column(
                modifier = Modifier
                    .layoutId("bigText")
                    .graphicsLayer {
                        alpha = (1f - appBarState.collapsedFraction / 0.4f).coerceIn(0f, 1f)
                    },
            ) {
                Crossfade(
                    targetState = loading,
                    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                ) { isLoading ->
                    if (isLoading) {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.70f)
                                    .height(MaterialTheme.typography.headlineSmall.lineHeight.value.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.45f)
                                    .height(MaterialTheme.typography.headlineSmall.lineHeight.value.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            )
                            Box(
                                modifier = Modifier
                                    .padding(top = Spacing.sm)
                                    .fillMaxWidth(0.55f)
                                    .height(MaterialTheme.typography.bodyMedium.lineHeight.value.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            )
                        }
                    } else {
                        Column {
                            Text(
                                text = title.ifBlank { stringResource(R.string.track_list_title) },
                                style = MaterialTheme.typography.headlineSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            subtitle?.invoke()
                            if (meta != null) {
                                Text(
                                    text = meta,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            // "barTitle"
            Text(
                text = title.ifBlank { stringResource(R.string.track_list_title) },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .layoutId("barTitle")
                    .graphicsLayer {
                        alpha = ((appBarState.collapsedFraction - 0.6f) / 0.4f).coerceIn(0f, 1f)
                    }
                    ,
            )
            // "desc"
            if (!loading && description != null) {
                Column(
                    modifier = Modifier
                        .layoutId("desc")
                        .graphicsLayer {
                            alpha = (1f - appBarState.collapsedFraction / 0.35f).coerceIn(0f, 1f)
                        }
                        .clickable(
                            role = Role.Button,
                            onClickLabel = stringResource(R.string.track_list_show_details),
                        ) { onDescriptionClick() },
                ) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.track_list_more),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Icon(
                            androidx.compose.ui.res.painterResource(R.drawable.ic_chevron_right),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            // "playAll" — collection action first, Play all pinned at the end (0011-D44)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .layoutId("playAll")
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = (1f - appBarState.collapsedFraction / 0.35f).coerceIn(0f, 1f)
                    },
            ) {
                Crossfade(
                    targetState = loading,
                    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                    modifier = Modifier.weight(1f),
                ) { isLoading ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isLoading) {
                            if (hasCollectionAction) {
                                Box(
                                    modifier = Modifier
                                        .width(104.dp)
                                        .height(40.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                )
                            }
                        } else {
                            leadingActions()
                        }
                        Spacer(Modifier.weight(1f).widthIn(min = Spacing.sm))
                    }
                }
                if (loading) {
                    Box(
                        modifier = Modifier
                            .width(104.dp)
                            .height(40.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                } else Button(
                    onClick = onPlayAll,
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text(
                        text = stringResource(R.string.track_list_play_all),
                        modifier = Modifier.padding(start = Spacing.sm),
                    )
                }
            }
            // "playCollapsed" — round 40dp button in the bar
            FilledIconButton(
                onClick = onPlayAll,
                enabled = collapsedPlayVisible.value,
                shape = CircleShape,
                modifier = Modifier
                    .layoutId("playCollapsed")
                    .size(40.dp)
                    .graphicsLayer {
                        val t = ((appBarState.collapsedFraction - 0.6f) / 0.4f).coerceIn(0f, 1f)
                        alpha = t
                        scaleX = 0.8f + 0.2f * t
                        scaleY = 0.8f + 0.2f * t
                    }
                    ,
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.track_list_play_all),
                )
            }
        },
    ) { measurables, constraints ->
        // Layout phase: state reads here never recompose.
        val f = appBarState.collapsedFraction
        val offset = appBarState.heightOffset
        val inset = topInset.roundToPx()
        val barH = 64.dp.roundToPx()
        val collapsedH = inset + barH
        val padH = Spacing.xl.roundToPx()
        val gap = Spacing.md.roundToPx()

        val byId = measurables.associateBy { it.layoutId }
        fun place(id: String, maxW: Int) = byId[id]?.measure(
            constraints.copy(minWidth = 0, minHeight = 0, maxWidth = maxW.coerceAtLeast(0)),
        )

        val artExpSize = 112.dp.roundToPx()
        val artColSize = 40.dp.roundToPx()
        val artExpX = padH
        val artExpY = collapsedH + 8.dp.roundToPx()
        val artColX = padH + 48.dp.roundToPx() + 8.dp.roundToPx()
        val artColY = inset + (barH - artColSize) / 2

        val back = byId["back"]?.measure(constraints.copy(minWidth = 0, minHeight = 0))
        val bigText = place("bigText", constraints.maxWidth - (artExpX + artExpSize + gap) - padH)
        val barTitleX = artColX + artColSize + 12.dp.roundToPx()
        val collapsedPlayX = constraints.maxWidth - padH - artColSize
        val barTitle = place("barTitle", collapsedPlayX - 12.dp.roundToPx() - barTitleX)
        val desc = place("desc", constraints.maxWidth - 2 * padH)
        val playAll = place("playAll", constraints.maxWidth - 2 * padH)
        val playCollapsed = byId["playCollapsed"]?.measure(constraints.copy(minWidth = 0, minHeight = 0))
        val rowH = maxOf(artExpSize, bigText?.height ?: 0)
        val descTop = artExpY + rowH + gap
        val playAllTop = descTop + (desc?.height ?: 0) + if (desc != null) gap else 0
        val expandedHeight = playAllTop + (playAll?.height ?: 0) + gap

        appBarState.heightOffsetLimit = collapsedH - expandedHeight.toFloat()

        val height = (expandedHeight + offset).roundToInt().coerceAtLeast(collapsedH)
        val dims = HeaderDims(
            barHeight = collapsedH.toFloat(),
            artworkExpandedSize = artExpSize.toFloat(),
            artworkExpandedX = artExpX.toFloat(),
            artworkExpandedY = artExpY.toFloat(),
            artworkCollapsedSize = artColSize.toFloat(),
            artworkCollapsedX = artColX.toFloat(),
            artworkCollapsedY = artColY.toFloat(),
            contentExpandedY = descTop.toFloat(),
        )
        val g = headerGeometry(f, dims, expandedHeight.toFloat())
        val artSide = g.artworkSize.roundToInt()
        val art = byId["art"]?.measure(
            androidx.compose.ui.unit.Constraints.fixed(artSide, artSide),
        )

        layout(constraints.maxWidth, height) {
            art?.placeWithLayer(g.artworkX.roundToInt(), g.artworkY.roundToInt()) {
                clip = true
                shape = if (circularArtwork) CircleShape else RoundedCornerShape(g.artworkCorner.dp)
            }

            if (g.bigTitleAlpha > 0f) {
                bigText?.place(
                    artExpX + artExpSize + gap,
                    artExpY + offset.roundToInt(),
                )
            }
            if (!loading) {
                barTitle?.takeIf { f > 0.3f }?.place(
                    barTitleX,
                    inset + (barH - barTitle.height) / 2,
                )
            }
            if (g.contentAlpha > 0f) {
                desc?.place(padH, descTop + offset.roundToInt())
                playAll?.place(padH, playAllTop + offset.roundToInt())
            }
            if (!loading && g.playButtonEnabled) {
                playCollapsed?.place(
                    collapsedPlayX,
                    inset + (barH - playCollapsed.height) / 2,
                )
            }
            // Back is placed last so it always wins hit-testing in the bar row.
            back?.place(padH, inset + (barH - back.height) / 2)
        }
    }
}
