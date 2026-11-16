package one.tunkshif.swirlmusic.ui.comments

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.Instant
import one.tunkshif.swirlmusic.data.model.Comment
import one.tunkshif.swirlmusic.data.model.CommentQuote
import one.tunkshif.swirlmusic.ui.common.LoadError
import one.tunkshif.swirlmusic.ui.common.SectionState
import one.tunkshif.swirlmusic.ui.components.LocalArtworkPainter
import one.tunkshif.swirlmusic.ui.components.LocalArtworkPainterResolver
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
class CommentsScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val nowMillis = Instant.parse("2026-03-06T12:00:00Z").toEpochMilli()

    private fun comment(
        id: Long,
        user: String,
        text: String,
        replyTo: CommentQuote? = null,
        replyCount: Int = 0,
        likedCount: Long = 0,
        timeMillis: Long,
        location: String? = null,
        avatarUrl: String? = "avatar$id",
    ) = Comment(
        id = id,
        userId = id * 10,
        userName = user,
        avatarUrl = avatarUrl,
        text = text,
        timeMillis = timeMillis,
        likedCount = likedCount,
        replyCount = replyCount,
        location = location,
        replyTo = replyTo,
    )

    /** Synthetic names only; avatar colours vary by URL so the rows aren't identical. */
    private val content = CommentsUiState(
        comments = SectionState.Content(
            listOf(
                comment(
                    id = 1,
                    user = "Night Owl",
                    text = "This chorus hits different at 2am.\nStill looping it.",
                    replyTo = CommentQuote(userName = "Early Bird", text = "the bridge is the best part"),
                    replyCount = 14,
                    likedCount = 3_243,
                    timeMillis = nowMillis - 41 * 60_000L,
                    location = "Shanghai",
                ),
                comment(
                    id = 2,
                    user = "Vinyl Kid",
                    text = "打卡打卡 ❤️🤣",
                    replyCount = 1,
                    likedCount = 512,
                    timeMillis = nowMillis - 5 * 3_600_000L,
                ),
                comment(
                    id = 3,
                    user = "Quiet Listener",
                    text = "came for the bassline, stayed for the synth solo. nobody warns you about the outro.",
                    likedCount = 98,
                    timeMillis = Instant.parse("2026-03-05T10:00:00Z").toEpochMilli(),
                    location = "Berlin",
                    avatarUrl = null,
                ),
                comment(
                    id = 4,
                    user = "Tape Deck",
                    text = "heard this in a record store and had to ask what it was",
                    likedCount = 0,
                    timeMillis = Instant.parse("2025-11-20T18:00:00Z").toEpochMilli(),
                ),
            ),
        ),
        totalCount = 1_969_866L,
    )

    private fun capture(name: String, dark: Boolean, state: CommentsUiState) {
        compose.setContent {
            CompositionLocalProvider(
                LocalArtworkPainterResolver provides { url ->
                    url?.let { ColorPainter(Color(0xFF000000L or (it.hashCode().toLong() and 0xFFFFFFL))) }
                },
            ) {
                SwirlMusicTheme(darkTheme = dark, dynamicColor = false) {
                    CommentsScreen(
                        state = state,
                        title = "Midnight Drive — Kavinsky",
                        onBack = {},
                        onSelectSort = {},
                        onRetry = {},
                        onLoadMore = {},
                        nowMillis = nowMillis,
                        contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp),
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/comments/$name.png")
    }

    @Test
    fun content_light() = capture("comments-content-light", dark = false, state = content)

    @Test
    fun content_dark() = capture("comments-content-dark", dark = true, state = content)

    @Test
    fun empty_light() = capture(
        "comments-empty-light",
        dark = false,
        state = CommentsUiState(comments = SectionState.Content(emptyList()), totalCount = 0),
    )

    @Test
    fun empty_dark() = capture(
        "comments-empty-dark",
        dark = true,
        state = CommentsUiState(comments = SectionState.Content(emptyList()), totalCount = 0),
    )

    @Test
    fun error_light() = capture(
        "comments-error-light",
        dark = false,
        state = CommentsUiState(comments = SectionState.Failed(LoadError.Server)),
    )

    @Test
    fun error_dark() = capture(
        "comments-error-dark",
        dark = true,
        state = CommentsUiState(comments = SectionState.Failed(LoadError.Server)),
    )
}
