package one.tunkshif.swirlmusic.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.ExperimentalTextApi
import one.tunkshif.swirlmusic.R

/**
 * Montserrat, bundled as one variable font at four weights. Bundled rather than downloaded from
 * Google Fonts, so screenshots and a device without Play services render the same face.
 *
 * SIL Open Font License: `licenses/Montserrat-OFL.txt`.
 */
@OptIn(ExperimentalTextApi::class)
val SwirlFontFamily = FontFamily(
    Font(R.font.montserrat, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.montserrat, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.montserrat, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.montserrat, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.montserrat, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
)

/**
 * The house type scale.
 *
 * Three rules carry the look: display sizes are large and tight, tracking is negative above body
 * size, and line height is short so a two-line title reads as one block.
 */
private fun tight(
    size: Int,
    weight: FontWeight,
    lineHeight: Float = 1.05f,
    tracking: Float = -0.02f,
): TextStyle = TextStyle(
    fontFamily = SwirlFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * lineHeight).sp,
    letterSpacing = tracking.em,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

val Typography = Typography(
    displayLarge = tight(56, FontWeight.Bold, lineHeight = 0.95f),
    displayMedium = tight(44, FontWeight.Bold, lineHeight = 0.95f),
    displaySmall = tight(36, FontWeight.SemiBold),
    headlineLarge = tight(32, FontWeight.Bold, lineHeight = 1.1f, tracking = -0.015f),
    headlineMedium = tight(28, FontWeight.SemiBold, lineHeight = 1.1f, tracking = -0.015f),
    headlineSmall = tight(24, FontWeight.SemiBold, lineHeight = 1.15f, tracking = -0.01f),
    titleLarge = tight(22, FontWeight.Bold, lineHeight = 1.15f, tracking = -0.01f),
    titleMedium = tight(16, FontWeight.SemiBold, lineHeight = 1.3f, tracking = -0.005f),
    titleSmall = tight(14, FontWeight.SemiBold, lineHeight = 1.3f, tracking = 0f),
    bodyLarge = tight(16, FontWeight.Normal, lineHeight = 1.45f, tracking = 0f),
    bodyMedium = tight(14, FontWeight.Normal, lineHeight = 1.45f, tracking = 0f),
    bodySmall = tight(12, FontWeight.Normal, lineHeight = 1.4f, tracking = 0.01f),
    labelLarge = tight(14, FontWeight.Medium, lineHeight = 1.3f, tracking = 0.01f),
    labelMedium = tight(12, FontWeight.Medium, lineHeight = 1.3f, tracking = 0.02f),
    labelSmall = tight(11, FontWeight.Medium, lineHeight = 1.3f, tracking = 0.03f),
)
