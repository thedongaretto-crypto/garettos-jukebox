package com.garetto.jukebox.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garetto.jukebox.audio.LoopMode
import com.garetto.jukebox.audio.PlayerEngine
import com.garetto.jukebox.audio.PlayerState
import com.garetto.jukebox.data.Song
import com.garetto.jukebox.data.UserData

@Composable
fun NowPlayingScreen(state: PlayerState, onClose: () -> Unit, onOpenLab: () -> Unit) {
    val song = state.song
    val user by UserData.state.collectAsState()
    val isFavorite = song != null && song.id in user.favorites
    var menuOpen by remember { mutableStateOf(false) }
    if (menuOpen && song != null) {
        SongMenu(song, inPlaylist = null, onDismiss = { menuOpen = false })
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Jx.Bg)
            .blockTouches()
    ) {
        AuroraBackground(intensity = 1.35f)

        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top bar
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBtn(Glyph.Down, onClose)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    MonoLabel("NOW PLAYING", color = Jx.Pink)
                    Text(
                        song?.album ?: "",
                        color = Jx.Muted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconBtn(Glyph.Sliders, onOpenLab)
            }

            // Spinning record. Swipe down on it to close the player.
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        var total = 0f
                        detectVerticalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = { if (total > 90.dp.toPx()) onClose() },
                        ) { _, dy -> total += dy }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Vinyl(song, state.isPlaying, Modifier.padding(6.dp).aspectRatio(1f))
            }

            VizBars(
                playing = state.isPlaying,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
            )
            Spacer(Modifier.height(16.dp))

            // Title slides in when the song changes
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(
                if (isFavorite) Glyph.HeartFilled else Glyph.Heart,
                { song?.let { UserData.toggleFavorite(it.id) } },
                tint = if (isFavorite) Jx.Pink else Jx.Muted,
            )
            AnimatedContent(
                targetState = song,
                transitionSpec = {
                    (slideInVertically(tween(320)) { it / 2 } + fadeIn(tween(320))) togetherWith
                        (slideOutVertically(tween(240)) { -it / 2 } + fadeOut(tween(200)))
                },
                contentKey = { it?.id },
                label = "title",
                modifier = Modifier.weight(1f),
            ) { s ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        s?.title ?: "",
                        color = Jx.Ink,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        s?.artist ?: "",
                        color = Jx.Pink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            IconBtn(Glyph.Plus, { menuOpen = true }, tint = Jx.Muted)
            }
            Spacer(Modifier.height(10.dp))

            SeekBar(state)
            Spacer(Modifier.height(6.dp))

            // Controls
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBtn(
                    Glyph.Shuffle,
                    { PlayerEngine.toggleShuffle() },
                    tint = if (state.shuffle) Jx.Pink else Jx.Muted,
                    size = 48.dp,
                )
                IconBtn(Glyph.Prev, { PlayerEngine.prev() }, size = 60.dp)
                PlayButton(state.isPlaying) { PlayerEngine.toggle() }
                IconBtn(Glyph.Next, { PlayerEngine.next() }, size = 60.dp)
                IconBtn(
                    if (state.loop == LoopMode.ONE) Glyph.LoopOne else Glyph.Loop,
                    { PlayerEngine.cycleLoop() },
                    tint = if (state.loop == LoopMode.OFF) Jx.Muted else Jx.Pink,
                    size = 48.dp,
                )
            }
            Spacer(Modifier.height(14.dp))
            Chip("Audio Lab  ·  EQ, bass, crossfade", selected = false, onClick = onOpenLab)
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun SeekBar(state: PlayerState) {
    val pos by PlayerEngine.position.collectAsState()
    var dragging by remember { mutableStateOf<Float?>(null) }
    val dur = state.durationMs.coerceAtLeast(1L)
    val live = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
    val shownFrac = dragging ?: live
    Column(Modifier.fillMaxWidth()) {
        NeonSlider(
            value = shownFrac,
            onChange = { dragging = it },
            onDone = {
                dragging?.let { PlayerEngine.seekTo((it * dur).toLong()) }
                dragging = null
            },
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Text(formatTime((shownFrac * dur).toLong()), color = Jx.Muted, fontSize = 12.sp, fontFamily = Jx.Mono)
            Spacer(Modifier.weight(1f))
            Text(formatTime(state.durationMs), color = Jx.Muted, fontSize = 12.sp, fontFamily = Jx.Mono)
        }
    }
}

@Composable
private fun PlayButton(playing: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "playGlow")
    val pulse by transition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    val glow by animateFloatAsState(if (playing) 1f else 0.45f, tween(400), label = "glow")
    Box(
        Modifier
            .size(80.dp)
            .bounceClick(onClick)
            .drawBehind {
                val r = size.minDimension * (0.72f + 0.22f * pulse * glow)
                drawCircle(
                    Brush.radialGradient(
                        listOf(Jx.Pink.copy(alpha = 0.55f * glow), Jx.Violet.copy(alpha = 0.2f * glow), Color.Transparent),
                        center = center,
                        radius = r,
                    ),
                    radius = r,
                )
            }
            .background(Jx.NeonDiagonal, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = playing,
            transitionSpec = {
                (scaleIn(spring(dampingRatio = 0.5f, stiffness = 500f), initialScale = 0.4f) + fadeIn()) togetherWith
                    (scaleOut(tween(120), targetScale = 0.4f) + fadeOut(tween(120)))
            },
            label = "playPause",
        ) { p ->
            GlyphIcon(if (p) Glyph.Pause else Glyph.Play, Color.White, Modifier.size(34.dp))
        }
    }
}

/** A record that spins while music plays, with the album cover as its label. */
@Composable
private fun Vinyl(song: Song?, playing: Boolean, modifier: Modifier = Modifier) {
    val angle = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing) {
        if (playing) {
            var last = 0L
            while (true) {
                withFrameNanos { now ->
                    if (last != 0L) {
                        angle.floatValue = (angle.floatValue + (now - last) / 1_000_000_000f * 36f) % 360f
                    }
                    last = now
                }
            }
        }
    }
    val transition = rememberInfiniteTransition(label = "vinylGlow")
    val pulse by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    val scale by animateFloatAsState(
        targetValue = if (playing) 1f else 0.88f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 180f),
        label = "vinylScale",
    )
    val energy by animateFloatAsState(if (playing) 1f else 0.35f, tween(600), label = "energy")

    Box(
        modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        contentAlignment = Alignment.Center,
    ) {
        // Halo
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            drawCircle(
                Brush.radialGradient(
                    0.55f to Jx.Pink.copy(alpha = 0.6f * pulse * energy),
                    0.8f to Jx.Violet.copy(alpha = 0.3f * energy),
                    1f to Color.Transparent,
                    center = center,
                    radius = r,
                ),
                radius = r,
            )
        }
        // Record
        Box(
            Modifier
                .fillMaxSize(0.84f)
                .graphicsLayer { rotationZ = angle.floatValue },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                drawCircle(Color(0xFF0B0712), radius = r)
                for (i in 0..13) {
                    drawCircle(
                        Color.White.copy(alpha = if (i % 3 == 0) 0.09f else 0.045f),
                        radius = r * (0.54f + i * 0.033f),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
                drawCircle(
                    Brush.sweepGradient(
                        listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.13f),
                            Color.Transparent,
                            Color.Transparent,
                            Jx.Pink.copy(alpha = 0.12f),
                            Color.Transparent,
                            Color.Transparent,
                        ),
                        center = center,
                    ),
                    radius = r,
                )
                drawCircle(
                    Brush.sweepGradient(listOf(Jx.Violet, Jx.Pink, Jx.Violet), center = center),
                    radius = r - 1.dp.toPx(),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
            ArtBox(song, Modifier.fillMaxSize(0.5f), shape = CircleShape, px = 512)
            Box(
                Modifier
                    .size(12.dp)
                    .background(Jx.Bg, CircleShape)
            )
        }
    }
}
