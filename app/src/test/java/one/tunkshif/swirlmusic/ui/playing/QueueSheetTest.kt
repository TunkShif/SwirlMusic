package one.tunkshif.swirlmusic.ui.playing

import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import one.tunkshif.swirlmusic.R
import one.tunkshif.swirlmusic.ui.theme.SwirlMusicTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Behaviour tests for the queue sheet body. JVM, no player. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class QueueSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()

    private val events = mutableListOf<NowPlayingEvent>()

    private fun item(uid: Long) = QueueItemUi(uid, "title-$uid", "artist-$uid", null)

    private fun show(queue: QueueUiState) {
        compose.setContent {
            SwirlMusicTheme(dynamicColor = false) {
                Surface { QueueSheetContent(queue = queue, onEvent = { events += it }) }
            }
        }
    }

    private fun reorderable(count: Int, current: Long = 2L) = QueueUiState(
        items = (0L until count).map(::item),
        currentUid = current,
        canReorder = true,
    )

    private fun hasAction(label: String) = SemanticsMatcher("action '$label'") { node ->
        node.config.getOrElse(SemanticsActions.CustomActions) { emptyList() }
            .any { it.label == label }
    }

    private fun SemanticsNodeInteraction.performCustomAction(label: String) {
        val actions = fetchSemanticsNode().config
            .getOrElse(SemanticsActions.CustomActions) { emptyList() }
        requireNotNull(actions.firstOrNull { it.label == label }) { "no action '$label'" }
            .action
            .invoke()
    }

    @Test
    fun `a row tap sends skip-to with the uid`() {
        show(reorderable(5))

        compose.onNodeWithText("title-3").performClick()

        assertThat(events).containsExactly(NowPlayingEvent.SkipTo(3L))
    }

    @Test
    fun `the remove action sends remove`() {
        show(reorderable(5))

        compose.onNodeWithText("title-1")
            .performCustomAction(context.getString(R.string.playing_queue_remove))

        assertThat(events).containsExactly(NowPlayingEvent.Remove(1L))
    }

    @Test
    fun `move down sends move to the next uid`() {
        show(reorderable(5))

        compose.onNodeWithText("title-0")
            .performCustomAction(context.getString(R.string.playing_queue_move_down))

        assertThat(events).containsExactly(NowPlayingEvent.Move(0L, 1L))
    }

    @Test
    fun `shuffle shows the hint and hides handles and move actions`() {
        show(reorderable(5).copy(canReorder = false))

        compose.onNodeWithText(context.getString(R.string.playing_queue_shuffle_hint)).assertExists()
        compose.onNodeWithContentDescription(context.getString(R.string.playing_queue_drag))
            .assertDoesNotExist()
        compose.onNode(hasAction(context.getString(R.string.playing_queue_move_down)))
            .assertDoesNotExist()
        compose.onNode(hasAction(context.getString(R.string.playing_queue_move_up)))
            .assertDoesNotExist()
    }

    @Test
    fun `the current row carries the now-playing state description`() {
        show(reorderable(5, current = 2L))

        compose.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                context.getString(R.string.playing_queue_now_playing),
            ),
        ).assertExists()
    }
}
