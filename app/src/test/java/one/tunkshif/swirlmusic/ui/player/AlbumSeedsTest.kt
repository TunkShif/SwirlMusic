package one.tunkshif.swirlmusic.ui.player

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import one.tunkshif.swirlmusic.data.media.ArtworkSeedSource
import org.junit.Test

/** `toAlbumSeeds`: dedupe on URL, cancel the pending seed when the track changes. */
@OptIn(ExperimentalCoroutinesApi::class)
class AlbumSeedsTest {

    private class FakeSeedSource : ArtworkSeedSource {
        val calls = mutableListOf<String>()
        private val deferreds = mutableMapOf<String, CompletableDeferred<Int?>>()

        fun answer(url: String, seed: Int?) {
            deferreds.getOrPut(url) { CompletableDeferred() }.complete(seed)
        }

        override suspend fun seed(artworkUrl: String): Int? {
            calls += artworkUrl
            return deferreds.getOrPut(artworkUrl) { CompletableDeferred() }.await()
        }
    }

    @Test
    fun `a url emits its seed`() = runTest {
        val source = FakeSeedSource()
        val urls = MutableStateFlow<String?>("u1")

        urls.toAlbumSeeds(source).test {
            source.answer("u1", 42)
            assertThat(awaitItem()).isEqualTo(42)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a repeated url does not reseed`() = runTest {
        val source = FakeSeedSource()
        val urls = MutableStateFlow<String?>("u1")

        urls.toAlbumSeeds(source).test {
            source.answer("u1", 7)
            assertThat(awaitItem()).isEqualTo(7)
            urls.value = "u1"
            urls.value = "u1"
            expectNoEvents()
            assertThat(source.calls).containsExactly("u1")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a track change cancels the pending seed`() = runTest {
        val source = FakeSeedSource()
        val urls = MutableStateFlow<String?>("a")

        urls.toAlbumSeeds(source).test {
            // A is still resolving when B arrives; only B's seed may surface.
            urls.value = "b"
            source.answer("a", 1)
            source.answer("b", 2)
            assertThat(awaitItem()).isEqualTo(2)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a null url emits null`() = runTest {
        val source = FakeSeedSource()

        kotlinx.coroutines.flow.flowOf<String?>(null).toAlbumSeeds(source).test {
            assertThat(awaitItem()).isNull()
            awaitComplete()
        }
        assertThat(source.calls).isEmpty()
    }
}
