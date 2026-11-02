package one.tunkshif.swirlmusic.data.api.dto

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import one.tunkshif.swirlmusic.data.api.toDomain
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

    @Test
    fun `playlist detail carries description, createTime and tags`() {
        val response = json.decodeFromString<PlaylistDetailResponse>(fixture("playlist_detail.json"))

        assertThat(response.playlist.description).isNotEmpty()
        assertThat(response.playlist.createTime).isGreaterThan(0)
        assertThat(response.playlist.tags).isNotNull()
    }

    @Test
    fun `album detail carries label fields and disc or track numbers`() {
        val response = json.decodeFromString<AlbumDetailResponse>(fixture("album_detail.json"))

        assertThat(response.album.company).isNotEmpty()
        assertThat(response.album.subType).isNotEmpty()
        assertThat(response.album.publishTime).isGreaterThan(0)
        assertThat(response.album.description).contains("\n")
        assertThat(response.songs).hasSize(4)
        assertThat(response.songs.map { it.cd }).containsExactly("1", "1", "2", "2").inOrder()
        assertThat(response.songs.map { it.no }).containsExactly(1, 2, 1, 2).inOrder()
    }

    @Test
    fun `album detail songs carry their album id and name`() {
        val response = json.decodeFromString<AlbumDetailResponse>(fixture("album_detail.json"))
        val track = response.toDomain().tracks.first()

        assertThat(track.albumId).isEqualTo(1571654)
        assertThat(track.albumName).isEqualTo("Back To Basics")
    }

    @Test
    fun `album detail songs without al fall back to the album header`() {
        val response = json.decodeFromString<AlbumDetailResponse>(fixture("album_detail.json"))
        val withoutAlbum = response.copy(songs = response.songs.map { it.copy(al = null) })
        val track = withoutAlbum.toDomain().tracks.first()

        assertThat(track.albumId).isEqualTo(1571654)
        assertThat(track.albumName).isEqualTo("Back To Basics")
    }

    @Test
    fun `user playlist items carry subscribed, specialType and creator`() {
        val response = json.decodeFromString<UserPlaylistResponse>(fixture("user_playlist.json"))

        assertThat(response.playlist[0].specialType).isEqualTo(5)
        assertThat(response.playlist[0].subscribed).isFalse()
        assertThat(response.playlist[2].subscribed).isTrue()
        assertThat(response.playlist[2].creator?.nickname).isEqualTo("fixture-user")
        val summaries = response.playlist.map { it.toDomain() }
        assertThat(summaries[0].isLiked).isTrue()
        assertThat(summaries[2].subscribed).isTrue()
        assertThat(summaries[2].creatorName).isEqualTo("fixture-user")
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
