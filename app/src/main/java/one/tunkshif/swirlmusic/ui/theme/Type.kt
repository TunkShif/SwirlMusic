package one.tunkshif.swirlmusic.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import one.tunkshif.swirlmusic.R

/**
 * Google Sans Flex, bundled as one variable font with the rounded axis pinned on. Bundled
 * rather than downloaded, so screenshots and a device without Play services render the same
 * face.
 *
 * SIL Open Font License: `licenses/GoogleSansFlex-OFL.txt`.
 */
@OptIn(ExperimentalTextApi::class)
private fun rounded(weight: Int) = FontVariation.Settings(
    FontVariation.weight(weight),
    FontVariation.Setting("ROND", 100f),
)

@OptIn(ExperimentalTextApi::class)
val SwirlFontFamily = FontFamily(
    Font(R.font.google_sans_flex, FontWeight.Normal, variationSettings = rounded(400)),
    Font(R.font.google_sans_flex, FontWeight.Medium, variationSettings = rounded(500)),
    Font(R.font.google_sans_flex, FontWeight.SemiBold, variationSettings = rounded(600)),
    Font(R.font.google_sans_flex, FontWeight.Bold, variationSettings = rounded(700)),
)

/**
 * Montserrat, kept only for hero titles ([HeroTitleStyle]), where it is stretched wide.
 * SIL Open Font License: `licenses/Montserrat-OFL.txt`.
 */
@OptIn(ExperimentalTextApi::class)
val SwirlDisplayFamily = FontFamily(
    Font(R.font.montserrat, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)

/**
 * The house type scale: the Material 3 baseline, set in Google Sans Flex Rounded.
 */
private fun style(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    tracking: Float,
): TextStyle = TextStyle(
    fontFamily = SwirlFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
)

val Typography = Typography(
    displayLarge = style(57, 64, FontWeight.Medium, -0.25f),
    displayMedium = style(45, 52, FontWeight.Medium, 0f),
    displaySmall = style(36, 44, FontWeight.Medium, 0f),
    headlineLarge = style(32, 40, FontWeight.SemiBold, 0f),
    headlineMedium = style(28, 36, FontWeight.SemiBold, 0f),
    headlineSmall = style(24, 32, FontWeight.SemiBold, 0f),
    titleLarge = style(22, 28, FontWeight.Medium, 0f),
    titleMedium = style(16, 24, FontWeight.Medium, 0.15f),
    titleSmall = style(14, 20, FontWeight.Medium, 0.1f),
    bodyLarge = style(16, 24, FontWeight.Normal, 0.5f),
    bodyMedium = style(14, 20, FontWeight.Normal, 0.25f),
    bodySmall = style(12, 16, FontWeight.Normal, 0.4f),
    labelLarge = style(14, 20, FontWeight.Medium, 0.1f),
    labelMedium = style(12, 16, FontWeight.Medium, 0.5f),
    labelSmall = style(11, 16, FontWeight.Medium, 0.5f),
)

/**
 * The one stretched-Montserrat style: hero titles ("Welcome to", the home greeting). Wide
 * and tight so a short phrase reads as a block. Two lines maximum before it turns into a wall.
 */
val HeroTitleStyle: TextStyle
    @OptIn(ExperimentalTextApi::class)
    get() = TextStyle(
        fontFamily = SwirlDisplayFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 42.sp,
        lineHeight = 1.0.em,
        letterSpacing = (-0.02).em,
        textGeometricTransform = TextGeometricTransform(scaleX = 1.35f),
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
