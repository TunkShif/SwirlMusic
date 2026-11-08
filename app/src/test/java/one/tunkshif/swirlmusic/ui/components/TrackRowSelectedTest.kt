package one.tunkshif.swirlmusic.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import one.tunkshif.swirlmusic.data.model.Track
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class TrackRowSelectedTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a selected track row exposes the selected semantic`() {
        compose.setContent {
            TrackRow(
                track = Track(1, "Song", "Artist", null, 100),
                index = 0,
                count = 2,
                selected = true,
                onClick = {},
            )
        }
        compose.onNodeWithText("Song").assertIsSelected()
    }
}
