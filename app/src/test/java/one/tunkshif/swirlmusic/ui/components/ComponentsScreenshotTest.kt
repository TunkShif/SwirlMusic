package one.tunkshif.swirlmusic.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import one.tunkshif.swirlmusic.ui.theme.Spacing
import one.tunkshif.swirlmusic.ui.theme.SwirlMusicTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class ComponentsScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    /** The skeleton grid: square cards and circular (artist) cards side by side. */
    @Test
    fun grid_skeleton_light() {
        compose.setContent {
            SwirlMusicTheme(darkTheme = false, dynamicColor = false) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 152.dp),
                    userScrollEnabled = false,
                    contentPadding = PaddingValues(Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(6) { GridCardSkeleton(circularArtwork = it % 2 == 1) }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/components/grid-skeleton-light.png")
    }

    /** The artwork viewer: scrim, image, caption and the save button. */
    @Test
    fun artwork_viewer_light() {
        compose.setContent {
            SwirlMusicTheme(darkTheme = false, dynamicColor = false) {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalArtworkPainter provides
                        androidx.compose.ui.graphics.painter.ColorPainter(
                            androidx.compose.ui.graphics.Color(0xFF9AA0C8),
                        ),
                ) {
                    ArtworkViewerContent(
                        url = "https://p1.example.invalid/cover.jpg",
                        title = "Imaginal Disk",
                        onDismiss = {},
                        onSave = { true },
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/components/artwork-viewer-light.png")
    }
}
