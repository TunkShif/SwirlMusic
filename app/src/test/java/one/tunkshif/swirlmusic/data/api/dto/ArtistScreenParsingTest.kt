package one.tunkshif.swirlmusic.data.api.dto

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import one.tunkshif.swirlmusic.data.api.releaseKindOf
import one.tunkshif.swirlmusic.data.api.toDescription
import one.tunkshif.swirlmusic.data.api.toRelease
import one.tunkshif.swirlmusic.data.model.DescriptionSection
import one.tunkshif.swirlmusic.data.model.ReleaseKind
import org.junit.Test

/** `/artists`, `/artist/album` and `/artist/desc` against the trimmed real fixtures. */
class ArtistScreenParsingTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `artists carries the header fields and counts`() {
        val response = json.decodeFromString<ArtistDetailResponse>(fixture("artists.json"))

        assertThat(response.artist.name).isNotEmpty()
        assertThat(response.artist.picUrl ?: response.artist.img1v1Url).isNotNull()
        assertThat(response.artist.briefDesc).isNotEmpty()
        assertThat(response.artist.musicSize).isGreaterThan(0)
        assertThat(response.artist.albumSize).isGreaterThan(0)
        assertThat(response.hotSongs).hasSize(3)
    }

    @Test
    fun `a zero size maps to a null count`() {
        val response = ArtistDetailResponse(
            artist = ArtistDto(id = 1, name = "A", musicSize = 0, albumSize = 0),
        )
        // The NcmApi mapping rule: a size of 0 means null.
        assertThat(response.artist.musicSize.takeIf { it > 0 }).isNull()
        assertThat(response.artist.albumSize.takeIf { it > 0 }).isNull()
    }

    @Test
    fun `artist album items map to releases`() {
        val response = json.decodeFromString<ArtistAlbumsResponse>(fixture("artist_album.json"))

        assertThat(response.more).isFalse()
        assertThat(response.hotAlbums).hasSize(5)

        val releases = response.hotAlbums.mapNotNull { it.toRelease() }
        assertThat(releases).hasSize(5)
        assertThat(releases.map { it.kind }).containsExactly(
            ReleaseKind.Album,
            ReleaseKind.Ep,
            ReleaseKind.Single,
            ReleaseKind.Album,
            ReleaseKind.Single,
        ).inOrder()
        assertThat(releases.first().publishedAt).isGreaterThan(0L)
        assertThat(releases.first().trackCount).isGreaterThan(0)
        assertThat(releases.first().artworkUrl).contains("param=")
    }

    @Test
    fun `releaseKindOf maps the documented table`() {
        assertThat(releaseKindOf("专辑")).isEqualTo(ReleaseKind.Album)
        assertThat(releaseKindOf("EP")).isEqualTo(ReleaseKind.Ep)
        assertThat(releaseKindOf("EP/Single")).isEqualTo(ReleaseKind.Ep)
        assertThat(releaseKindOf("Single")).isEqualTo(ReleaseKind.Single)
        assertThat(releaseKindOf(" 专辑 ")).isEqualTo(ReleaseKind.Album)
        assertThat(releaseKindOf("精选集")).isEqualTo(ReleaseKind.Other)
        assertThat(releaseKindOf(null)).isEqualTo(ReleaseKind.Other)
    }

    @Test
    fun `a release with id zero is dropped`() {
        assertThat(AlbumDto(id = 0, name = "Ghost", type = "专辑").toRelease()).isNull()
        assertThat(AlbumDto(id = 1, name = "A", type = "精选集").toRelease()!!.kind)
            .isEqualTo(ReleaseKind.Other)
    }

    @Test
    fun `artist desc maps sections`() {
        val response = json.decodeFromString<ArtistDescResponse>(fixture("artist_desc.json"))

        val description = response.toDescription()
        assertThat(description.brief).isNotEmpty()
        assertThat(description.sections).hasSize(2)
        assertThat(description.sections.first().title).isNotEmpty()
        assertThat(description.sections.first().body).isNotEmpty()
    }

    @Test
    fun `blank sections are dropped and a blank brief is null`() {
        val response = ArtistDescResponse(
            briefDesc = "  ",
            introduction = listOf(
                IntroDto(ti = " one ", txt = " body "),
                IntroDto(ti = "two", txt = " "),
            ),
        )

        assertThat(response.toDescription()).isEqualTo(
            one.tunkshif.swirlmusic.data.model.ArtistDescription(
                brief = null,
                sections = listOf(DescriptionSection("one", "body")),
            ),
        )
        assertThat(ArtistDescResponse().toDescription().sections).isEmpty()
    }

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("fixtures/ncm/$name")) {
            "missing fixture $name"
        }.readText()
}
