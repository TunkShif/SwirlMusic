package one.tunkshif.swirlmusic.data.api.dto

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import one.tunkshif.swirlmusic.data.api.avatarUrl
import org.junit.Test

/**
 * Contract test for `/artist/detail`: the profile carries a square `avatar` and a wide
 * `cover`; the menu wants avatar first, cover as fallback, at menu size. Trimmed real payload.
 */
class ArtistProfileParsingTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `the avatar maps to a sized https url`() {
        val response = json.decodeFromString<ArtistProfileResponse>(fixture("artist_detail.json"))

        assertThat(response.data.artist.avatar).isNotEmpty()
        assertThat(response.avatarUrl())
            .isEqualTo("https://p4.music.126.net/4egsgJXURoPcBNjj00YQHQ==/109951170045684137.jpg?param=120y120")
    }

    @Test
    fun `a blank or missing avatar falls back to the cover`() {
        val cover = "http://p3.music.126.net/abc/109951170045683199.jpg"
        val blankAvatar = ArtistProfileResponse(
            data = ArtistProfileData(ArtistProfileDto(avatar = " ", cover = cover)),
        )
        val noAvatar = ArtistProfileResponse(
            data = ArtistProfileData(ArtistProfileDto(avatar = null, cover = cover)),
        )

        assertThat(blankAvatar.avatarUrl())
            .isEqualTo("https://p3.music.126.net/abc/109951170045683199.jpg?param=120y120")
        assertThat(noAvatar.avatarUrl()).isEqualTo(blankAvatar.avatarUrl())
    }

    @Test
    fun `neither image means no avatar`() {
        assertThat(ArtistProfileResponse().avatarUrl()).isNull()
        val blank = ArtistProfileResponse(
            data = ArtistProfileData(ArtistProfileDto(avatar = "", cover = "")),
        )
        assertThat(blank.avatarUrl()).isNull()
    }

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("fixtures/ncm/$name")) {
            "missing fixture $name"
        }.readText()
}
