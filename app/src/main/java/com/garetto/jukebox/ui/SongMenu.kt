package com.garetto.jukebox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.garetto.jukebox.data.Song
import com.garetto.jukebox.data.UserData

/**
 * Pop-up for one song: favorite it and add it to (or remove it from) playlists.
 * [inPlaylist] is the playlist being viewed, if any.
 */
@Composable
fun SongMenu(song: Song, inPlaylist: Long?, onDismiss: () -> Unit) {
    val user by UserData.state.collectAsState()
    var naming by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(26.dp)
    val favorite = song.id in user.favorites

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Jx.CardHi)
                .border(
                    1.dp,
                    Brush.linearGradient(listOf(Jx.Violet.copy(alpha = 0.8f), Jx.Pink.copy(alpha = 0.4f))),
                    shape,
                )
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ArtBox(song, Modifier.size(50.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        song.title,
                        color = Jx.Ink,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(song.artist, color = Jx.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(12.dp))

            MenuRow(
                if (favorite) Glyph.HeartFilled else Glyph.Heart,
                if (favorite) "Remove from favorites" else "Add to favorites",
                tint = Jx.Pink,
            ) { UserData.toggleFavorite(song.id) }

            if (inPlaylist != null) {
                MenuRow(Glyph.Close, "Remove from this playlist") {
                    UserData.removeFromPlaylist(inPlaylist, song.id)
                    onDismiss()
                }
            }

            Spacer(Modifier.height(10.dp))
            MonoLabel("PLAYLISTS", Modifier.padding(start = 8.dp), color = Jx.Pink)
            Spacer(Modifier.height(4.dp))
            user.playlists.forEach { p ->
                val inIt = song.id in p.songIds
                MenuRow(
                    if (inIt) Glyph.Check else Glyph.Plus,
                    p.name,
                    tint = if (inIt) Jx.Pink else Jx.Ink,
                ) {
                    if (inIt) UserData.removeFromPlaylist(p.id, song.id) else UserData.addToPlaylist(p.id, song.id)
                }
            }
            MenuRow(Glyph.Plus, "New playlist…", tint = Jx.Muted) { naming = true }

            Spacer(Modifier.height(8.dp))
            Text(
                "Done",
                color = Jx.Pink,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier
                    .align(Alignment.End)
                    .bounceClick(onDismiss)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }

    if (naming) {
        NameDialog(
            title = "New playlist",
            label = "Playlist name",
            onDismiss = { naming = false },
            onSave = { name ->
                val id = UserData.createPlaylist(name)
                UserData.addToPlaylist(id, song.id)
                naming = false
            },
        )
    }
}

@Composable
private fun MenuRow(glyph: Glyph, text: String, tint: Color = Jx.Ink, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(glyph, tint, Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(text, color = Jx.Ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
