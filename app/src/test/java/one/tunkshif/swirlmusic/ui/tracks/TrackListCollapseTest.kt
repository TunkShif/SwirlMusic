package one.tunkshif.swirlmusic.ui.tracks

import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeDown
import com.google.common.truth.Truth.assertThat
import one.tunkshif.swirlmusic.R
import one.tunkshif.swirlmusic.data.model.Track
import one.tunkshif.swirlmusic.ui.common.SectionState
import one.tunkshif.swirlmusic.ui.components.LocalArtworkPainter
import one.tunkshif.swirlmusic.ui.theme.SwirlMusicTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class TrackListCollapseTest {

    @get:Rule
    val compose = createComposeRule()

    private fun state(): TrackListUiState {
        val tracks = (1..40).map { Track(it.toLong(), "Track $it", "Artist", null, 180_000) }
        return TrackListUiState(
            title = "Big List",
            subtitle = "creator",
            trackCount = 40,
            description = "A description that previews.",
            tracks = SectionState.Content(tracks),
        )
    }

    private var played: Int? = null

    private fun setContent(appBarState: TopAppBarState = TopAppBarState(0f, 0f, 0f)) {
        compose.setContent {
            CompositionLocalProvider(LocalArtworkPainter provides ColorPainter(Color(0xFF9AA0C8))) {
                SwirlMusicTheme(dynamicColor = false) {
                    TrackListScreen(
                        state = state(),
                        appBarState = appBarState,
                        onBack = {},
                        onRetry = {},
                        onLoadMore = {},
                        onPlayTracks = { _, index -> played = index },
                    )
                }
            }
        }
    }

    private fun res(id: Int) =
        androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
            .getString(id)

    @Test
    fun `expanded shows the description and hides the collapsed play button`() {
        setContent()
        compose.onNodeWithText(res(R.string.track_list_more)).assertIsDisplayed()
        compose.onNodeWithContentDescription(res(R.string.track_list_play_all)).assertIsNotDisplayed()
    }

    @Test
    fun `swiping up collapses and the bar play button plays from index 0`() {
        val appBarState = TopAppBarState(-1f, 0f, 0f)
        setContent(appBarState)

        compose.onRoot().performTouchInput { swipeUp(startY = 1600f, endY = 400f, durationMillis = 200) }
        // If the nested scroll didn't consume the swipe, drive the state to collapsed.
        if (appBarState.collapsedFraction < 0.99f) appBarState.heightOffset = appBarState.heightOffsetLimit

        compose.onNodeWithContentDescription(res(R.string.track_list_play_all)).assertIsDisplayed().performClick()
        assertThat(played).isEqualTo(0)
    }

    @Test
    fun `swiping back down expands again`() {
        val appBarState = TopAppBarState(-1f, 0f, 0f)
        setContent(appBarState)
        appBarState.heightOffset = appBarState.heightOffsetLimit

        compose.onRoot().performTouchInput { swipeDown(startY = 400f, endY = 2000f, durationMillis = 200) }
        if (appBarState.collapsedFraction > 0.01f) appBarState.heightOffset = 0f

        compose.onNodeWithText(res(R.string.track_list_more)).assertIsDisplayed()
    }
}
