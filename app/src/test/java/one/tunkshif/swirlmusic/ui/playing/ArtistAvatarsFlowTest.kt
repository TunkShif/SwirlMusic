package one.tunkshif.swirlmusic.ui.playing

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** The menu's avatar map: empty first, fetched only for multi-artist tracks. */
@OptIn(ExperimentalCoroutinesApi::class)
class ArtistAvatarsFlowTest {

    private class FakeFetch {
        val calls = mutableListOf<List<Long>>()
        val deferreds = mutableMapOf<List<Long>, CompletableDeferred<Map<Long, String>>>()

        suspend fun fetch(ids: List<Long>): Map<Long, String> {
            calls += ids
            return deferreds.getOrPut(ids) { CompletableDeferred() }.await()
        }
    }

    @Test
    fun `a multi-artist list emits empty then the fetched map`() = runTest {
        val fetch = FakeFetch()
        val ids = MutableStateFlow(listOf(1L, 2L))

        artistAvatarsFlow(ids, fetch::fetch).test {
            assertThat(awaitItem()).isEmpty()
            fetch.deferreds.getValue(listOf(1, 2)).complete(mapOf(1L to "u1", 2L to "u2"))
            assertThat(awaitItem()).containsExactly(1L, "u1", 2L, "u2")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a single artist emits empty and never fetches`() = runTest {
        val fetch = FakeFetch()

        artistAvatarsFlow(kotlinx.coroutines.flow.flowOf(listOf(1L)), fetch::fetch).test {
            assertThat(awaitItem()).isEmpty()
            awaitComplete()
        }
        assertThat(fetch.calls).isEmpty()
    }

    @Test
    fun `a repeated list does not refetch`() = runTest {
        val fetch = FakeFetch()
        val ids = MutableStateFlow(listOf(1L, 2L))

        artistAvatarsFlow(ids, fetch::fetch).test {
            skipItems(1)
            fetch.deferreds.getValue(listOf(1, 2)).complete(emptyMap())
            assertThat(awaitItem()).isEmpty()
            ids.value = listOf(1L, 2L)
            expectNoEvents()
            assertThat(fetch.calls).hasSize(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a new list cancels the pending fetch`() = runTest {
        val fetch = FakeFetch()
        val ids = MutableStateFlow(listOf(1L, 2L))

        artistAvatarsFlow(ids, fetch::fetch).test {
            assertThat(awaitItem()).isEmpty()
            // Switch tracks while the first fetch is still pending.
            ids.value = listOf(3L, 4L)
            assertThat(awaitItem()).isEmpty()
            fetch.deferreds.getValue(listOf(1, 2)).complete(mapOf(1L to "stale"))
            fetch.deferreds.getValue(listOf(3, 4)).complete(mapOf(3L to "u3"))
            assertThat(awaitItem()).containsExactly(3L, "u3")
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
