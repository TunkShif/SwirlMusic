package one.tunkshif.swirlmusic.player

import android.net.Uri
import androidx.media3.common.Player
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.Dispatchers
import one.tunkshif.swirlmusic.data.api.ApiResult
import one.tunkshif.swirlmusic.data.api.NcmAuthApi
import one.tunkshif.swirlmusic.data.api.NcmPlaybackApi
import one.tunkshif.swirlmusic.data.api.SystemTimeSource
import one.tunkshif.swirlmusic.data.api.dto.LogoutResponse
import one.tunkshif.swirlmusic.data.api.dto.QrCheckResponse
import one.tunkshif.swirlmusic.data.api.dto.QrCreateBody
import one.tunkshif.swirlmusic.data.api.dto.QrKeyBody
import one.tunkshif.swirlmusic.data.api.dto.UserAccountResponse
import one.tunkshif.swirlmusic.data.cache.AccountScopedCache
import one.tunkshif.swirlmusic.data.cache.CacheGate
import one.tunkshif.swirlmusic.data.cache.SwirlDatabase
import one.tunkshif.swirlmusic.data.model.PlayableUrl
import one.tunkshif.swirlmusic.data.model.Track
import one.tunkshif.swirlmusic.data.session.SessionCache
import one.tunkshif.swirlmusic.data.session.SessionCipher
import one.tunkshif.swirlmusic.data.session.SessionRepository
import one.tunkshif.swirlmusic.data.session.SessionStorage
import one.tunkshif.swirlmusic.data.session.SessionStore
import one.tunkshif.swirlmusic.test.R as TestR
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Queue commands against a real ExoPlayer: uid-addressed move/remove/skipTo and the three
 * loop modes. A real player needs a device; fakes cover everything above it, and the fake
 * playback API hands out `file://` URLs to a bundled tone so playback can really advance.
 */
@RunWith(AndroidJUnit4::class)
class PlayerControllerQueueTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private lateinit var controller: PlayerController
    private lateinit var db: SwirlDatabase
    private lateinit var playbackApi: FakePlaybackApi
    private lateinit var resumeStore: PlaybackResumeStore
    private lateinit var resolver: SongUrlResolver
    private lateinit var sessionRepository: SessionRepository

    private fun track(id: Long) = Track(id, "track-$id", "artist", null, 60_000)

    @Before
    fun setUp() {
        val audioFile = File(context.cacheDir, "queue_test_tone.wav")
        instrumentation.context.resources.openRawResource(TestR.raw.test_tone).use { input ->
            audioFile.outputStream().use { output -> input.copyTo(output) }
        }
        playbackApi = FakePlaybackApi(Uri.fromFile(audioFile).toString())

        db = Room.inMemoryDatabaseBuilder(context, SwirlDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = db.trackListDao()
        resumeStore = PlaybackResumeStore(
            dao, CacheGate(dao, Dispatchers.IO), SystemTimeSource, Dispatchers.IO,
        )
        sessionRepository = SessionRepository(
            FakeAuthApi(),
            SessionStore(FakeStorage(), FakeCipher()),
            SessionCache(),
            AccountScopedCache { },
        )
        resolver = SongUrlResolver(playbackApi, SystemTimeSource)
    }

    private fun createController() {
        instrumentation.runOnMainSync {
            controller = PlayerController(
                context, resolver, playbackApi, ScrobbleTracker(), sessionRepository,
                resumeStore, Dispatchers.Main,
            )
        }
    }

    @After
    fun tearDown() {
        if (::controller.isInitialized) {
            instrumentation.runOnMainSync { controller.player.release() }
        }
        db.close()
    }

    @Test
    fun move_remove_and_skip_keep_uids_aligned_with_media_items() {
        playTracks(5, startIndex = 0)
        awaitQueueSize(5)

        val uidOf = { i: Int -> controller.state.value.queue[i].uid }
        // Move the first entry to index 3.
        instrumentation.runOnMainSync { controller.move(uidOf(0), uidOf(3)) }
        awaitQueueAligned()
        assertThat(queueTrackIds()).containsExactly(2L, 3L, 4L, 1L, 5L).inOrder()

        // Skip to the entry now at index 2 (track 4).
        instrumentation.runOnMainSync { controller.skipTo(uidOf(2)) }
        awaitMain { controller.player.currentMediaItemIndex == 2 }
        assertThat(controller.state.value.currentIndex).isEqualTo(2)

        // Remove the head entry; the rest stays in order.
        instrumentation.runOnMainSync { controller.remove(uidOf(0)) }
        awaitQueueSize(4)
        assertThat(queueTrackIds()).containsExactly(3L, 4L, 1L, 5L).inOrder()
        awaitQueueAligned()
    }

    @Test
    fun removing_the_current_entry_advances_to_the_next() {
        playTracks(5, startIndex = 0)
        awaitQueueSize(5)
        instrumentation.runOnMainSync { controller.skipTo(controller.state.value.queue[1].uid) }
        awaitMain { controller.player.currentMediaItemIndex == 1 }

        instrumentation.runOnMainSync { controller.remove(controller.state.value.queue[1].uid) }
        awaitQueueSize(4)

        // ExoPlayer advances: index 1 now points at the former index 2.
        assertThat(queueTrackIds()).containsExactly(1L, 3L, 4L, 5L).inOrder()
        awaitMain { controller.player.currentMediaItemIndex == 1 }
        assertThat(controller.state.value.current?.id).isEqualTo(3L)
    }

    @Test
    fun removing_the_only_entry_stops_and_clears() {
        playTracks(1, startIndex = 0)
        awaitQueueSize(1)

        instrumentation.runOnMainSync { controller.remove(controller.state.value.queue[0].uid) }
        awaitQueueSize(0)

        assertThat(controller.state.value.current).isNull()
        var mediaItems = -1
        instrumentation.runOnMainSync { mediaItems = controller.player.mediaItemCount }
        assertThat(mediaItems).isEqualTo(0)
        awaitMain { true }
        runBlockingIdle()
        assertThat(db.trackListDao().queueIds()).isEmpty()
        assertThat(db.trackListDao().resume()).isNull()
    }

    @Test
    fun cycleLoopMode_sets_player_flags_and_shuffle_keeps_current_first() {
        playTracks(5, startIndex = 2)
        awaitQueueSize(5)
        awaitMain { controller.player.currentMediaItemIndex == 2 }

        instrumentation.runOnMainSync { controller.cycleLoopMode() }
        awaitMain {
            controller.player.shuffleModeEnabled &&
                controller.player.repeatMode == Player.REPEAT_MODE_ALL
        }
        assertThat(controller.state.value.loopMode).isEqualTo(LoopMode.Shuffle)
        // The current track plays first; the rest is a permutation behind it.
        assertThat(controller.state.value.playOrder.first()).isEqualTo(2)
        assertThat(controller.state.value.playOrder.sorted()).containsExactly(0, 1, 2, 3, 4)

        instrumentation.runOnMainSync { controller.cycleLoopMode() }
        awaitMain {
            controller.player.repeatMode == Player.REPEAT_MODE_ONE && !controller.player.shuffleModeEnabled
        }
        assertThat(controller.state.value.loopMode).isEqualTo(LoopMode.RepeatOne)

        instrumentation.runOnMainSync { controller.cycleLoopMode() }
        awaitMain {
            controller.player.repeatMode == Player.REPEAT_MODE_ALL && !controller.player.shuffleModeEnabled
        }
        assertThat(controller.state.value.loopMode).isEqualTo(LoopMode.ListLoop)
        assertThat(controller.state.value.playOrder).containsExactly(0, 1, 2, 3, 4).inOrder()
    }

    @Test
    fun restored_mode_uses_the_saved_loop_mode_and_mutates_the_queue() {
        // A saved queue + resume row, loaded before the player has any items.
        val dao = db.trackListDao()
        dao.upsertTracks((1L..3L).map { one.tunkshif.swirlmusic.data.cache.TrackEntity.of(track(it), 0) })
        dao.replaceQueue(listOf(1L, 2L, 3L))
        dao.saveResume(
            one.tunkshif.swirlmusic.data.cache.PlaybackResumeEntity(
                0, 1, 5_000, LoopMode.RepeatOne.ordinal, 0,
            ),
        )
        createController()
        awaitMain { controller.state.value.restored }

        val state = controller.state.value
        assertThat(state.loopMode).isEqualTo(LoopMode.RepeatOne)
        assertThat(state.currentIndex).isEqualTo(1)
        assertThat(state.positionMs).isEqualTo(5_000)
        assertThat(state.current?.id).isEqualTo(2L)
        assertThat(state.hasNext).isTrue()

        // In restored mode the mode lives on the restored queue, not on the empty player.
        instrumentation.runOnMainSync { controller.cycleLoopMode() }
        awaitMain { controller.state.value.loopMode == LoopMode.ListLoop }

        // Removing the entry before the current one shifts the index with it.
        val firstUid = controller.state.value.queue[0].uid
        instrumentation.runOnMainSync { controller.remove(firstUid) }
        awaitMain { controller.state.value.queue.size == 2 }
        assertThat(controller.state.value.queue.map { it.track.id }).containsExactly(2L, 3L).inOrder()
        assertThat(controller.state.value.currentIndex).isEqualTo(0)
        assertThat(controller.state.value.current?.id).isEqualTo(2L)
        assertThat(controller.state.value.restored).isTrue()
    }

    // helpers

    private fun playTracks(count: Int, startIndex: Int) {
        createController()
        instrumentation.runOnMainSync {
            controller.play((1L..count.toLong()).map(::track), startIndex)
        }
    }

    private fun queueTrackIds(): List<Long> = controller.state.value.queue.map { it.track.id }

    private fun awaitQueueSize(size: Int) =
        awaitMain { controller.player.mediaItemCount == size && controller.state.value.queue.size == size }

    private fun awaitQueueAligned() = awaitMain {
        val entries = controller.state.value.queue
        entries.size == controller.player.mediaItemCount &&
            entries.indices.all { i ->
                controller.player.getMediaItemAt(i).mediaId == entries[i].track.id.toString()
            }
    }

    private fun awaitMain(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        var done = false
        while (System.currentTimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync { done = condition() }
            if (done) return
            Thread.sleep(50)
        }
        instrumentation.runOnMainSync { assertThat(condition()).isTrue() }
    }

    /** Let the resume-store coroutines on the main scope run. */
    private fun runBlockingIdle() {
        Thread.sleep(300)
        instrumentation.waitForIdleSync()
    }

    private class FakePlaybackApi(private val url: String) : NcmPlaybackApi {
        override suspend fun songUrls(ids: List<Long>, level: String): ApiResult<List<PlayableUrl>> =
            ApiResult.Success(
                ids.map { PlayableUrl(it, url, System.currentTimeMillis() + 3_600_000, false, level, 320) },
            )

        override suspend fun scrobble(trackId: Long, playedSeconds: Int): ApiResult<Unit> =
            ApiResult.Success(Unit)
    }

    private class FakeAuthApi : NcmAuthApi {
        private fun offline() = ApiResult.NetworkError(IllegalStateException("offline in test"))

        override suspend fun qrKey(): ApiResult<QrKeyBody> = offline()
        override suspend fun qrCreate(key: String): ApiResult<QrCreateBody> = offline()
        override suspend fun qrCheck(key: String): ApiResult<QrCheckResponse> = offline()
        override suspend fun userAccount(): ApiResult<UserAccountResponse> = offline()
        override suspend fun logout(): ApiResult<LogoutResponse> = offline()
    }

    private class FakeStorage : SessionStorage {
        override suspend fun read(): String? = null
        override suspend fun write(value: String?) = Unit
    }

    private class FakeCipher : SessionCipher {
        override fun encrypt(plaintext: String) = plaintext
        override fun decrypt(ciphertext: String) = ciphertext
    }
}
