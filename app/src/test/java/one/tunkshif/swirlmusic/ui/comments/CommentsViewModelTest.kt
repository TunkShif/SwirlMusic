package one.tunkshif.swirlmusic.ui.comments

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import one.tunkshif.swirlmusic.data.api.ApiResult
import one.tunkshif.swirlmusic.data.api.NcmCommentsApi
import one.tunkshif.swirlmusic.data.model.Comment
import one.tunkshif.swirlmusic.data.model.CommentPage
import one.tunkshif.swirlmusic.data.model.CommentPageKey
import one.tunkshif.swirlmusic.data.model.CommentQuote
import one.tunkshif.swirlmusic.data.model.CommentSort
import one.tunkshif.swirlmusic.data.model.CommentTarget
import one.tunkshif.swirlmusic.data.model.CommentTargetKind
import one.tunkshif.swirlmusic.ui.common.LoadError
import one.tunkshif.swirlmusic.ui.common.SectionState
import org.junit.After
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CommentsViewModelTest {

    private val target = CommentTarget(CommentTargetKind.Song, 186016)

    private class FakeCommentsApi : NcmCommentsApi {
        data class Call(
            val target: CommentTarget,
            val sort: CommentSort,
            val key: CommentPageKey?,
        )

        val calls = mutableListOf<Call>()
        val results = ArrayDeque<ApiResult<CommentPage>>()

        override suspend fun comments(
            target: CommentTarget,
            sort: CommentSort,
            key: CommentPageKey?,
        ): ApiResult<CommentPage> {
            calls += Call(target, sort, key)
            return results.removeFirst()
        }
    }

    private val api = FakeCommentsApi()
    private val viewModel = CommentsViewModel(api)

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.useTestMain() {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    }

    private fun comment(id: Long, user: String = "user$id") = Comment(
        id = id,
        userId = id * 10,
        userName = user,
        avatarUrl = "avatar$id",
        text = "text $id",
        timeMillis = 1_000L,
        likedCount = id * 100,
        replyCount = id.toInt(),
        location = null,
        replyTo = null,
    )

    private fun page(
        comments: List<Comment>,
        total: Long = 99,
        next: CommentPageKey? = null,
    ) = ApiResult.Success(CommentPage(comments, total, next))

    @Test
    fun `the first load asks for the hot first page`() = runTest {
        useTestMain()
        api.results += page(listOf(comment(1)), total = 5110, next = CommentPageKey(2, "k"))

        viewModel.load(target)
        advanceUntilIdle()

        assertThat(api.calls).containsExactly(
            FakeCommentsApi.Call(target, CommentSort.Hot, null),
        )
        val state = viewModel.state.value
        assertThat(state.sort).isEqualTo(CommentSort.Hot)
        assertThat(state.totalCount).isEqualTo(5110)
        assertThat((state.comments as SectionState.Content).value).hasSize(1)
        assertThat(state.canLoadMore).isTrue()
    }

    @Test
    fun `loadMore passes the returned key and appends, deduped by id`() = runTest {
        useTestMain()
        val key = CommentPageKey(2, "k1")
        api.results += page(listOf(comment(1), comment(2)), next = key)
        api.results += page(listOf(comment(2), comment(3)), next = CommentPageKey(3, "k2"))

        viewModel.load(target)
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        assertThat(api.calls[1]).isEqualTo(FakeCommentsApi.Call(target, CommentSort.Hot, key))
        val items = (viewModel.state.value.comments as SectionState.Content).value
        assertThat(items.map { it.id }).containsExactly(1L, 2L, 3L).inOrder()
        assertThat(viewModel.state.value.canLoadMore).isTrue()
    }

    @Test
    fun `paging stops when next is null`() = runTest {
        useTestMain()
        api.results += page(listOf(comment(1)), next = null)

        viewModel.load(target)
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        assertThat(api.calls).hasSize(1)
        assertThat(viewModel.state.value.canLoadMore).isFalse()
    }

    @Test
    fun `switching to newest fetches once and switching back does not refetch`() = runTest {
        useTestMain()
        api.results += page(listOf(comment(1)), next = CommentPageKey(2, "h"))
        api.results += page(listOf(comment(9)), next = CommentPageKey(2, "1522551232881"))

        viewModel.load(target)
        advanceUntilIdle()
        viewModel.onSelectSort(CommentSort.Newest)
        advanceUntilIdle()

        assertThat(api.calls.map { it.sort }).containsExactly(CommentSort.Hot, CommentSort.Newest)
        var items = (viewModel.state.value.comments as SectionState.Content).value
        assertThat(items.map { it.id }).containsExactly(9L)

        viewModel.onSelectSort(CommentSort.Hot)
        advanceUntilIdle()
        assertThat(api.calls).hasSize(2)
        items = (viewModel.state.value.comments as SectionState.Content).value
        assertThat(items.map { it.id }).containsExactly(1L)

        // The Newest feed is cached too: going back to it fires nothing.
        viewModel.onSelectSort(CommentSort.Newest)
        advanceUntilIdle()
        assertThat(api.calls).hasSize(2)
    }

    @Test
    fun `a first-page failure becomes the error state and retry reloads`() = runTest {
        useTestMain()
        api.results += ApiResult.HttpError(400, "{}")
        api.results += page(listOf(comment(1)))

        viewModel.load(target)
        advanceUntilIdle()

        assertThat(viewModel.state.value.comments)
            .isEqualTo(SectionState.Failed(LoadError.Server))

        viewModel.retry()
        advanceUntilIdle()

        assertThat(api.calls).hasSize(2)
        val items = (viewModel.state.value.comments as SectionState.Content).value
        assertThat(items).hasSize(1)
    }

    @Test
    fun `a load-more failure keeps items and retry resumes with the same key`() = runTest {
        useTestMain()
        val key = CommentPageKey(2, "k1")
        api.results += page(listOf(comment(1)), next = key)
        api.results += ApiResult.NetworkError(java.io.IOException("down"))
        api.results += page(listOf(comment(2)), next = null)

        viewModel.load(target)
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        var state = viewModel.state.value
        assertThat((state.comments as SectionState.Content).value).hasSize(1)
        assertThat(state.moreError).isEqualTo(LoadError.Network)
        assertThat(state.canLoadMore).isTrue()

        viewModel.loadMore()
        advanceUntilIdle()

        assertThat(api.calls).hasSize(3)
        assertThat(api.calls[2].key).isEqualTo(key)
        state = viewModel.state.value
        assertThat((state.comments as SectionState.Content).value.map { it.id })
            .containsExactly(1L, 2L)
        assertThat(state.moreError).isNull()
    }
}
