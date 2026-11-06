package one.tunkshif.swirlmusic.ui.playing

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlinx.coroutines.delay
import one.tunkshif.swirlmusic.R
import one.tunkshif.swirlmusic.data.local.LyricsExtra
import one.tunkshif.swirlmusic.data.model.LyricLine
import one.tunkshif.swirlmusic.data.model.LyricWord
import one.tunkshif.swirlmusic.data.model.Lyrics
import one.tunkshif.swirlmusic.ui.theme.Spacing

private const val LYRICS_ANCHOR = 0.35f
private const val FOLLOW_RESUME_MS = 3_000L

/**
 * The lyrics view that swaps in for the cover. [position] is the shared displayed position, read
 * only inside derivedStateOf/draw lambdas so the list doesn't recompose per frame.
 */
@Composable
fun LyricsPane(
    state: LyricsUiState,
    position: () -> Long,
    onSeek: (Long) -> Unit,
    onRetry: () -> Unit,
    onCycleExtra: (LyricsExtra) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        LyricsUiState.Loading -> Message(modifier) {
            @OptIn(ExperimentalMaterial3ExpressiveApi::class)
            LoadingIndicator()
        }
        LyricsUiState.Error -> Message(modifier) {
            Text(
                text = stringResource(R.string.playing_lyrics_error),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.playing_lyrics_retry))
            }
        }
        is LyricsUiState.Content -> when (val lyrics = state.lyrics) {
            Lyrics.None -> Message(modifier) {
                Text(
                    text = stringResource(R.string.playing_lyrics_none),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is Lyrics.Instrumental -> Message(modifier) {
                Text(
                    text = stringResource(R.string.playing_lyrics_instrumental),
                    style = MaterialTheme.typography.titleMedium,
                )
                Credits(credits = lyrics.credits)
            }
            is Lyrics.Unsynced -> Column(modifier.fillMaxSize()) {
                Text(
                    text = stringResource(R.string.playing_lyrics_unsynced),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.sm))
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    itemsIndexed(lyrics.lines) { _, line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    item { Credits(credits = lyrics.credits) }
                }
            }
            is Lyrics.Synced -> SyncedLyrics(
                synced = lyrics,
                extra = state.extra,
                extraChoices = state.extraChoices,
                position = position,
                onSeek = onSeek,
                onCycleExtra = onCycleExtra,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun Message(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        content()
    }
}

@Composable
private fun Credits(credits: List<String>) {
    if (credits.isEmpty()) return
    Spacer(Modifier.height(Spacing.xl))
    credits.forEach { credit ->
        Text(
            text = credit,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xs))
    }
}

@Composable
private fun SyncedLyrics(
    synced: Lyrics.Synced,
    extra: LyricsExtra,
    extraChoices: List<LyricsExtra>,
    position: () -> Long,
    onSeek: (Long) -> Unit,
    onCycleExtra: (LyricsExtra) -> Unit,
    modifier: Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val listState = remember(synced) { LazyListState() }
            val activeIndex by remember(synced) {
                derivedStateOf { synced.lineIndexAt(position()) }
            }
            var follow by remember(synced) { mutableStateOf(true) }
            var settled by remember(synced) { mutableStateOf(false) }

            LaunchedEffect(listState) {
                listState.interactionSource.interactions.collect {
                    if (it is DragInteraction.Start) follow = false
                }
            }
            LaunchedEffect(follow, listState.isScrollInProgress) {
                if (!follow && !listState.isScrollInProgress) {
                    delay(FOLLOW_RESUME_MS)
                    follow = true
                }
            }
            LaunchedEffect(activeIndex, follow) {
                if (!follow || activeIndex < 0) return@LaunchedEffect
                if (!settled) {
                    listState.scrollToItem(activeIndex)
                    settled = true
                } else {
                    listState.animateScrollToItem(activeIndex)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.08f to Color.Black,
                                0.92f to Color.Black,
                                1f to Color.Transparent,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
                contentPadding = PaddingValues(
                    top = maxHeight * LYRICS_ANCHOR,
                    bottom = maxHeight * (1f - LYRICS_ANCHOR),
                ),
            ) {
                itemsIndexed(
                    synced.lines,
                    key = { index, _ -> index },
                ) { index, line ->
                    LyricRow(
                        line = line,
                        isActive = index == activeIndex,
                        extra = extra,
                        position = position,
                        onClick = {
                            follow = true
                            onSeek(line.startMs)
                        },
                    )
                }
                item { Credits(credits = synced.credits) }
            }

            if (extraChoices.size > 1) {
                FilledTonalIconButton(
                    onClick = { onCycleExtra(nextExtra(extraChoices, extra)) },
                    modifier = Modifier.align(Alignment.BottomEnd),
                ) {
                    Icon(
                        painter = painterResource(extraIconRes(extra)),
                        contentDescription = stringResource(
                            R.string.playing_lyrics_extra_fmt,
                            stringResource(extraLabelRes(extra)),
                        ),
                    )
                }
            }
        }
    }
}

private fun extraLabelRes(extra: LyricsExtra) = when (extra) {
    LyricsExtra.None -> R.string.playing_lyrics_extra_original
    LyricsExtra.Translation -> R.string.playing_lyrics_extra_translation
    LyricsExtra.Romanization -> R.string.playing_lyrics_extra_romanization
}

internal fun extraIconRes(extra: LyricsExtra) = when (extra) {
    LyricsExtra.None -> R.drawable.ic_lyrics
    LyricsExtra.Translation -> R.drawable.ic_translate
    LyricsExtra.Romanization -> R.drawable.ic_abc
}

@Composable
private fun LyricRow(
    line: LyricLine,
    isActive: Boolean,
    extra: LyricsExtra,
    position: () -> Long,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(if (isActive) 1f else 0.94f, label = "lyricScale")
    val textColor by animateColorAsState(
        if (isActive) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        },
        label = "lyricColor",
    )
    val extraColor by animateColorAsState(
        if (isActive) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        },
        label = "lyricExtraColor",
    )

    val extraText = when (extra) {
        LyricsExtra.Translation -> line.translation
        LyricsExtra.Romanization -> line.romanization
        LyricsExtra.None -> null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .clickable(
                onClickLabel = stringResource(R.string.playing_lyrics_play_from_line),
                role = Role.Button,
            ) { onClick() }
            .semantics { selected = isActive },
    ) {
        if (isActive && line.words.isNotEmpty()) {
            WordFilledText(
                line = line,
                position = position,
                activeColor = textColor,
            )
        } else {
            Text(
                text = line.text,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
            )
        }
        if (extraText != null) {
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                text = extraText,
                style = MaterialTheme.typography.bodyLarge,
                color = extraColor,
            )
        }
    }
}

/**
 * The active word-timed line: text laid out in the inactive colour, then the filled prefix
 * redrawn on top from the text layout. The position is read inside the draw lambda only, so
 * this never recomposes per frame. LTR layout only.
 */
@Composable
private fun WordFilledText(
    line: LyricLine,
    position: () -> Long,
    activeColor: Color,
) {
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val leadingTrim = line.words.firstOrNull()?.text?.takeWhile { it.isWhitespace() }?.length ?: 0

    Text(
        text = line.text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        color = inactiveColor,
        onTextLayout = { layout = it },
        modifier = Modifier.drawWithContent {
            drawContent()
            val textLayout = layout ?: return@drawWithContent
            val fill = wordFillOffset(line.words, position(), leadingTrim)
            if (fill <= 0f) return@drawWithContent

            val path = Path()
            for (lineIndex in 0 until textLayout.lineCount) {
                val lineStart = textLayout.getLineStart(lineIndex)
                val lineEnd = textLayout.getLineEnd(lineIndex)
                if (lineEnd <= floor(fill)) {
                    path.addRect(
                        Rect(
                            textLayout.getLineLeft(lineIndex),
                            textLayout.getLineTop(lineIndex),
                            textLayout.getLineRight(lineIndex),
                            textLayout.getLineBottom(lineIndex),
                        ),
                    )
                } else if (lineStart <= fill) {
                    val c = floor(fill).toInt()
                    val x0 = textLayout.getHorizontalPosition(c, usePrimaryDirection = true)
                    val x1 = if (c + 1 <= lineEnd) {
                        textLayout.getHorizontalPosition(c + 1, usePrimaryDirection = true)
                    } else {
                        textLayout.getLineRight(lineIndex)
                    }
                    path.addRect(
                        Rect(
                            textLayout.getLineLeft(lineIndex),
                            textLayout.getLineTop(lineIndex),
                            x0 + (x1 - x0) * (fill - c),
                            textLayout.getLineBottom(lineIndex),
                        ),
                    )
                }
            }
            clipPath(path) {
                drawText(textLayout, color = activeColor, topLeft = Offset.Zero)
            }
        },
    )
}

/**
 * Fractional character offset the fill reaches. Before the first word: 0; mid-word: a fraction
 * of that word's chars; in the gap between words: the end of the previous word; after the last
 * word ends: [Float.MAX_VALUE] (full line). [leadingTrim] subtracts the first word's leading
 * whitespace, which the displayed line text no longer contains.
 */
internal fun wordFillOffset(
    words: List<LyricWord>,
    positionMs: Long,
    leadingTrim: Int,
): Float {
    if (words.isEmpty()) return 0f
    var offset = -leadingTrim.toFloat()
    for (word in words) {
        if (positionMs < word.startMs) break
        val end = word.startMs + word.durationMs
        offset += if (positionMs >= end) {
            word.text.length.toFloat()
        } else {
            word.text.length *
                (positionMs - word.startMs).toFloat() / word.durationMs.coerceAtLeast(1)
        }
        if (positionMs < end) return if (offset > 0f) offset else 0f
    }
    return if (positionMs >= words.last().startMs + words.last().durationMs) {
        Float.MAX_VALUE
    } else {
        if (offset > 0f) offset else 0f
    }
}

internal fun extraToastRes(extra: LyricsExtra) = when (extra) {
    LyricsExtra.None -> R.string.playing_lyrics_showing_original
    LyricsExtra.Translation -> R.string.playing_lyrics_showing_translation
    LyricsExtra.Romanization -> R.string.playing_lyrics_showing_romanization
}
