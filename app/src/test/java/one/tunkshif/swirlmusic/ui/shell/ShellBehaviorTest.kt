package one.tunkshif.swirlmusic.ui.shell

import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import one.tunkshif.swirlmusic.R
import one.tunkshif.swirlmusic.navigation.TopLevelDestination
import one.tunkshif.swirlmusic.ui.theme.SwirlMusicTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Behaviour tests for the shell. Runs on the JVM: no device, no Hilt, no network.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class ShellBehaviorTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `starts on home and shows an empty state`() {
        compose.setContent {
            SwirlMusicTheme(dynamicColor = false) { SwirlApp() }
        }

        compose.onNodeWithText(context.getString(R.string.home_empty_body)).assertExists()
    }

    @Test
    fun `tapping a tab replaces the screen`() {
        compose.setContent {
            SwirlMusicTheme(dynamicColor = false) { SwirlApp() }
        }

        compose.onAllNodesWithText(label(TopLevelDestination.Library)).onFirst().performClick()
        compose.onNodeWithText(context.getString(R.string.library_empty_body)).assertExists()

        compose.onAllNodesWithText(label(TopLevelDestination.Profile)).onFirst().performClick()
        compose.onNodeWithText(context.getString(R.string.profile_empty_body)).assertExists()
    }

    @Test
    fun `the mini player opens the playing screen and hides the chrome`() {
        compose.setContent {
            SwirlMusicTheme(dynamicColor = false) { SwirlApp() }
        }

        compose.onNodeWithText(context.getString(R.string.mini_player_idle_title)).performClick()

        compose.onNodeWithText(context.getString(R.string.playing_empty_body)).assertExists()
        // The bottom bar is gone, so no navigation label is on screen.
        assertThat(compose.onAllNodesWithText(label(TopLevelDestination.Home)).fetchSemanticsNodes())
            .isEmpty()
    }

    @Test
    fun `back from the playing screen returns to the tab`() {
        compose.setContent {
            SwirlMusicTheme(dynamicColor = false) { SwirlApp() }
        }

        compose.onAllNodesWithText(label(TopLevelDestination.Library)).onFirst().performClick()
        compose.onNodeWithText(context.getString(R.string.mini_player_idle_title)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.playing_back)).performClick()

        compose.onNodeWithText(context.getString(R.string.library_empty_body)).assertExists()
    }

    @Test
    fun `the selected tab survives recreation, as after a rotation`() {
        val restorationTester = StateRestorationTester(compose)
        restorationTester.setContent {
            SwirlMusicTheme(dynamicColor = false) { SwirlApp() }
        }

        compose.onAllNodesWithText(label(TopLevelDestination.Library)).onFirst().performClick()
        restorationTester.emulateSavedInstanceStateRestore()

        compose.onNodeWithText(context.getString(R.string.library_empty_body)).assertExists()
    }

    private fun label(destination: TopLevelDestination) = context.getString(destination.labelRes)
}
