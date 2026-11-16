package one.tunkshif.swirlmusic.data.api

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import one.tunkshif.swirlmusic.data.api.dto.RecommendSongsResponse
import one.tunkshif.swirlmusic.data.api.dto.PlaylistDto
import one.tunkshif.swirlmusic.data.api.dto.TrackDto
import one.tunkshif.swirlmusic.data.model.ArtistRef
import org.junit.Test

/**
 * DTO to domain, against fixtures shaped like real payloads. NetEase renames fields between
 * endpoints (`playcount` here, `playCount` there) and uses short track keys, so this is the layer
 * that keeps the UI honest.
 */
class HomeMappersTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun `a track keeps its artists, album art, and duration`() {
        val response = json.decodeFromString<RecommendSongsResponse>(fixture("recommend_songs.json"))
        val track = response.data.dailySongs.first().toDomain()

        assertThat(track.title).isEqualTo("Nightcall")
        assertThat(track.artistLine).isEqualTo("Kavinsky")
        assertThat(track.durationMs).isEqualTo(258000)
        assertThat(track.artworkUrl)
            .isEqualTo("https://p1.music.126.net/cover-1001.jpg?param=300y300")
    }

    @Test
    fun `several artists are joined into one line`() {
        val response = json.decodeFromString<RecommendSongsResponse>(fixture("recommend_songs.json"))

        assertThat(response.data.dailySongs[1].toDomain().artistLine).isEqualTo("M83 / Guest")
    }

    @Test
    fun `a track keeps artist ids and names in order`() {
        val response = json.decodeFromString<RecommendSongsResponse>(fixture("recommend_songs.json"))

        assertThat(response.data.dailySongs[1].toDomain().artists)
            .containsExactly(ArtistRef(502, "M83"), ArtistRef(503, "Guest"))
            .inOrder()
    }

    @Test
    fun `a zero artist id is kept as sent`() {
        val dto = json.decodeFromString<TrackDto>(
            """
            {
              "id": 9,
              "name": "Song",
              "ar": [{"id": 0, "name": "Unknown"}, {"id": 7, "name": "Known"}],
              "dt": 1000
            }
            """.trimIndent(),
        )

        assertThat(dto.toDomain().artists)
            .containsExactly(ArtistRef(0, "Unknown"), ArtistRef(7, "Known"))
            .inOrder()
    }

    @Test
    fun `play counts are read from either spelling`() {
        val lower = json.decodeFromString<PlaylistDto>(
            """{"id":1,"name":"A","playcount":1234567}""",
        )
        val camel = json.decodeFromString<PlaylistDto>(
            """{"id":1,"name":"A","playCount":987000}""",
        )

        assertThat(lower.toDomain().playCount).isEqualTo(1234567)
        assertThat(camel.toDomain().playCount).isEqualTo(987000)
    }

    @Test
    fun `a track keeps its album id and name`() {
        val response = json.decodeFromString<RecommendSongsResponse>(fixture("recommend_songs.json"))
        val track = response.data.dailySongs.first().toDomain()

        assertThat(track.albumId).isEqualTo(601)
        assertThat(track.albumName).isEqualTo("OutRun")
    }

    @Test
    fun `a song-detail-style track reads the album from the album key`() {
        // `/song/detail` spells the album object `album`, not `al`.
        val dto = json.decodeFromString<one.tunkshif.swirlmusic.data.api.dto.TrackDto>(
            """
            {
              "id": 42,
              "name": "Song",
              "artists": [{"id": 1, "name": "Artist"}],
              "album": {"id": 77, "name": "The Album", "picUrl": "http://p1.example/c.jpg"},
              "dt": 1000
            }
            """.trimIndent(),
        )
        val track = dto.toDomain()

        assertThat(track.albumId).isEqualTo(77)
        assertThat(track.albumName).isEqualTo("The Album")
    }

    @Test
    fun `artwork sizing keeps an existing query string`() {
        assertThat(sizedArtwork("https://host/a.jpg?x=1", 64))
            .isEqualTo("https://host/a.jpg?x=1&param=64y64")
        assertThat(sizedArtwork("http://host/a.jpg", 64))
            .isEqualTo("https://host/a.jpg?param=64y64")
    }

    @Test
    fun `resized artwork replaces an existing param`() {
        assertThat(resizedArtwork("https://host/a.jpg", 1000))
            .isEqualTo("https://host/a.jpg?param=1000y1000")
        assertThat(resizedArtwork("https://host/a.jpg?param=300y300", 1000))
            .isEqualTo("https://host/a.jpg?param=1000y1000")
        assertThat(resizedArtwork("https://host/a.jpg?foo=1&param=300y300", 1000))
            .isEqualTo("https://host/a.jpg?foo=1&param=1000y1000")
        assertThat(resizedArtwork("http://host/a.jpg?param=300y300", 1000))
            .isEqualTo("https://host/a.jpg?param=1000y1000")
    }

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("fixtures/ncm/$name")) {
            "missing fixture $name"
        }.readText()
}
