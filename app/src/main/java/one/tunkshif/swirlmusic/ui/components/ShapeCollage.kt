package one.tunkshif.swirlmusic.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import one.tunkshif.swirlmusic.R
import one.tunkshif.swirlmusic.ui.theme.SwirlMusicTheme

/**
 * A static scatter of MaterialShapes containers holding icons, the way the reference decorates
 * its setup screens. Rotations and offsets are fixed so screenshot goldens stay deterministic.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShapeCollage(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp),
        contentAlignment = Alignment.Center,
    ) {
        CollageItem(
            shape = MaterialShapes.Pill.toShape(),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            iconRes = R.drawable.ic_headphones,
            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
            size = 120.dp,
            iconSize = 50.dp,
            rotation = 20f,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-24).dp, y = 12.dp),
        )
        CollageItem(
            shape = MaterialShapes.Cookie9Sided.toShape(),
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            iconRes = R.drawable.ic_music_note,
            iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
            size = 190.dp,
            iconSize = 80.dp,
            rotation = -12f,
            modifier = Modifier.align(Alignment.Center),
        )
        CollageItem(
            shape = MaterialShapes.Circle.toShape(),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            iconRes = R.drawable.ic_qr_code,
            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
            size = 90.dp,
            iconSize = 42.dp,
            rotation = 8f,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 32.dp, y = 8.dp),
        )
        CollageItem(
            shape = MaterialShapes.Sunny.toShape(),
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            iconRes = R.drawable.ic_phone_android,
            iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
            size = 105.dp,
            iconSize = 48.dp,
            rotation = -8f,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-48).dp, y = (-12).dp),
        )
        CollageItem(
            shape = MaterialShapes.Clover4Leaf.toShape(),
            containerColor = MaterialTheme.colorScheme.primary,
            iconRes = R.drawable.ic_check,
            iconTint = MaterialTheme.colorScheme.onPrimary,
            size = 60.dp,
            iconSize = 30.dp,
            rotation = 14f,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 72.dp, y = (-20).dp),
        )
    }
}

@Composable
private fun CollageItem(
    shape: Shape,
    containerColor: Color,
    iconTint: Color,
    size: Dp,
    iconSize: Dp,
    rotation: Float,
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int,
) {
    Surface(
        modifier = modifier
            .size(size)
            .rotate(rotation),
        shape = shape,
        color = containerColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = iconTint,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ShapeCollagePreview() {
    SwirlMusicTheme(dynamicColor = false) {
        ShapeCollage()
    }
}
