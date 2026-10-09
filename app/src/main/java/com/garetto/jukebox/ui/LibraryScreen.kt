package com.garetto.jukebox.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garetto.jukebox.audio.PlayerEngine
import com.garetto.jukebox.audio.PlayerState
import com.garetto.jukebox.data.MusicLibrary
import com.garetto.jukebox.data.Song
import com.garetto.jukebox.data.UserData
import kotlinx.coroutines.launch

private data class SongGroup(val key: String, val name: String, val subtitle: String, val songs: List<Song>)

private val tabNames = listOf("Songs", "Favorites", "Most played", "Playlists", "Albums", "Artists")
private const val T_SONGS = 0
private const val T_FAVORITES = 1
private const val T_TOP = 2
private const val T_PLAYLISTS = 3
private const val T_ALBUMS = 4
private const val T_ARTISTS = 5

private fun countLabel(n: Int) = if (n == 1) "1 song" else "$n songs"

@Composable
fun LibraryScreen(state: PlayerState, onOpenPlayer: () -> Unit, onOpenLab: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val songs by MusicLibrary.songs.collectAsState()
    val loading by MusicLibrary.loading.collectAsState()
    val user by UserData.state.collectAsState()

    var tab by rememberSaveable { mutableStateOf(T_SONGS) }
    var query by rememberSaveable { mutableStateOf("") }
    // The album name, artist name or playlist id that is currently opened.
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    var menuSong by remember { mutableStateOf<Song?>(null) }
    var namingPlaylist by remember { mutableStateOf(false) }
    val q = query.trim()

    BackHandler(enabled = group != null) { group = null }

    val byId = remember(songs) { songs.associateBy { it.id } }
    val openPlaylist = if (tab == T_PLAYLISTS) user.playlists.firstOrNull { it.id.toString() == group } else null

    val shown: List<Song> = remember(songs, q, tab, group, user) {
        val g = group
        val base = when {
            tab == T_FAVORITES -> songs.filter { it.id in user.favorites }
            tab == T_TOP -> songs.filter { (user.plays[it.id] ?: 0) > 0 }
                .sortedByDescending { user.plays[it.id] ?: 0 }
                .take(100)
            tab == T_PLAYLISTS && g != null -> openPlaylist?.songIds?.mapNotNull { byId[it] } ?: emptyList()
            tab == T_ALBUMS && g != null -> songs.filter { it.album == g }.sortedBy { it.track }
            tab == T_ARTISTS && g != null -> songs.filter { it.artist == g }
            else -> songs
        }
        if (q.isEmpty()) base
        else base.filter {
            it.title.contains(q, ignoreCase = true) ||
                it.artist.contains(q, ignoreCase = true) ||
                it.album.contains(q, ignoreCase = true)
        }
    }
    val groups: List<SongGroup> = remember(songs, q, tab, user.playlists) {
        val all = when (tab) {
            T_PLAYLISTS -> user.playlists.map { p ->
                val list = p.songIds.mapNotNull { byId[it] }
                SongGroup(p.id.toString(), p.name, countLabel(list.size), list)
            }
            T_ALBUMS -> songs.groupBy { it.album }.map { (name, list) ->
                SongGroup(name, name, list.first().artist, list)
            }.sortedBy { it.name.lowercase() }
            T_ARTISTS -> songs.groupBy { it.artist }.map { (name, list) ->
                SongGroup(name, name, countLabel(list.size), list)
            }.sortedBy { it.name.lowercase() }
            else -> emptyList()
        }
        all.filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) }
    }
    val groupTab = tab == T_PLAYLISTS || tab == T_ALBUMS || tab == T_ARTISTS
    val showGroups = groupTab && group == null
    val groupTitle = if (tab == T_PLAYLISTS) openPlaylist?.name ?: "" else group ?: ""

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
    ) {
        // Header
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 10.dp, top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                MonoLabel("9 9 9  ·  ${songs.size} TRACKS", color = Jx.Pink)
                Spacer(Modifier.height(4.dp))
                GlitchTitle(
                    "GARETTO'S JUKEBOX",
                    TextStyle(fontSize = 25.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp),
                )
            }
            IconBtn(Glyph.Sliders, onOpenLab, tint = Jx.Ink, size = 48.dp)
        }

        // Search
        Row(
            Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(Jx.Card.copy(alpha = 0.85f))
                .border(1.dp, Jx.Violet.copy(alpha = 0.35f), RoundedCornerShape(50))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphIcon(Glyph.Search, Jx.Muted, Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = Jx.Ink, fontSize = 15.sp),
                cursorBrush = SolidColor(Jx.Pink),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) {
                            Text("Search songs, artists, albums", color = Jx.Muted, fontSize = 15.sp, maxLines = 1)
                        }
                        inner()
                    }
                },
            )
            if (query.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Box(Modifier.bounceClick { query = "" }) {
                    GlyphIcon(Glyph.Close, Jx.Muted, Modifier.size(16.dp))
                }
            }
        }

        // Tabs (swipe sideways for more)
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tabNames.forEachIndexed { i, name ->
                Chip(name, selected = tab == i, onClick = {
                    tab = i
                    group = null
                })
            }
        }
        Spacer(Modifier.height(8.dp))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                songs.isEmpty() -> EmptyLibrary(loading) {
                    scope.launch { MusicLibrary.refresh(context) }
                }

                showGroups -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    if (tab == T_PLAYLISTS) {
                        item(key = "newPlaylist") {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                NeonButton("New playlist", { namingPlaylist = true }, glyph = Glyph.Plus)
                            }
                        }
                        if (groups.isEmpty()) {
                            item(key = "noLists") {
                                Hint("No playlists yet. Make one here, or tap the three dots next to any song.")
                            }
                        }
                    }
                    items(groups, key = { "g:" + it.key }) { g ->
                        GroupRow(g, round = tab == T_ARTISTS, modifier = Modifier.animateItem()) { group = g.key }
                    }
                }

                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    item(key = "header") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (group != null) {
                                IconBtn(Glyph.Back, { group = null }, size = 40.dp)
                                Text(
                                    groupTitle,
                                    color = Jx.Ink,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(end = 10.dp),
                                )
                                if (openPlaylist != null) {
                                    Text(
                                        "Delete",
                                        color = Jx.Muted,
                                        fontSize = 13.sp,
                                        modifier = Modifier
                                            .bounceClick {
                                                UserData.deletePlaylist(openPlaylist.id)
                                                group = null
                                            }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                    )
                                }
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                            if (shown.isNotEmpty()) {
                                NeonButton("Shuffle", { PlayerEngine.shuffleAll(shown) }, glyph = Glyph.Shuffle)
                            }
                        }
                    }
                    itemsIndexed(shown, key = { _, s -> s.id }) { i, s ->
                        SongRow(
                            song = s,
                            current = state.song?.id == s.id,
                            playing = state.isPlaying,
                            favorite = s.id in user.favorites,
                            trailing = if (tab == T_TOP) {
                                val n = user.plays[s.id] ?: 0
                                if (n == 1) "1 play" else "$n plays"
                            } else {
                                formatTime(s.durationMs)
                            },
                            rank = if (tab == T_TOP && q.isEmpty()) i + 1 else 0,
                            modifier = Modifier.animateItem(),
                            onMore = { menuSong = s },
                        ) {
                            if (state.song?.id == s.id) onOpenPlayer() else PlayerEngine.play(shown, i)
                        }
                    }
                    if (shown.isEmpty()) {
                        item(key = "none") {
                            Hint(
                                when {
                                    q.isNotEmpty() -> "Nothing matches \"$q\""
                                    tab == T_FAVORITES -> "No favorites yet. Tap the three dots next to a song, or the heart on the player."
                                    tab == T_TOP -> "Your most listened songs will show up here once you have played some music."
                                    tab == T_PLAYLISTS -> "This playlist is empty. Tap the three dots next to any song to add it."
                                    else -> "Nothing here yet."
                                }
                            )
                        }
                    }
                }
            }
        }

        MiniPlayer(state, onOpenPlayer)
    }

    menuSong?.let { s ->
        SongMenu(s, inPlaylist = openPlaylist?.id, onDismiss = { menuSong = null })
    }
    if (namingPlaylist) {
        NameDialog(
            title = "New playlist",
            label = "Playlist name",
            onDismiss = { namingPlaylist = false },
            onSave = { name ->
                UserData.createPlaylist(name)
                namingPlaylist = false
            },
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        color = Jx.Muted,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 36.dp),
    )
}

@Composable
private fun EmptyLibrary(loading: Boolean, onRescan: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(36.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlyphIcon(Glyph.Note, Jx.Violet, Modifier.size(64.dp))
        Spacer(Modifier.height(18.dp))
        Text(
            if (loading) "Looking for music…" else "No music found on this phone yet",
            color = Jx.Ink,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        if (!loading) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Copy MP3, FLAC or M4A files into the phone's Music folder, then scan again.",
                color = Jx.Muted,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            NeonButton("Scan again", onRescan)
        }
    }
}

@Composable
private fun SongRow(
    song: Song,
    current: Boolean,
    playing: Boolean,
    favorite: Boolean,
    trailing: String,
    rank: Int,
    modifier: Modifier = Modifier,
    onMore: () -> Unit,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val highlight = if (current) {
        Brush.horizontalGradient(listOf(Jx.Violet.copy(alpha = 0.38f), Jx.Pink.copy(alpha = 0.08f)))
    } else {
        SolidColor(Color.Transparent)
    }
    Row(
        modifier
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(highlight)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (rank > 0) {
            Text(
                "$rank",
                color = if (rank <= 3) Jx.Pink else Jx.Muted,
                fontFamily = Jx.Mono,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(30.dp),
            )
        }
        ArtBox(song, Modifier.size(52.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (favorite) {
                    GlyphIcon(Glyph.HeartFilled, Jx.Pink, Modifier.size(12.dp))
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    song.title,
                    color = if (current) Color.White else Jx.Ink,
                    fontSize = 15.sp,
                    fontWeight = if (current) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                song.artist,
                color = Jx.Muted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        if (current) {
            PlayingBars(playing)
        } else {
            Text(trailing, color = Jx.Muted, fontSize = 12.sp, fontFamily = Jx.Mono, maxLines = 1)
        }
        IconBtn(Glyph.More, onMore, tint = Jx.Muted, size = 40.dp)
    }
}

@Composable
private fun GroupRow(g: SongGroup, round: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtBox(
            g.songs.firstOrNull(),
            Modifier.size(58.dp),
            shape = if (round) CircleShape else RoundedCornerShape(14.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                g.name,
                color = Jx.Ink,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(g.subtitle, color = Jx.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        MonoLabel("${g.songs.size}", color = Jx.Pink)
        Spacer(Modifier.width(8.dp))
    }
}

/** The bar at the bottom that shows what is playing. Tap it to open the full player. */
@Composable
private fun MiniPlayer(state: PlayerState, onOpen: () -> Unit) {
    val song = state.song ?: return
    val pos by PlayerEngine.position.collectAsState()
    val frac = if (state.durationMs > 0L) (pos.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Jx.CardHi)
            .border(
                1.dp,
                Brush.horizontalGradient(listOf(Jx.Violet.copy(alpha = 0.8f), Jx.Pink.copy(alpha = 0.5f))),
                shape,
            )
            .clickable(onClick = onOpen)
    ) {
        Row(
            Modifier.padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtBox(song, Modifier.size(46.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    color = Jx.Ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(song.artist, color = Jx.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconBtn(if (state.isPlaying) Glyph.Pause else Glyph.Play, { PlayerEngine.toggle() })
            IconBtn(Glyph.Next, { PlayerEngine.next() })
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(Jx.Muted.copy(alpha = 0.2f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(frac)
                    .fillMaxHeight()
                    .background(Jx.Neon)
            )
        }
    }
}
