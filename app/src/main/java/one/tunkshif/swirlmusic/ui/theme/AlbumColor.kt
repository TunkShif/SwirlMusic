package one.tunkshif.swirlmusic.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.materialkolor.dynamiccolor.DynamicScheme
import com.materialkolor.hct.Hct
import com.materialkolor.quantize.QuantizerCelebi
import com.materialkolor.scheme.SchemeContent
import com.materialkolor.score.Score

/**
 * The colour seed hidden in a cover's pixels. Null for no pixels; for a greyscale cover the
 * top quantizer colour wins over Score's chroma-biased default, so the seed stays grey.
 */
internal fun seedFromPixels(pixels: IntArray): Int? {
    if (pixels.isEmpty()) return null
    val quantized = QuantizerCelebi.quantize(pixels, 128)
    return Score.score(quantized, desired = 1, fallbackColorArgb = null).firstOrNull()
        ?: quantized.maxByOrNull { it.value }?.key
}

/** The Material scheme a seed colour implies, mapped role by role. */
internal fun albumColorScheme(seed: Int, isDark: Boolean): ColorScheme =
    dynamicSchemeToColorScheme(SchemeContent(Hct.fromInt(seed), isDark, 0.0), isDark)

internal fun dynamicSchemeToColorScheme(scheme: DynamicScheme, isDark: Boolean): ColorScheme =
    if (isDark) darkScheme(scheme) else lightScheme(scheme)

private fun lightScheme(s: DynamicScheme) = lightColorScheme(
    primary = Color(s.primary),
    onPrimary = Color(s.onPrimary),
    primaryContainer = Color(s.primaryContainer),
    onPrimaryContainer = Color(s.onPrimaryContainer),
    inversePrimary = Color(s.inversePrimary),
    secondary = Color(s.secondary),
    onSecondary = Color(s.onSecondary),
    secondaryContainer = Color(s.secondaryContainer),
    onSecondaryContainer = Color(s.onSecondaryContainer),
    tertiary = Color(s.tertiary),
    onTertiary = Color(s.onTertiary),
    tertiaryContainer = Color(s.tertiaryContainer),
    onTertiaryContainer = Color(s.onTertiaryContainer),
    background = Color(s.background),
    onBackground = Color(s.onBackground),
    surface = Color(s.surface),
    onSurface = Color(s.onSurface),
    surfaceVariant = Color(s.surfaceVariant),
    onSurfaceVariant = Color(s.onSurfaceVariant),
    surfaceTint = Color(s.surfaceTint),
    inverseSurface = Color(s.inverseSurface),
    inverseOnSurface = Color(s.inverseOnSurface),
    error = Color(s.error),
    onError = Color(s.onError),
    errorContainer = Color(s.errorContainer),
    onErrorContainer = Color(s.onErrorContainer),
    outline = Color(s.outline),
    outlineVariant = Color(s.outlineVariant),
    scrim = Color(s.scrim),
    surfaceBright = Color(s.surfaceBright),
    surfaceDim = Color(s.surfaceDim),
    surfaceContainer = Color(s.surfaceContainer),
    surfaceContainerHigh = Color(s.surfaceContainerHigh),
    surfaceContainerHighest = Color(s.surfaceContainerHighest),
    surfaceContainerLow = Color(s.surfaceContainerLow),
    surfaceContainerLowest = Color(s.surfaceContainerLowest),
    primaryFixed = Color(s.primaryFixed),
    primaryFixedDim = Color(s.primaryFixedDim),
    onPrimaryFixed = Color(s.onPrimaryFixed),
    onPrimaryFixedVariant = Color(s.onPrimaryFixedVariant),
    secondaryFixed = Color(s.secondaryFixed),
    secondaryFixedDim = Color(s.secondaryFixedDim),
    onSecondaryFixed = Color(s.onSecondaryFixed),
    onSecondaryFixedVariant = Color(s.onSecondaryFixedVariant),
    tertiaryFixed = Color(s.tertiaryFixed),
    tertiaryFixedDim = Color(s.tertiaryFixedDim),
    onTertiaryFixed = Color(s.onTertiaryFixed),
    onTertiaryFixedVariant = Color(s.onTertiaryFixedVariant),
)

private fun darkScheme(s: DynamicScheme) = darkColorScheme(
    primary = Color(s.primary),
    onPrimary = Color(s.onPrimary),
    primaryContainer = Color(s.primaryContainer),
    onPrimaryContainer = Color(s.onPrimaryContainer),
    inversePrimary = Color(s.inversePrimary),
    secondary = Color(s.secondary),
    onSecondary = Color(s.onSecondary),
    secondaryContainer = Color(s.secondaryContainer),
    onSecondaryContainer = Color(s.onSecondaryContainer),
    tertiary = Color(s.tertiary),
    onTertiary = Color(s.onTertiary),
    tertiaryContainer = Color(s.tertiaryContainer),
    onTertiaryContainer = Color(s.onTertiaryContainer),
    background = Color(s.background),
    onBackground = Color(s.onBackground),
    surface = Color(s.surface),
    onSurface = Color(s.onSurface),
    surfaceVariant = Color(s.surfaceVariant),
    onSurfaceVariant = Color(s.onSurfaceVariant),
    surfaceTint = Color(s.surfaceTint),
    inverseSurface = Color(s.inverseSurface),
    inverseOnSurface = Color(s.inverseOnSurface),
    error = Color(s.error),
    onError = Color(s.onError),
    errorContainer = Color(s.errorContainer),
    onErrorContainer = Color(s.onErrorContainer),
    outline = Color(s.outline),
    outlineVariant = Color(s.outlineVariant),
    scrim = Color(s.scrim),
    surfaceBright = Color(s.surfaceBright),
    surfaceDim = Color(s.surfaceDim),
    surfaceContainer = Color(s.surfaceContainer),
    surfaceContainerHigh = Color(s.surfaceContainerHigh),
    surfaceContainerHighest = Color(s.surfaceContainerHighest),
    surfaceContainerLow = Color(s.surfaceContainerLow),
    surfaceContainerLowest = Color(s.surfaceContainerLowest),
    primaryFixed = Color(s.primaryFixed),
    primaryFixedDim = Color(s.primaryFixedDim),
    onPrimaryFixed = Color(s.onPrimaryFixed),
    onPrimaryFixedVariant = Color(s.onPrimaryFixedVariant),
    secondaryFixed = Color(s.secondaryFixed),
    secondaryFixedDim = Color(s.secondaryFixedDim),
    onSecondaryFixed = Color(s.onSecondaryFixed),
    onSecondaryFixedVariant = Color(s.onSecondaryFixedVariant),
    tertiaryFixed = Color(s.tertiaryFixed),
    tertiaryFixedDim = Color(s.tertiaryFixedDim),
    onTertiaryFixed = Color(s.onTertiaryFixed),
    onTertiaryFixedVariant = Color(s.onTertiaryFixedVariant),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun animateColorScheme(target: ColorScheme): ColorScheme {
    val spec = MaterialTheme.motionScheme.slowEffectsSpec<Color>()
    @Composable
    fun anim(color: Color): State<Color> = animateColorAsState(color, spec, label = "albumColor")
    return target.copy(
        primary = anim(target.primary).value,
        onPrimary = anim(target.onPrimary).value,
        primaryContainer = anim(target.primaryContainer).value,
        onPrimaryContainer = anim(target.onPrimaryContainer).value,
        inversePrimary = anim(target.inversePrimary).value,
        secondary = anim(target.secondary).value,
        onSecondary = anim(target.onSecondary).value,
        secondaryContainer = anim(target.secondaryContainer).value,
        onSecondaryContainer = anim(target.onSecondaryContainer).value,
        tertiary = anim(target.tertiary).value,
        onTertiary = anim(target.onTertiary).value,
        tertiaryContainer = anim(target.tertiaryContainer).value,
        onTertiaryContainer = anim(target.onTertiaryContainer).value,
        background = anim(target.background).value,
        onBackground = anim(target.onBackground).value,
        surface = anim(target.surface).value,
        onSurface = anim(target.onSurface).value,
        surfaceVariant = anim(target.surfaceVariant).value,
        onSurfaceVariant = anim(target.onSurfaceVariant).value,
        surfaceTint = anim(target.surfaceTint).value,
        inverseSurface = anim(target.inverseSurface).value,
        inverseOnSurface = anim(target.inverseOnSurface).value,
        error = anim(target.error).value,
        onError = anim(target.onError).value,
        errorContainer = anim(target.errorContainer).value,
        onErrorContainer = anim(target.onErrorContainer).value,
        outline = anim(target.outline).value,
        outlineVariant = anim(target.outlineVariant).value,
        scrim = anim(target.scrim).value,
        surfaceBright = anim(target.surfaceBright).value,
        surfaceDim = anim(target.surfaceDim).value,
        surfaceContainer = anim(target.surfaceContainer).value,
        surfaceContainerHigh = anim(target.surfaceContainerHigh).value,
        surfaceContainerHighest = anim(target.surfaceContainerHighest).value,
        surfaceContainerLow = anim(target.surfaceContainerLow).value,
        surfaceContainerLowest = anim(target.surfaceContainerLowest).value,
        primaryFixed = anim(target.primaryFixed).value,
        primaryFixedDim = anim(target.primaryFixedDim).value,
        onPrimaryFixed = anim(target.onPrimaryFixed).value,
        onPrimaryFixedVariant = anim(target.onPrimaryFixedVariant).value,
        secondaryFixed = anim(target.secondaryFixed).value,
        secondaryFixedDim = anim(target.secondaryFixedDim).value,
        onSecondaryFixed = anim(target.onSecondaryFixed).value,
        onSecondaryFixedVariant = anim(target.onSecondaryFixedVariant).value,
        tertiaryFixed = anim(target.tertiaryFixed).value,
        tertiaryFixedDim = anim(target.tertiaryFixedDim).value,
        onTertiaryFixed = anim(target.onTertiaryFixed).value,
        onTertiaryFixedVariant = anim(target.onTertiaryFixedVariant).value,
    )
}

/**
 * Re-themes [content] with the scheme derived from the current track's artwork. A null seed
 * passes the surrounding scheme through untouched; changes cross-fade on the slow spec.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AlbumColorTheme(seed: Int?, content: @Composable () -> Unit) {
    val inherited = MaterialTheme.colorScheme
    val isDark = inherited.background.luminance() < 0.5f
    val target = seed?.let { albumColorScheme(it, isDark) } ?: inherited
    MaterialExpressiveTheme(
        colorScheme = animateColorScheme(target),
        motionScheme = MaterialTheme.motionScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content,
    )
}
