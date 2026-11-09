package one.tunkshif.swirlmusic.data.library

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import one.tunkshif.swirlmusic.data.api.ApiResult
import one.tunkshif.swirlmusic.data.api.WriteOutcome
import one.tunkshif.swirlmusic.ui.common.LoadError
import org.junit.Test

/** Changes flow only on Done; Refused and failures stay silent. */
@kotlin.OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [36])
class CollectionRepositoryTest {

    private val api = FakeCollectionApi()
    private val db = androidx.room.Room.inMemoryDatabaseBuilder(
        org.robolectric.RuntimeEnvironment.getApplication(),
        one.tunkshif.swirlmusic.data.cache.SwirlDatabase::class.java,
    ).allowMainThreadQueries().build()
    private val dispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher()
    private val libraryApi = one.tunkshif.swirlmusic.ui.library.FakeLibraryApi()
    private val repository = CollectionRepository(
        api,
        libraryApi,
        one.tunkshif.swirlmusic.ui.library.signedInSession(),
        db.trackListDao(),
        one.tunkshif.swirlmusic.data.cache.CacheGate(db.trackListDao(), dispatcher),
        object : one.tunkshif.swirlmusic.data.api.TimeSource { override fun nowMillis() = 1_000L },
        dispatcher,
    )

    @Test
    fun `a Done write emits its tab change`() = runTest {
        val received = mutableListOf<LibraryChange>()
        backgroundScope.launch { repository.changes.toList(received) }
        runCurrent()

        assertThat(repository.setAlbumSaved(1, saved = true)).isEqualTo(ToggleResult.Done)
        assertThat(repository.setArtistFollowed(2, followed = true)).isEqualTo(ToggleResult.Done)
        runCurrent()

        assertThat(received).containsExactly(LibraryChange.Albums, LibraryChange.Artists).inOrder()
    }

    @Test
    fun `a Refused write emits nothing`() = runTest {
        val received = mutableListOf<LibraryChange>()
        backgroundScope.launch { repository.changes.toList(received) }
        runCurrent()
        api.setAlbumSavedResult = ApiResult.Success(WriteOutcome.Refused)

        assertThat(repository.setAlbumSaved(1, saved = true)).isEqualTo(ToggleResult.Refused)
        runCurrent()

        assertThat(received).isEmpty()
    }

    @Test
    fun `a network failure maps to LoadError Network and emits nothing`() = runTest {
        val received = mutableListOf<LibraryChange>()
        backgroundScope.launch { repository.changes.toList(received) }
        runCurrent()
        api.setAlbumSavedResult = ApiResult.NetworkError(IOException("offline"))

        val result = repository.setAlbumSaved(1, saved = true)
        runCurrent()

        assertThat(result).isEqualTo(ToggleResult.Failed(LoadError.Network))
        assertThat(received).isEmpty()
    }

    @Test
    fun `an http failure maps to LoadError Server`() = runTest {
        api.setArtistFollowedResult = ApiResult.HttpError(500, "{}")

        assertThat(repository.setArtistFollowed(2, followed = true))
            .isEqualTo(ToggleResult.Failed(LoadError.Server))
    }

    @Test
    fun `an unauthorized write maps to LoadError Server`() = runTest {
        api.setArtistFollowedResult = ApiResult.Unauthorized("{}")

        assertThat(repository.setArtistFollowed(2, followed = true))
            .isEqualTo(ToggleResult.Failed(LoadError.Server))
    }

    @Test
    fun `state reads pass through`() = runTest {
        api.albumSavedResult = ApiResult.Success(true)
        api.artistFollowedResult = ApiResult.Success(false)

        assertThat(repository.albumSaved(1)).isEqualTo(ApiResult.Success(true))
        assertThat(repository.artistFollowed(2)).isEqualTo(ApiResult.Success(false))
    }
    @kotlin.OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `last-known state round trips`() = runTest {
        assertThat(repository.lastKnownAlbumSaved(1)).isNull()

        repository.rememberAlbums(mapOf(1L to true, 2L to false))
        repository.rememberArtists(mapOf(3L to true))

        assertThat(repository.lastKnownAlbumSaved(1)).isTrue()
        assertThat(repository.lastKnownAlbumSaved(2)).isFalse()
        assertThat(repository.lastKnownArtistFollowed(3)).isTrue()
    }

    @kotlin.OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `reads and Done writes are recorded and Refused and Failed are not`() = runTest {
        api.albumSavedResult = ApiResult.Success(true)
        api.artistFollowedResult = ApiResult.Success(false)
        repository.albumSaved(5)
        repository.artistFollowed(6)
        assertThat(repository.lastKnownAlbumSaved(5)).isTrue()
        assertThat(repository.lastKnownArtistFollowed(6)).isFalse()

        repository.setAlbumSaved(5, false)
        assertThat(repository.lastKnownAlbumSaved(5)).isFalse()

        api.setAlbumSavedResult = ApiResult.Success(WriteOutcome.Refused)
        repository.setAlbumSaved(7, true)
        assertThat(repository.lastKnownAlbumSaved(7)).isNull()

        api.setArtistFollowedResult = ApiResult.NetworkError(IOException("offline"))
        repository.setArtistFollowed(8, true)
        assertThat(repository.lastKnownArtistFollowed(8)).isNull()
    }

    @kotlin.OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `the account cache wipe clears remembered state`() = runTest {
        repository.rememberAlbums(mapOf(1L to true))
        assertThat(repository.lastKnownAlbumSaved(1)).isTrue()

        db.trackListDao().clearAll()

        assertThat(repository.lastKnownAlbumSaved(1)).isNull()
    }



    @Test
    fun `the likelist replaces song rows and runs once`() = runTest {
        db.trackListDao().upsertCollectionStates(
            listOf(
                one.tunkshif.swirlmusic.data.cache.CollectionStateEntity("song", 9, true, 0),
                one.tunkshif.swirlmusic.data.cache.CollectionStateEntity("album", 9, true, 0),
            ),
        )
        libraryApi.likedIdsResult = ApiResult.Success(listOf(1L, 2L, 3L))

        repository.refreshLikedIds()
        repository.refreshLikedIds()

        assertThat(repository.lastKnownTrackLiked(1)).isTrue()
        assertThat(repository.lastKnownTrackLiked(3)).isTrue()
        // Replaced, not merged: the stale row is gone, other kinds untouched.
        assertThat(repository.lastKnownTrackLiked(9)).isNull()
        assertThat(repository.lastKnownAlbumSaved(9)).isTrue()
        assertThat(libraryApi.calls.count { it.startsWith("likedIds") }).isEqualTo(1)
    }

    @Test
    fun `a Done like write upserts emits LikedSongs and drops the snapshot version`() = runTest {
        val received = mutableListOf<LibraryChange>()
        backgroundScope.launch { repository.changes.toList(received) }
        runCurrent()
        db.trackListDao().replaceSnapshot(
            one.tunkshif.swirlmusic.data.cache.SnapshotEntity(
                sourceKey = "playlist:99",
                title = "Liked",
                subtitle = null,
                artworkUrl = null,
                trackCount = 0,
                version = 7L,
                fetchedAt = 0,
                lastOpenedAt = 0,
            ),
            emptyList(),
        )

        assertThat(repository.setTrackLiked(1, liked = true)).isEqualTo(ToggleResult.Done)
        runCurrent()

        assertThat(repository.lastKnownTrackLiked(1)).isTrue()
        assertThat(received).containsExactly(LibraryChange.LikedSongs)
        assertThat(db.trackListDao().snapshot("playlist:99")!!.version).isNull()
    }

    @Test
    fun `a failed like write changes nothing`() = runTest {
        val received = mutableListOf<LibraryChange>()
        backgroundScope.launch { repository.changes.toList(received) }
        runCurrent()
        api.setTrackLikedResult = ApiResult.NetworkError(IOException("offline"))

        assertThat(repository.setTrackLiked(1, liked = true))
            .isEqualTo(ToggleResult.Failed(LoadError.Network))
        runCurrent()

        assertThat(repository.lastKnownTrackLiked(1)).isNull()
        assertThat(received).isEmpty()
    }

    @Test
    fun `a signed-out likelist refresh writes nothing and stays retryable`() = runTest {
        val signedOut = CollectionRepository(
            api,
            libraryApi,
            one.tunkshif.swirlmusic.ui.library.signedOutSession(),
            db.trackListDao(),
            one.tunkshif.swirlmusic.data.cache.CacheGate(db.trackListDao(), dispatcher),
            object : one.tunkshif.swirlmusic.data.api.TimeSource {
                override fun nowMillis() = 1_000L
            },
            dispatcher,
        )
        libraryApi.likedIdsResult = ApiResult.Success(listOf(1L))

        signedOut.refreshLikedIds()

        assertThat(repository.lastKnownTrackLiked(1)).isNull()
        assertThat(libraryApi.calls.none { it.startsWith("likedIds") }).isTrue()
    }

    @Test
    fun `signing out and back in reloads the likelist`() = runTest {
        val session = one.tunkshif.swirlmusic.ui.library.signedInSession()
        val gate = one.tunkshif.swirlmusic.data.cache.CacheGate(db.trackListDao(), dispatcher)
        val repository = CollectionRepository(
            api,
            libraryApi,
            session,
            db.trackListDao(),
            gate,
            object : one.tunkshif.swirlmusic.data.api.TimeSource {
                override fun nowMillis() = 1_000L
            },
            dispatcher,
        )
        libraryApi.likedIdsResult = ApiResult.Success(listOf(1L))
        repository.refreshLikedIds()
        assertThat(repository.lastKnownTrackLiked(1)).isTrue()

        // The real sign-out path: the cache wipes and the generation bumps.
        session.signOut()
        gate.clearAll()
        assertThat(repository.lastKnownTrackLiked(1)).isNull()

        session.signIn("MUSIC_U=test")
        libraryApi.likedIdsResult = ApiResult.Success(listOf(2L))
        repository.refreshLikedIds()

        assertThat(repository.lastKnownTrackLiked(2)).isTrue()
        assertThat(repository.lastKnownTrackLiked(1)).isNull()
        assertThat(libraryApi.calls.count { it.startsWith("likedIds") }).isEqualTo(2)
    }
}
