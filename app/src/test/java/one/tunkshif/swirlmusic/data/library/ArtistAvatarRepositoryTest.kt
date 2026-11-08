package one.tunkshif.swirlmusic.data.library

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.test.runTest
import one.tunkshif.swirlmusic.data.api.ApiResult
import one.tunkshif.swirlmusic.data.api.NcmArtistApi
import org.junit.Test

/** Cache hits skip the API; a null avatar is remembered, a failure is not. */
class ArtistAvatarRepositoryTest {

    private class FakeArtistApi : NcmArtistApi {
        val calls = mutableListOf<Long>()
        var answers = mutableMapOf<Long, ApiResult<String?>>()

        override suspend fun artistAvatar(id: Long): ApiResult<String?> {
            calls += id
            return answers.getValue(id)
        }

        override suspend fun artistAlbums(id: Long, limit: Int, offset: Int) =
            ApiResult.Success(one.tunkshif.swirlmusic.data.model.Page(emptyList<one.tunkshif.swirlmusic.data.model.ArtistRelease>(), false))

        override suspend fun artistDescription(id: Long) =
            ApiResult.Success(one.tunkshif.swirlmusic.data.model.ArtistDescription(null, emptyList()))
    }

    private val api = FakeArtistApi()
    private val repository = ArtistAvatarRepository(api)

    @Test
    fun `ids map to their urls`() = runTest {
        api.answers[1] = ApiResult.Success("https://img/1.jpg")
        api.answers[2] = ApiResult.Success("https://img/2.jpg")

        assertThat(repository.avatars(listOf(1, 2)))
            .containsExactly(1L, "https://img/1.jpg", 2L, "https://img/2.jpg")
        assertThat(api.calls).containsExactly(1L, 2L)
    }

    @Test
    fun `a second call for the same ids hits the cache`() = runTest {
        api.answers[1] = ApiResult.Success("https://img/1.jpg")
        repository.avatars(listOf(1))

        assertThat(repository.avatars(listOf(1))).containsExactly(1L, "https://img/1.jpg")
        assertThat(api.calls).containsExactly(1L)
    }

    @Test
    fun `a null avatar is remembered and absent`() = runTest {
        api.answers[1] = ApiResult.Success(null)

        assertThat(repository.avatars(listOf(1))).isEmpty()
        assertThat(repository.avatars(listOf(1))).isEmpty()
        assertThat(api.calls).containsExactly(1L)
    }

    @Test
    fun `a failed fetch is absent and retried`() = runTest {
        api.answers[1] = ApiResult.NetworkError(IOException("boom"))

        assertThat(repository.avatars(listOf(1))).isEmpty()

        api.answers[1] = ApiResult.Success("https://img/1.jpg")

        assertThat(repository.avatars(listOf(1))).containsExactly(1L, "https://img/1.jpg")
        assertThat(api.calls).containsExactly(1L, 1L)
    }
}
