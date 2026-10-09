package com.garetto.jukebox.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000L).toInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Tap handler that makes the element squish and spring back. */
fun Modifier.bounceClick(onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 700f),
        label = "press",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}

/** Stops taps from reaching whatever is underneath a full-screen layer. */
fun Modifier.blockTouches(): Modifier = pointerInput(Unit) { detectTapGestures { } }

private val stars: List<FloatArray> = Random(999).let { r ->
    List(80) { floatArrayOf(r.nextFloat(), r.nextFloat(), r.nextFloat()) }
}

/** Slow-drifting neon clouds with twinkling stars. */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier, intensity: Float = 1f) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val a by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(22000, easing = LinearEasing)),
        label = "drift",
    )
    Canvas(modifier.fillMaxSize()) {
        drawRect(Jx.Bg)
        val w = size.width
        val h = size.height

        fun blob(color: Color, cx: Float, cy: Float, r: Float, alpha: Float) {
            val c = Offset(cx, cy)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = (alpha * intensity).coerceIn(0f, 1f)), Color.Transparent),
                    center = c,
                    radius = r,
                ),
                radius = r,
                center = c,
            )
        }

        blob(Jx.Violet, w * (0.2f + 0.2f * cos(a)), h * (0.16f + 0.08f * sin(a)), w * 0.95f, 0.5f)
        blob(Jx.Pink, w * (0.85f + 0.15f * sin(a * 2f)), h * (0.52f + 0.14f * cos(a)), w * 0.8f, 0.32f)
        blob(Jx.Blue, w * (0.3f + 0.22f * sin(a)), h * (0.92f + 0.07f * cos(a * 2f)), w * 0.95f, 0.42f)

        for (s in stars) {
            val twinkle = 0.12f + 0.6f * abs(sin(a * 3f + s[2] * 40f))
            drawCircle(
                color = Color.White.copy(alpha = twinkle),
                radius = (0.6f + s[2] * 1.2f) * density,
                center = Offset(s[0] * w, s[1] * h),
            )
        }
    }
}

/** Title with a pink/cyan ghost that glitches every few seconds. */
@Composable
fun GlitchTitle(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    val jitter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(1600L, 4200L))
            repeat(4) {
                jitter.snapTo(Random.nextFloat() * 2f - 1f)
                delay(55L)
            }
            jitter.snapTo(0f)
        }
    }
    Box(modifier) {
        Text(
            text,
            style = style,
            color = Jx.Pink.copy(alpha = 0.85f),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.graphicsLayer {
                translationX = (2f + 7f * jitter.value) * density
                translationY = 1f * density
            },
        )
        Text(
            text,
            style = style,
            color = Jx.Cyan.copy(alpha = 0.7f),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.graphicsLayer {
                translationX = (-2f - 6f * jitter.value) * density
                translationY = -1f * density
            },
        )
        Text(text, style = style, color = Jx.Ink, maxLines = 1, softWrap = false)
    }
}

/** Frosted panel with a glowing edge. */
@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Jx.Card.copy(alpha = 0.8f))
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(Jx.Violet.copy(alpha = 0.55f), Jx.Pink.copy(alpha = 0.18f), Color.Transparent)
                ),
                shape,
            )
            .padding(18.dp),
        content = content,
    )
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val glow by animateFloatAsState(if (selected) 1f else 0f, tween(260), label = "chip")
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .bounceClick(onClick)
            .clip(shape)
            .background(Jx.CardHi)
            .background(Jx.Neon, alpha = glow)
            .border(1.dp, Jx.Violet.copy(alpha = 0.35f), shape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) Color.White else Jx.Muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
fun IconBtn(
    glyph: Glyph,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Jx.Ink,
    size: Dp = 44.dp,
) {
    Box(modifier.size(size).bounceClick(onClick), contentAlignment = Alignment.Center) {
        GlyphIcon(glyph, tint, Modifier.size(size * 0.52f))
    }
}

/**
 * Glowing slider. [value] runs 0..1. With [centered] the lit part grows
 * out from the middle (used for left/right balance).
 */
@Composable
fun NeonSlider(
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onDone: () -> Unit = {},
    centered: Boolean = false,
) {
    val change by rememberUpdatedState(onChange)
    val done by rememberUpdatedState(onDone)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(Unit) {
                val pad = 12.dp.toPx()
                detectTapGestures { o ->
                    change(((o.x - pad) / (size.width - 2f * pad)).coerceIn(0f, 1f))
                    done()
                }
            }
            .pointerInput(Unit) {
                val pad = 12.dp.toPx()
                detectHorizontalDragGestures(
                    onDragStart = { o -> change(((o.x - pad) / (size.width - 2f * pad)).coerceIn(0f, 1f)) },
                    onDragEnd = { done() },
                    onDragCancel = { done() },
                ) { c, _ ->
                    c.consume()
                    change(((c.position.x - pad) / (size.width - 2f * pad)).coerceIn(0f, 1f))
                }
            }
    ) {
        val pad = 12.dp.toPx()
        val y = size.height / 2f
        val x0 = pad
        val x1 = size.width - pad
        val v = value.coerceIn(0f, 1f)
        val tx = x0 + (x1 - x0) * v
        val from = if (centered) (x0 + x1) / 2f else x0

        drawLine(Jx.Muted.copy(alpha = 0.28f), Offset(x0, y), Offset(x1, y), 4.dp.toPx(), StrokeCap.Round)
        if (abs(tx - from) > 0.5f) {
            drawLine(
                Brush.horizontalGradient(listOf(Jx.Violet, Jx.Pink), startX = x0, endX = x1),
                Offset(from, y),
                Offset(tx, y),
                5.dp.toPx(),
                StrokeCap.Round,
            )
        }
        if (centered) {
            drawLine(
                Jx.Muted.copy(alpha = 0.6f),
                Offset(from, y - 6.dp.toPx()),
                Offset(from, y + 6.dp.toPx()),
                1.5.dp.toPx(),
            )
        }
        val glow = 16.dp.toPx()
        drawCircle(
            Brush.radialGradient(
                listOf(Jx.Pink.copy(alpha = 0.6f), Color.Transparent),
                center = Offset(tx, y),
                radius = glow,
            ),
            radius = glow,
            center = Offset(tx, y),
        )
        drawCircle(Color.White, radius = 6.5.dp.toPx(), center = Offset(tx, y))
    }
}

/** Seconds counter that only runs while [running] is true. */
@Composable
private fun rememberRunningClock(running: Boolean): () -> Float {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running) {
        if (running) {
            var last = 0L
            while (true) {
                withFrameNanos { now ->
                    if (last != 0L) time.floatValue += (now - last) / 1_000_000_000f
                    last = now
                }
            }
        }
    }
    return { time.floatValue }
}

/** Dancing bars under the record. Decorative: moves while music plays. */
@Composable
fun VizBars(playing: Boolean, modifier: Modifier = Modifier, bars: Int = 30) {
    val amp by animateFloatAsState(if (playing) 1f else 0.08f, tween(700), label = "amp")
    val clock = rememberRunningClock(playing)
    Canvas(modifier) {
        val t = clock()
        val slot = size.width / bars
        val bw = slot * 0.55f
        val brush = Brush.verticalGradient(listOf(Jx.Pink, Jx.Violet))
        for (i in 0 until bars) {
            val wave = abs(sin(t * (2.2f + (i % 5) * 0.7f) + i * 0.9f) * sin(t * 1.3f + i * 0.37f))
            val h = size.height * (0.1f + 0.9f * wave) * amp
            val barH = h.coerceAtLeast(bw * 0.6f)
            drawRoundRect(
                brush = brush,
                topLeft = Offset(slot * i + (slot - bw) / 2f, (size.height - barH) / 2f),
                size = Size(bw, barH),
                cornerRadius = CornerRadius(bw / 2f, bw / 2f),
                alpha = 0.55f + 0.45f * amp,
            )
        }
    }
}

/** Tiny three-bar indicator shown on the song that is currently playing. */
@Composable
fun PlayingBars(playing: Boolean, modifier: Modifier = Modifier) {
    val clock = rememberRunningClock(playing)
    Canvas(modifier.size(18.dp)) {
        val t = clock()
        val slot = size.width / 3f
        val bw = slot * 0.55f
        for (i in 0 until 3) {
            val wave = if (playing) abs(sin(t * (4f + i * 1.3f) + i * 1.7f)) else 0.25f
            val h = size.height * (0.25f + 0.75f * wave)
            drawRoundRect(
                color = Jx.Pink,
                topLeft = Offset(slot * i + (slot - bw) / 2f, size.height - h),
                size = Size(bw, h),
                cornerRadius = CornerRadius(bw / 2f, bw / 2f),
            )
        }
    }
}

/** Big pill button filled with the neon gradient and a moving shine. */
@Composable
fun NeonButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, glyph: Glyph? = null) {
    val transition = rememberInfiniteTransition(label = "shine")
    val sweep by transition.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep",
    )
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .bounceClick(onClick)
            .clip(shape)
            .background(Jx.Neon)
            .padding(horizontal = 22.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            val x = size.width * sweep
            drawLine(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent),
                    startX = x - 40.dp.toPx(),
                    endX = x + 40.dp.toPx(),
                ),
                Offset(x, -size.height),
                Offset(x - size.height, size.height * 2f),
                strokeWidth = 46.dp.toPx(),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (glyph != null) {
                GlyphIcon(glyph, Color.White, Modifier.size(18.dp))
                Box(Modifier.size(8.dp))
            }
            Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
        }
    }
}

@Composable
fun MonoLabel(text: String, modifier: Modifier = Modifier, color: Color = Jx.Muted) {
    Text(
        text,
        modifier = modifier,
        color = color,
        fontFamily = Jx.Mono,
        fontSize = 11.sp,
        letterSpacing = 2.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
    )
}

/** Small pop-up that asks for a name (new playlist, saved preset). */
@Composable
fun NameDialog(title: String, label: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Jx.CardHi,
        titleContentColor = Jx.Ink,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(30) },
                singleLine = true,
                label = { Text(label) },
            )
        },
        confirmButton = { TextButton(onClick = { onSave(name) }) { Text("Save", color = Jx.Pink) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Jx.Muted) } },
    )
}
