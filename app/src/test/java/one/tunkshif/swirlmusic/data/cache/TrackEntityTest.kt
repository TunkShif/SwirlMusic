package one.tunkshif.swirlmusic.data.cache

import com.google.common.truth.Truth.assertThat
import one.tunkshif.swirlmusic.data.model.ArtistRef
import one.tunkshif.swirlmusic.data.model.Track
import org.junit.Test

/** The artists column is a JSON string; this pins its encode/decode edge cases. */
class TrackEntityTest {

    private val track = Track(
        id = 1,
        title = "Nightcall",
        artistLine = "Kavinsky",
        artworkUrl = null,
        durationMs = 258_000,
    )

    @Test
    fun `artists survive the entity round trip`() {
        val artists = listOf(ArtistRef(501, "Kavinsky"), ArtistRef(502, "M83"))

        val restored = TrackEntity.of(track.copy(artists = artists), now = 1_000).toTrack()

        assertThat(restored.artists).containsExactlyElementsIn(artists).inOrder()
    }

    @Test
    fun `an empty artist list is stored as null and reads back empty`() {
        val entity = TrackEntity.of(track, now = 1_000)

        assertThat(entity.artists).isNull()
        assertThat(entity.toTrack().artists).isEmpty()
    }

    @Test
    fun `a null artists column decodes to an empty list`() {
        val entity = TrackEntity.of(track, now = 1_000).copy(artists = null)

        assertThat(entity.toTrack().artists).isEmpty()
    }

    @Test
    fun `a malformed artists column decodes to an empty list`() {
        val entity = TrackEntity.of(track, now = 1_000).copy(artists = "not json")

        assertThat(entity.toTrack().artists).isEmpty()
    }
}
