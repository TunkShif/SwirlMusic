package one.tunkshif.swirlmusic.ui.library

import com.google.common.truth.Truth.assertThat
import one.tunkshif.swirlmusic.data.model.PlaylistSummary
import org.junit.Test

class PlaylistSectionsTest {
    private fun p(id: Long, liked: Boolean = false, saved: Boolean = false) =
        PlaylistSummary(id, "p$id", null, 0, 0, subscribed = saved, isLiked = liked)

    @Test
    fun `liked is pulled out of created and order is kept`() {
        val sections = playlistSections(listOf(p(1, liked = true), p(2), p(3), p(4, saved = true), p(5, saved = true)), complete = true)
        assertThat(sections.liked?.id).isEqualTo(1)
        assertThat(sections.created.map { it.id }).containsExactly(2L, 3L).inOrder()
        assertThat(sections.saved.map { it.id }).containsExactly(4L, 5L).inOrder()
        assertThat(sections.createdCount).isEqualTo(2)
        assertThat(sections.savedCount).isEqualTo(2)
    }

    @Test
    fun `counts stay null until the list is complete`() {
        val sections = playlistSections(listOf(p(1, liked = true), p(2)), complete = false)
        assertThat(sections.createdCount).isNull()
        assertThat(sections.savedCount).isNull()
    }

    @Test
    fun `empty sections stay empty`() {
        val sections = playlistSections(listOf(p(1)), complete = true)
        assertThat(sections.liked).isNull()
        assertThat(sections.saved).isEmpty()
    }
}
