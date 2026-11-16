package one.tunkshif.swirlmusic.ui.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
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
    fun `content shows the greeting, lucky button, and both sections`() {
        setContent(HomeTestData.content)

        compose.onNodeWithText("Good evening").assertExists()
        compose.onNodeWithText("tunkshif").assertExists()
        compose.onNodeWithText(text(R.string.home_feeling_lucky)).assertExists()
        compose.onNodeWithText(text(R.string.home_daily_title)).assertExists()
        compose.onAllNodesWithText("Nightcall").onFirst().assertExists()
        // Item 5 of the seven-item list is the "Recently played" header; index scroll avoids
        // the semantics churn that makes performScrollToNode lose the list mid-scroll.
        compose.onNode(
            hasScrollToIndexAction() and hasAnyDescendant(hasText("Good evening")),
        ).performScrollToIndex(5)
        compose.onNodeWithText(text(R.string.home_recent_title)).assertExists()
        compose.onNodeWithText("OutRun").assertExists()
    }

    @Test
    fun `tapping a recent album opens it`() {
        var opened = -1L
        compose.setContent {
            SwirlMusicTheme(dynamicColor = false) {
                HomeScreen(
                    nickname = "tunkshif",
                    state = HomeTestData.content,
                    onRefresh = {},
                    onRetry = {},
                    onOpenAlbum = { opened = it },
                    now = HomeTestData.evening,
                )
            }
        }

        compose.onNode(
            hasScrollToIndexAction() and hasAnyDescendant(hasText("Good evening")),
        ).performScrollToIndex(6)
        compose.onNodeWithText("OutRun").performClick()

        assertThat(opened).isEqualTo(30)
    }

    @Test
    fun `tapping feeling lucky calls the action`() {
        var tapped = false
        compose.setContent {
            SwirlMusicTheme(dynamicColor = false) {
                HomeScreen(
                    nickname = "tunkshif",
                    state = HomeTestData.content,
                    onRefresh = {},
                    onRetry = {},
                    onLucky = { tapped = true },
                    now = HomeTestData.evening,
                )
            }
        }

        compose.onNodeWithText(text(R.string.home_feeling_lucky)).performClick()

        assertThat(tapped).isTrue()
    }

    @Test
    fun `feeling lucky is hidden without liked songs`() {
        setContent(HomeTestData.content.copy(likedIds = emptyList()))

        compose.onNodeWithText(text(R.string.home_feeling_lucky)).assertDoesNotExist()
    }

    @Test
    fun `an empty recent row hides its header too`() {
        setContent(HomeTestData.empty)

        compose.onNodeWithText(text(R.string.home_recent_title)).assertDoesNotExist()
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
