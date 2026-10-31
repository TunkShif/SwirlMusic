package one.tunkshif.swirlmusic.ui.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
class HomeScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `content shows the greeting and all three sections`() {
        setContent(HomeTestData.content)

        compose.onNodeWithText("Good evening, tunkshif").assertExists()
        compose.onNodeWithText(text(R.string.home_daily_title)).assertExists()
        compose.onNodeWithText("Nightcall").assertExists()
        compose.onNodeWithText(text(R.string.home_playlists_title)).assertExists()
        compose.onNodeWithText("Late night drive").assertExists()
        compose.onNodeWithText(text(R.string.home_charts_title)).performScrollTo().assertExists()
    }

    @Test
    fun `play counts are written in the list style`() {
        setContent(HomeTestData.content)

        compose.onNodeWithText("1.2M plays").assertExists()
    }

    @Test
    fun `loading shows section titles without content`() {
        setContent(HomeTestData.loading)

        compose.onNodeWithText(text(R.string.home_daily_title)).assertExists()
        compose.onNodeWithText("Nightcall").assertDoesNotExist()
        compose.onNodeWithText(text(R.string.home_retry)).assertDoesNotExist()
    }

    @Test
    fun `a failed section offers retry, a healthy one still shows content`() {
        var retried = false
        compose.setContent {
            SwirlMusicTheme(dynamicColor = false) {
                HomeScreen(
                    nickname = "tunkshif",
                    state = HomeTestData.error,
                    onRefresh = {},
                    onRetry = { retried = true },
                    now = HomeTestData.evening,
                )
            }
        }

        compose.onNodeWithText(text(R.string.home_error_network)).assertExists()
        compose.onNodeWithText("Late night drive").assertExists()
        compose.onAllNodesWithText(text(R.string.home_retry)).onFirst().performClick()

        assertThat(retried).isTrue()
    }

    @Test
    fun `empty sections say so instead of showing nothing`() {
        setContent(HomeTestData.empty)

        compose.onAllNodesWithText(text(R.string.home_empty_title)).onFirst().assertExists()
    }

    @Test
    fun `without a nickname the greeting stands alone`() {
        compose.setContent {
            SwirlMusicTheme(dynamicColor = false) {
                HomeScreen(
                    nickname = null,
                    state = HomeTestData.loading,
                    onRefresh = {},
                    onRetry = {},
                    now = HomeTestData.evening,
                )
            }
        }

        compose.onNodeWithText("Good evening").assertExists()
    }

    private fun setContent(state: HomeUiState) {
        compose.setContent {
            SwirlMusicTheme(dynamicColor = false) {
                HomeScreen(
                    nickname = "tunkshif",
                    state = state,
                    onRefresh = {},
                    onRetry = {},
                    now = HomeTestData.evening,
                )
            }
        }
    }

    private fun text(resId: Int): String = context.getString(resId)
}
