package one.tunkshif.swirlmusic.ui.playing

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import one.tunkshif.swirlmusic.data.api.ApiResult
import one.tunkshif.swirlmusic.data.cache.CacheGate
import one.tunkshif.swirlmusic.data.cache.CollectionStateEntity
import one.tunkshif.swirlmusic.data.cache.SwirlDatabase
import one.tunkshif.swirlmusic.data.library.CollectionRepository
import one.tunkshif.swirlmusic.data.library.FakeCollectionApi
import one.tunkshif.swirlmusic.ui.library.FakeLibraryApi
import one.tunkshif.swirlmusic.ui.library.signedInSession
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Optimistic like toggles: last-known paint, in-flight ignore, revert, per-track writes. */
@kotlin.OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrackLikeStateTest {

    private val api = FakeCollectionApi()
    private val libraryApi = FakeLibraryApi()
    private val db = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication(),
        SwirlDatabase::class.java,
    ).allowMainThreadQueries().build()
    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = CollectionRepository(
        api,
        libraryApi,
        signedInSession(),
        db.trackListDao(),
        CacheGate(db.trackListDao(), dispatcher),
        object : one.tunkshif.swirlmusic.data.api.TimeSource {
            override fun nowMillis() = 1_000L
        },
        dispatcher,
    )
    private val ids = MutableStateFlow<Long?>(1L)

    private fun likeState(scope: kotlinx.coroutines.CoroutineScope) =
        TrackLikeState(scope = scope, collection = repository, trackIds = ids)

    /** `liked` is WhileSubscribed: collect it so the transform runs. */
    private fun kotlinx.coroutines.test.TestScope.collect(
        like: TrackLikeState,
        values: MutableList<Boolean>,
    ) {
        backgroundScope.launch { like.liked.toList(values) }
        runCurrent()
    }

    @Test
    fun `last-known paints before the likelist lands`() = runTest {
        db.trackListDao().upsertCollectionStates(
            listOf(CollectionStateEntity("song", 1, true, 0)),
        )
        // A likelist failure leaves the row, so the paint must come from it.
        libraryApi.likedIdsResult = ApiResult.NetworkError(IOException("down"))
        val values = mutableListOf<Boolean>()
        val like = likeState(backgroundScope)

        collect(like, values)

        assertThat(values.last()).isTrue()
    }

    @Test
    fun `a toggle flips optimistically and reports Liked`() = runTest {
        val values = mutableListOf<Boolean>()
        val messages = mutableListOf<NowPlayingMessage>()
        val like = likeState(backgroundScope)
        collect(like, values)
        backgroundScope.launch { like.messages.toList(messages) }
        runCurrent()

        like.toggle(1)
        runCurrent()

        assertThat(values.last()).isTrue()
        assertThat(messages).containsExactly(NowPlayingMessage.Liked)
        assertThat(api.calls).containsExactly("setTrackLiked:1:1")
    }

    @Test
    fun `a second tap is ignored while a write is in flight`() = runTest {
        api.writeGate = CompletableDeferred()
        val values = mutableListOf<Boolean>()
        val like = likeState(backgroundScope)
        collect(like, values)

        like.toggle(1)
        runCurrent()
        assertThat(values.last()).isTrue()

        like.toggle(1)
        runCurrent()
        assertThat(api.calls.count { it.startsWith("setTrackLiked") }).isEqualTo(1)

        api.writeGate!!.complete(Unit)
        api.writeGate = null
        runCurrent()
        assertThat(repository.lastKnownTrackLiked(1)).isTrue()
    }

    @Test
    fun `a failed write reverts and reports LikeFailed`() = runTest {
        api.setTrackLikedResult = ApiResult.NetworkError(IOException("offline"))
        val values = mutableListOf<Boolean>()
        val messages = mutableListOf<NowPlayingMessage>()
        val like = likeState(backgroundScope)
        collect(like, values)
        backgroundScope.launch { like.messages.toList(messages) }
        runCurrent()

        like.toggle(1)
        runCurrent()

        assertThat(values.last()).isFalse()
        assertThat(messages).containsExactly(NowPlayingMessage.LikeFailed)
    }

    @Test
    fun `a track change mid-write applies the result to its own track`() = runTest {
        api.writeGate = CompletableDeferred()
        val values = mutableListOf<Boolean>()
        val like = likeState(backgroundScope)
        collect(like, values)

        like.toggle(1)
        runCurrent()
        ids.value = 2L
        runCurrent()
        assertThat(values.last()).isFalse()

        api.writeGate!!.complete(Unit)
        runCurrent()

        // The write completed for track 1; track 2 shows its own state.
        assertThat(repository.lastKnownTrackLiked(1)).isTrue()
        assertThat(values.last()).isFalse()
    }
}
