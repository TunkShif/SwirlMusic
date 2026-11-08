package one.tunkshif.swirlmusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import one.tunkshif.swirlmusic.R
import one.tunkshif.swirlmusic.data.model.ArtistRef

private val ArtistAvatarSize = 32.dp
private val ArtistAvatarIconSize = 20.dp

/**
 * The artist line of a detail header: plain text when there are no artist refs, a tap target
 * for one artist, and the choose-artist menu (decision 0009-D34) for several. [avatars] maps
 * artist id to a small image; ids without one get a person glyph.
 */
@Composable
fun ArtistLine(
    text: String,
    artists: List<ArtistRef>,
    avatars: Map<Long, String?>,
    onOpenArtist: (Long) -> Unit,
    style: TextStyle,
    color: Color,
    maxLines: Int,
    modifier: Modifier = Modifier,
) {
    val line = @Composable { m: Modifier ->
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = m,
        )
    }
    when (artists.size) {
        0 -> line(modifier)
        1 -> line(
            modifier.clickable(
                onClickLabel = stringResource(R.string.playing_open_artist),
                role = Role.Button,
            ) { onOpenArtist(artists[0].id) },
        )
        else -> {
            var expanded by rememberSaveable(artists) { mutableStateOf(false) }
            Box(modifier) {
                line(
                    Modifier.clickable(
                        onClickLabel = stringResource(R.string.playing_choose_artist),
                        role = Role.Button,
                    ) { expanded = true },
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    shape = MaterialTheme.shapes.large,
                ) {
                    artists.forEach { artist ->
                        DropdownMenuItem(
                            text = { Text(artist.name) },
                            leadingIcon = {
                                ArtistAvatar(avatarUrl = avatars[artist.id])
                            },
                            onClick = {
                                expanded = false
                                onOpenArtist(artist.id)
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Menu leading slot: the artist's avatar, or a person glyph on surfaceVariant. */
@Composable
private fun ArtistAvatar(avatarUrl: String?) {
    if (avatarUrl != null) {
        Artwork(
            url = avatarUrl,
            contentDescription = null,
            shape = CircleShape,
            modifier = Modifier.size(ArtistAvatarSize),
        )
    } else {
        Box(
            modifier = Modifier
                .size(ArtistAvatarSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_person),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(ArtistAvatarIconSize),
            )
        }
    }
}
