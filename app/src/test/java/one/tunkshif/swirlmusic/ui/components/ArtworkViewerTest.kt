package one.tunkshif.swirlmusic.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.CompositionLocalProvider
import androidx.test.espresso.Espresso
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import one.tunkshif.swirlmusic.R
import one.tunkshif.swirlmusic.ui.theme.SwirlMusicTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Dialog behaviour: content, one-shot save, back dismiss. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class ArtworkViewerTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()

    private fun show(
        onDismiss: () -> Unit = {},
        onSave: suspend () -> Boolean = { true },
    ) {
        compose.setContent {
            CompositionLocalProvider(
                LocalArtworkPainter provides ColorPainter(Color(0xFF204060)),
            ) {
                SwirlMusicTheme(dynamicColor = false) {
                    ArtworkViewer(
                        url = "https://p1.example.invalid/cover.jpg",
                        title = "OutRun",
                        onDismiss = onDismiss,
                        onSave = onSave,
                    )
                }
            }
        }
    }

    @Test
    fun `the viewer shows the title image and save button`() {
        show()

        compose.onNodeWithTag(ARTWORK_VIEWER_IMAGE_TAG).assertIsDisplayed()
        compose.onNodeWithText("OutRun").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.artwork_save)).assertIsDisplayed()
    }

    @Test
    fun `saving calls onSave once and ignores taps while in flight`() {
        val gate = CompletableDeferred<Boolean>()
        var saves = 0
        show(onSave = { saves++; gate.await() })

        compose.onNodeWithText(context.getString(R.string.artwork_save)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(R.string.artwork_save)).performClick()
        compose.waitForIdle()

        assertThat(saves).isEqualTo(1)

        gate.complete(true)
        compose.waitForIdle()
    }

    @Test
    fun `back dismisses the viewer`() {
        var dismissed = 0
        show(onDismiss = { dismissed++ })

        Espresso.pressBack()
        compose.waitForIdle()

        assertThat(dismissed).isEqualTo(1)
    }
}
