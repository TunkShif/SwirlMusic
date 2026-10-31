package one.tunkshif.swirlmusic.data.api.dto

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * Contract test for `/playlist/detail` with `n`/`s`: the response must carry every id in
 * `trackIds` plus the bundled full `tracks`. Fixture is a trimmed real response (n=3).
 */
class PlaylistDetailParsingTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `trackIds and tracks parse into the contents model`() {
        val response = json.decodeFromString<PlaylistDetailResponse>(fixture("playlist_detail.json"))

        assertThat(response.playlist.trackIds).hasSize(6)
        assertThat(response.playlist.trackIds!!.first().id).isEqualTo(287398)
        assertThat(response.playlist.tracks).isNotEmpty()
        assertThat(response.playlist.tracks!!.first().id).isEqualTo(287398)
        assertThat(response.playlist.name).isNotEmpty()
    }

    /**
     * `/user/playlist` items carry `"tracks": null` and no `trackIds` — a non-nullable DTO
     * field throws on that payload, which surfaces as a network error. Fixture is a trimmed
     * real response (ids and URLs redacted).
     */
    @Test
    fun `user playlist items with null tracks still parse`() {
        val response = json.decodeFromString<UserPlaylistResponse>(fixture("user_playlist.json"))
        assertThat(response.playlist).hasSize(3)
        assertThat(response.playlist[0].tracks).isNull()
        assertThat(response.playlist[0].trackIds).isNull()
        assertThat(response.playlist[0].name).isNotEmpty()
    }

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("fixtures/ncm/$name")) {
            "missing fixture $name"
        }.readText()
}
