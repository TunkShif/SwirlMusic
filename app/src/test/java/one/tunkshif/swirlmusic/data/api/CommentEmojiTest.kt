package one.tunkshif.swirlmusic.data.api

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** D67: `[name]` becomes its emoji only when the name is in the table. */
class CommentEmojiTest {

    @Test
    fun `a known token is replaced`() {
        assertThat(CommentEmoji.mapCommentEmoji("好听[爱心]")).isEqualTo("好听❤️")
        assertThat(CommentEmoji.mapCommentEmoji("[爆笑]")).isEqualTo("🤣")
    }

    @Test
    fun `several tokens in one string are all replaced`() {
        assertThat(CommentEmoji.mapCommentEmoji("[爱心]加油[加油]"))
            .isEqualTo("❤️加油💪")
    }

    @Test
    fun `unknown tokens stay as text`() {
        assertThat(CommentEmoji.mapCommentEmoji("[cp]")).isEqualTo("[cp]")
        assertThat(CommentEmoji.mapCommentEmoji("[foo bar]")).isEqualTo("[foo bar]")
    }

    @Test
    fun `an unclosed bracket is untouched`() {
        assertThat(CommentEmoji.mapCommentEmoji("[爱心")).isEqualTo("[爱心")
    }

    @Test
    fun `text without tokens is identical`() {
        assertThat(CommentEmoji.mapCommentEmoji("plain comment")).isEqualTo("plain comment")
    }
}
