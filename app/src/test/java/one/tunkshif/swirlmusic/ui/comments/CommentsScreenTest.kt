package one.tunkshif.swirlmusic.ui.comments

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import one.tunkshif.swirlmusic.data.model.Comment
import one.tunkshif.swirlmusic.data.model.CommentQuote
import one.tunkshif.swirlmusic.data.model.CommentSort
import one.tunkshif.swirlmusic.ui.common.LoadError
import one.tunkshif.swirlmusic.ui.common.SectionState
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
class CommentsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val nowMillis = Instant.parse("2026-03-06T12:00:00Z").toEpochMilli()

    private fun comment(
        id: Long,
        user: String = "user$id",
        text: String = "text $id",
        replyTo: CommentQuote? = null,
        replyCount: Int = 0,
        likedCount: Long = 0,
        timeMillis: Long = nowMillis - 61_000L,
        location: String? = null,
    ) = Comment(
        id = id,
        userId = id * 10,
        userName = user,
        avatarUrl = "avatar$id",
        text = text,
        timeMillis = timeMillis,
        likedCount = likedCount,
        replyCount = replyCount,
        location = location,
        replyTo = replyTo,
    )

    private fun setContent(
        state: CommentsUiState,
        onSelectSort: (CommentSort) -> Unit = {},
        onBack: () -> Unit = {},
        onRetry: () -> Unit = {},
        onLoadMore: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            SwirlMusicTheme(dynamicColor = false) {
                CommentsScreen(
                    state = state,
                    title = "Midnight Drive",
                    onBack = onBack,
                    onSelectSort = onSelectSort,
                    onRetry = onRetry,
                    onLoadMore = onLoadMore,
                    nowMillis = nowMillis,
                    contentPadding = PaddingValues(bottom = 24.dp),
                )
            }
        }
    }

    @Test
    fun `the toolbar shows the comments title and the ellipsized target title`() {
        setContent(CommentsUiState(comments = SectionState.Loading))
        composeTestRule.onNodeWithText("Comments").assertIsDisplayed()
        composeTestRule.onNodeWithText("Midnight Drive").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed()
    }

    @Test
    fun `rows show the name, meta, quote, text, likes and reply count`() {
        val comments = listOf(
            comment(
                id = 1,
                user = "Listener 3",
                text = "First line\nSecond line",
                replyTo = CommentQuote(userName = "Listener 11", text = "original take"),
                replyCount = 14,
                likedCount = 3200,
                timeMillis = nowMillis - 3 * 3_600_000L,
                location = "Shanghai",
            ),
            comment(id = 2, user = "Listener 7", text = "plain words", timeMillis = nowMillis - 2 * 24 * 3_600_000L + 1000),
        )
        setContent(CommentsUiState(comments = SectionState.Content(comments), totalCount = 1_969_866L))

        composeTestRule.onNodeWithText("Listener 3").assertIsDisplayed()
        composeTestRule.onNodeWithText("First line\nSecond line").assertIsDisplayed()
        composeTestRule.onNodeWithText("Listener 11: original take").assertIsDisplayed()
        composeTestRule.onNodeWithText("3.2K").assertIsDisplayed()
        composeTestRule.onNodeWithText("14 replies").assertIsDisplayed()
        composeTestRule.onNodeWithText("Listener 7").assertIsDisplayed()
        composeTestRule.onNodeWithText("plain words").assertIsDisplayed()
        composeTestRule.onNodeWithText("1,969,866 comments").assertIsDisplayed()
    }

    @Test
    fun `tapping newest selects it and the hot feed stays cached`() {
        var selected: CommentSort? = null
        setContent(
            CommentsUiState(comments = SectionState.Content(listOf(comment(1)))),
            onSelectSort = { selected = it },
        )
        composeTestRule.onNodeWithText("Hot").assertIsSelected()
        composeTestRule.onNodeWithText("Newest").assertIsNotSelected()
        composeTestRule.onNodeWithText("Newest").performClick()
        assertThat(selected).isEqualTo(CommentSort.Newest)
    }

    @Test
    fun `a first-page failure shows the error and retry reloads`() {
        var retried = false
        setContent(
            CommentsUiState(comments = SectionState.Failed(LoadError.Server)),
            onRetry = { retried = true },
        )
        composeTestRule.onNodeWithText("Could not load.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry").performClick()
        assertThat(retried).isTrue()
    }

    @Test
    fun `an empty thread shows the empty state`() {
        setContent(CommentsUiState(comments = SectionState.Content(emptyList()), totalCount = 0))
        composeTestRule.onNodeWithText("No comments yet").assertIsDisplayed()
        composeTestRule.onNodeWithText("0 comments").assertIsDisplayed()
    }

    @Test
    fun `a load-more failure shows a retry row while items stay`() {
        setContent(
            CommentsUiState(
                comments = SectionState.Content(listOf(comment(1))),
                canLoadMore = true,
                moreError = LoadError.Network,
            ),
        )
        composeTestRule.onNodeWithText("text 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Could not load. Check your connection.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry").assertIsDisplayed()
    }

    @Test
    fun `the end of the list triggers load more`() {
        var loads = 0
        setContent(
            CommentsUiState(
                comments = SectionState.Content((1L..30L).map { comment(it) }),
                canLoadMore = true,
            ),
            onLoadMore = { loads++ },
        )
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasText("text 30"))
        composeTestRule.waitForIdle()
        assertThat(loads).isGreaterThan(0)
    }
}
