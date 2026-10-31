package one.tunkshif.swirlmusic.ui.search

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import one.tunkshif.swirlmusic.data.api.ApiResult
import one.tunkshif.swirlmusic.data.local.SearchHistory
import one.tunkshif.swirlmusic.data.model.SearchType
import one.tunkshif.swirlmusic.ui.common.LoadError
import one.tunkshif.swirlmusic.ui.common.SectionState
import one.tunkshif.swirlmusic.ui.library.FakeLibraryApi
import org.junit.After
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val api = FakeLibraryApi()
    private val history = InMemorySearchHistory()

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.useTestMain() {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    }

    private fun viewModel(): SearchViewModel = SearchViewModel(api = api, history = history)

    private class InMemorySearchHistory : SearchHistory {
        private val entries = mutableListOf<String>()
        override suspend fun recent(): List<String> = entries.toList()
        override suspend fun add(query: String) {
            entries.removeAll { it.equals(query, true) }
            entries.add(0, query)
        }

        override suspend fun clear() {
            entries.clear()
        }
    }

    @Test
    fun `a submitted query shows results and is remembered`() = runTest {
        useTestMain()
        val viewModel = viewModel()

        viewModel.onQueryChange("kavinsky")
        viewModel.submit()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat((state.results as SectionState.Content).value.songs).hasSize(2)
        assertThat(state.history).contains("kavinsky")
        assertThat(state.searching).isFalse()
    }

    @Test
    fun `a blank query does nothing`() = runTest {
        useTestMain()
        val viewModel = viewModel()

        viewModel.onQueryChange("   ")
        viewModel.submit()
        advanceUntilIdle()

        assertThat(api.calls).isEmpty()
        assertThat(viewModel.state.value.submitted).isFalse()
    }

    @Test
    fun `changing the type searches again`() = runTest {
        useTestMain()
        val viewModel = viewModel()
        viewModel.onQueryChange("kavinsky")
        viewModel.submit()
        advanceUntilIdle()

        viewModel.selectType(SearchType.Albums)
        advanceUntilIdle()

        assertThat(api.calls).contains("search:kavinsky:Albums")
    }

    @Test
    fun `a network failure is reported`() = runTest {
        useTestMain()
        api.failWith = ApiResult.NetworkError(IOException("offline"))
        val viewModel = viewModel()

        viewModel.onQueryChange("kavinsky")
        viewModel.submit()
        advanceUntilIdle()

        assertThat(viewModel.state.value.results)
            .isEqualTo(SectionState.Failed(LoadError.Network))
    }

    @Test
    fun `history can be cleared`() = runTest {
        useTestMain()
        val viewModel = viewModel()
        viewModel.onQueryChange("kavinsky")
        viewModel.submit()
        advanceUntilIdle()

        viewModel.clearHistory()
        advanceUntilIdle()

        assertThat(viewModel.state.value.history).isEmpty()
    }
}
