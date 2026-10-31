package one.tunkshif.swirlmusic.ui.tracks

/**
 * Everything the collapsing header needs to place and fade its pieces for collapse
 * fraction [f] (0 = expanded, 1 = collapsed). Pure for tests; the Layout reads
 * TopAppBarState inside the layout phase only.
 */
internal data class HeaderDims(
    /** 64dp bar, in px. */
    val barHeight: Float,
    val artworkExpandedSize: Float,
    val artworkExpandedX: Float,
    val artworkExpandedY: Float,
    val artworkCollapsedSize: Float,
    val artworkCollapsedX: Float,
    val artworkCollapsedY: Float,
    /** Where the meta/description/Play-all block top sits when expanded, in px. */
    val contentExpandedY: Float,
)

internal data class HeaderGeometry(
    val height: Float,
    val artworkSize: Float,
    val artworkX: Float,
    val artworkY: Float,
    val artworkCorner: Float,
    val bigTitleAlpha: Float,
    val barTitleAlpha: Float,
    val contentAlpha: Float,
    val playButtonAlpha: Float,
    val playButtonScale: Float,
    val playButtonEnabled: Boolean,
)

internal fun headerGeometry(
    f: Float,
    dims: HeaderDims,
    expandedHeight: Float,
): HeaderGeometry {
    val c = f.coerceIn(0f, 1f)
    fun lerp(a: Float, b: Float) = a + (b - a) * c
    return HeaderGeometry(
        height = lerp(expandedHeight, dims.barHeight),
        artworkSize = lerp(dims.artworkExpandedSize, dims.artworkCollapsedSize),
        artworkX = lerp(dims.artworkExpandedX, dims.artworkCollapsedX),
        artworkY = lerp(dims.artworkExpandedY, dims.artworkCollapsedY),
        artworkCorner = lerp(16f, 10f),
        bigTitleAlpha = (1f - c / 0.4f).coerceIn(0f, 1f),
        barTitleAlpha = ((c - 0.6f) / 0.4f).coerceIn(0f, 1f),
        contentAlpha = (1f - c / 0.35f).coerceIn(0f, 1f),
        playButtonAlpha = ((c - 0.6f) / 0.4f).coerceIn(0f, 1f),
        playButtonScale = 0.8f + 0.2f * ((c - 0.6f) / 0.4f).coerceIn(0f, 1f),
        playButtonEnabled = c > 0.5f,
    )
}
