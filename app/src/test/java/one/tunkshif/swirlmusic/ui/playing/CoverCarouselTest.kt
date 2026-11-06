package one.tunkshif.swirlmusic.ui.playing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import one.tunkshif.swirlmusic.R
import one.tunkshif.swirlmusic.ui.theme.SwirlMusicTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class CoverCarouselTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private val skips = mutableListOf<Long>()
    private var taps = 0

    private val track = NowPlayingTrack("t", "a", null, null, null, null)
    private val prev = NowPlayingCover(uid = 7, artworkUrl = null, placeholderArtworkUrl = null)
    private val next = NowPlayingCover(uid = 9, artworkUrl = null, placeholderArtworkUrl = null)

    private fun show(withNeighbours: Boolean = true) {
        compose.setContent {
            SwirlMusicTheme(dynamicColor = false) {
                Surface {
                    CoverCarousel(
                        current = track,
                        currentUid = 8L,
                        previous = if (withNeighbours) prev else null,
                        next = if (withNeighbours) next else null,
                        onSkipTo = { skips += it },
                        coverModifier = Modifier
                            .size(200.dp)
                            .clickable { taps++ },
                        modifier = Modifier,
                    )
                }
            }
        }
    }

    @Test
    fun `swipe left skips to next`() {
        show()
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertThat(skips).containsExactly(9L)
    }

    @Test
    fun `swipe right skips to previous`() {
        show()
        compose.onRoot().performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertThat(skips).containsExactly(7L)
    }

    @Test
    fun `tap calls onTap`() {
        show()
        compose.onRoot().performTouchInput { click(center) }
        assertThat(taps).isEqualTo(1)
        assertThat(skips).isEmpty()
    }

    @Test
    fun `no neighbours means no skip`() {
        show(withNeighbours = false)
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertThat(skips).isEmpty()
    }

    @Test
    fun `a short slow drag settles back with no skip`() {
        show()
        compose.onRoot().performTouchInput {
            down(center)
            moveBy(Offset(-width * 0.15f, 0f), delayMillis = 1_000)
            up()
        }
        compose.waitForIdle()
        assertThat(skips).isEmpty()
    }

    @Test
    fun `a uid change returns the carousel to current`() {
        var uid by mutableLongStateOf(8L)
        compose.setContent {
            SwirlMusicTheme(dynamicColor = false) {
                Surface {
                    CoverCarousel(
                        current = track,
                        currentUid = uid,
                        previous = prev,
                        next = next,
                        onSkipTo = { skips += it },
                        coverModifier = Modifier.size(200.dp),
                        modifier = Modifier,
                    )
                }
            }
        }
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        uid = 9L
        compose.waitForIdle()
        assertThat(skips).containsExactly(9L)
    }

    @Test
    fun `accessibility actions skip to neighbours`() {
        show()
        val node = compose.onNode(SemanticsMatcher("has custom actions") { n ->
            n.config.getOrElse(SemanticsActions.CustomActions) { emptyList() }
                .any { it.label == context.getString(R.string.playing_previous) }
        }).fetchSemanticsNode()
        val actions = node.config[SemanticsActions.CustomActions]
        actions.first { it.label == context.getString(R.string.playing_previous) }.action()
        actions.first { it.label == context.getString(R.string.playing_next) }.action()
        assertThat(skips).containsExactly(7L, 9L).inOrder()
    }

    @Test
    fun `with only a next neighbour swipe right does nothing`() {
        compose.setContent {
            SwirlMusicTheme(dynamicColor = false) {
                Surface {
                    CoverCarousel(
                        current = track,
                        currentUid = 8L,
                        previous = null,
                        next = next,
                        onSkipTo = { skips += it },
                        coverModifier = Modifier.size(200.dp),
                        modifier = Modifier,
                    )
                }
            }
        }
        compose.onRoot().performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertThat(skips).isEmpty()
    }
}
