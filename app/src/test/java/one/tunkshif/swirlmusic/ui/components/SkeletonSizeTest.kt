package one.tunkshif.swirlmusic.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import one.tunkshif.swirlmusic.data.model.Track
import one.tunkshif.swirlmusic.ui.common.LoadingRows
import one.tunkshif.swirlmusic.ui.theme.SwirlMusicTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A skeleton must be the same size as the content it stands in for — by construction, not by
 * tuned constants. These assert it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class SkeletonSizeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a loading row is as tall as a track row`() {
        compose.setContent {
            SwirlMusicTheme(dynamicColor = false) {
                Column {
                    Box(modifier = Modifier.testTag("real")) {
                        TrackRow(
                            track = Track(1, "Song", "Artist", null, 180_000),
                            index = 0,
                            count = 1,
                            onClick = {},
                        )
                    }
                    Box(modifier = Modifier.testTag("skeleton")) {
                        LoadingRows(count = 1)
                    }
                }
            }
        }

        val real = compose.onNodeWithTag("real").fetchSemanticsNode().boundsInRoot.height
        val skeleton = compose.onNodeWithTag("skeleton").fetchSemanticsNode().boundsInRoot.height
        assertThat(skeleton).isEqualTo(real)
    }

    @Test
    fun `a grid skeleton with a subtitle is as tall as an album card`() {
        gridHeights(subtitle = "Artist", showSubtitle = true)
    }

    @Test
    fun `a grid skeleton without a subtitle is as tall as an artist card`() {
        gridHeights(subtitle = null, showSubtitle = false)
    }

    private fun gridHeights(subtitle: String?, showSubtitle: Boolean) {
        compose.setContent {
            SwirlMusicTheme(dynamicColor = false) {
                Column {
                    Box(modifier = Modifier.width(200.dp).testTag("real")) {
                        GridCard(
                            title = "Title",
                            subtitle = subtitle,
                            artworkUrl = null,
                            circularArtwork = false,
                            onClick = {},
                        )
                    }
                    Box(modifier = Modifier.width(200.dp).testTag("skeleton")) {
                        GridCardSkeleton(circularArtwork = false, showSubtitle = showSubtitle)
                    }
                }
            }
        }

        val real = compose.onNodeWithTag("real").fetchSemanticsNode().boundsInRoot.height
        val skeleton = compose.onNodeWithTag("skeleton").fetchSemanticsNode().boundsInRoot.height
        assertThat(skeleton).isEqualTo(real)
    }
}
