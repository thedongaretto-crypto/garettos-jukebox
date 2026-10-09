package com.garetto.jukebox.ui

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garetto.jukebox.audio.AudioConfig
import com.garetto.jukebox.audio.AudioSettings
import com.garetto.jukebox.audio.EqPresets
import com.garetto.jukebox.audio.PlayerEngine
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val reverbNames = listOf(
    "Off", "Small room", "Medium room", "Large room", "Medium hall", "Large hall", "Plate",
)

/** All the sound controls: equalizer, bass, surround, reverb, crossfade and more. */
@Composable
fun AudioLabScreen(onBack: () -> Unit) {
    val cfg by AudioSettings.config.collectAsState()
    val player by PlayerEngine.state.collectAsState()
    val rack = PlayerEngine.rack
    val update: ((AudioConfig) -> AudioConfig) -> Unit = { AudioSettings.update(it) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Jx.Bg)
            .blockTouches()
    ) {
        AuroraBackground(intensity = 0.8f)

        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            // Top bar with the master switch
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 18.dp, top = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBtn(Glyph.Back, onBack, size = 48.dp)
                Column(Modifier.weight(1f)) {
                    MonoLabel("SOUND CONTROLS", color = Jx.Pink)
                    GlitchTitle(
                        "AUDIO LAB",
                        TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp),
                    )
                }
                NeonSwitch(cfg.effectsOn) { on -> update { it.copy(effectsOn = on) } }
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val dim = if (cfg.effectsOn) 1f else 0.4f

                // ---------------------------------------------------- Equalizer
                GlassCard(Modifier.alpha(dim)) {
                    CardTitle("Equalizer", cfg.preset)
                    if (rack == null || !rack.hasEq || rack.bandCount == 0) {
                        Note("This phone does not offer an equalizer to apps.")
                    } else {
                        val n = rack.bandCount
                        val levels = EqPresets.fit(cfg.bandsDb, n)
                        Spacer(Modifier.height(10.dp))
                        EqGraph(
                            levels = levels,
                            rangeDb = rack.rangeDb,
                            centersHz = rack.centersHz,
                            onChange = { band, db ->
                                update { c ->
                                    val next = EqPresets.fit(c.bandsDb, n).toMutableList()
                                    next[band] = db
                                    c.copy(bandsDb = next, preset = "Custom")
                                }
                            },
                        )
                        Spacer(Modifier.height(12.dp))

                        var saving by remember { mutableStateOf(false) }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            EqPresets.names.forEach { name ->
                                Chip(name, selected = cfg.preset == name, onClick = {
                                    update {
                                        it.copy(
                                            preset = name,
                                            bandsDb = EqPresets.curve(name, rack.centersHz, rack.rangeDb),
                                        )
                                    }
                                })
                            }
                            cfg.custom.keys.forEach { name ->
                                Chip("★ $name", selected = cfg.preset == name, onClick = {
                                    update { c ->
                                        c.copy(preset = name, bandsDb = EqPresets.fit(c.custom[name] ?: emptyList(), n))
                                    }
                                })
                            }
                            Chip("+ Save mine", selected = false, onClick = { saving = true })
                        }
                        if (cfg.custom.containsKey(cfg.preset)) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Delete \"${cfg.preset}\"",
                                color = Jx.Pink,
                                fontSize = 13.sp,
                                modifier = Modifier.bounceClick {
                                    update { c -> c.copy(custom = c.custom - c.preset, preset = "Custom") }
                                },
                            )
                        }
                        if (saving) {
                            SavePresetDialog(
                                onDismiss = { saving = false },
                                onSave = { raw ->
                                    val name = raw.trim()
                                    if (name.isNotEmpty() && !EqPresets.isBuiltIn(name) && name != "Custom") {
                                        update { c ->
                                            c.copy(custom = c.custom + (name to EqPresets.fit(c.bandsDb, n)), preset = name)
                                        }
                                    }
                                    saving = false
                                },
                            )
                        }
                    }
                }

                // ---------------------------------------------------- Bass and space
                GlassCard(Modifier.alpha(dim)) {
                    CardTitle("Bass & Space", "")
                    LabeledSlider(
                        title = "Bass boost",
                        valueText = if (cfg.bass == 0) "Off" else "${cfg.bass / 10}%",
                        value = cfg.bass / 1000f,
                        onChange = { v -> update { it.copy(bass = (v * 1000f).roundToInt()) } },
                    )
                    LabeledSlider(
                        title = "Surround",
                        valueText = if (cfg.surround == 0) "Off" else "${cfg.surround / 10}%",
                        value = cfg.surround / 1000f,
                        onChange = { v -> update { it.copy(surround = (v * 1000f).roundToInt()) } },
                    )
                    LabeledSlider(
                        title = "Loudness",
                        valueText = if (cfg.loudnessDb < 0.25f) "Off" else "+%.1f dB".format(cfg.loudnessDb),
                        value = cfg.loudnessDb / 12f,
                        onChange = { v -> update { it.copy(loudnessDb = (v * 24f).roundToInt() / 2f) } },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Reverb", color = Jx.Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        reverbNames.forEachIndexed { i, name ->
                            Chip(name, selected = cfg.reverb == i, onClick = { update { it.copy(reverb = i) } })
                        }
                    }
                    Note("Surround is made for headphones. High loudness can distort, so back it off if the sound gets crunchy.")
                }

                // ---------------------------------------------------- Transitions
                GlassCard {
                    CardTitle("Transitions", "")
                    LabeledSlider(
                        title = "Crossfade",
                        valueText = if (cfg.crossfadeSec < 0.5f) "Off" else "${cfg.crossfadeSec.roundToInt()} s",
                        value = cfg.crossfadeSec / 12f,
                        onChange = { v -> update { it.copy(crossfadeSec = (v * 12f).roundToInt().toFloat()) } },
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Gapless playback", color = Jx.Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "No silence between songs. Used when crossfade is off.",
                                color = Jx.Muted,
                                fontSize = 12.sp,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        NeonSwitch(cfg.gapless) { on -> update { it.copy(gapless = on) } }
                    }
                }

                // ---------------------------------------------------- Balance
                GlassCard {
                    CardTitle("Balance", "")
                    val pct = (cfg.balance * 100f).roundToInt()
                    LabeledSlider(
                        title = "Left / Right",
                        valueText = when {
                            pct == 0 -> "Centre"
                            pct < 0 -> "L ${-pct}%"
                            else -> "R $pct%"
                        },
                        value = (cfg.balance + 1f) / 2f,
                        centered = true,
                        onChange = { v ->
                            val b = v * 2f - 1f
                            update { it.copy(balance = if (b > -0.06f && b < 0.06f) 0f else b) }
                        },
                    )
                }

                // ---------------------------------------------------- Sleep timer
                GlassCard {
                    val now by produceState(SystemClock.elapsedRealtime()) {
                        while (true) {
                            delay(1000L)
                            value = SystemClock.elapsedRealtime()
                        }
                    }
                    val left = player.sleepAt - now
                    CardTitle("Sleep timer", if (player.sleepAt > 0L && left > 0L) formatTime(left) else "")
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Chip("Off", selected = player.sleepAt == 0L, onClick = { PlayerEngine.setSleepTimer(0) })
                        listOf(15, 30, 45, 60, 90).forEach { m ->
                            Chip("$m min", selected = false, onClick = { PlayerEngine.setSleepTimer(m) })
                        }
                    }
                    Note("Music pauses by itself when the timer runs out.")
                }
            }
        }
    }
}

@Composable
private fun CardTitle(title: String, trailing: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Jx.Ink, fontSize = 18.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
        if (trailing.isNotEmpty()) MonoLabel(trailing.uppercase(), color = Jx.Pink)
    }
}

@Composable
private fun Note(text: String) {
    Spacer(Modifier.height(10.dp))
    Text(text, color = Jx.Muted, fontSize = 12.sp, lineHeight = 17.sp)
}

@Composable
private fun NeonSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = Jx.Pink,
            uncheckedThumbColor = Jx.Muted,
            uncheckedTrackColor = Jx.CardHi,
            uncheckedBorderColor = Jx.Muted.copy(alpha = 0.5f),
        ),
    )
}

@Composable
private fun LabeledSlider(
    title: String,
    valueText: String,
    value: Float,
    onChange: (Float) -> Unit,
    centered: Boolean = false,
) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Jx.Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(valueText, color = Jx.Pink, fontSize = 13.sp, fontFamily = Jx.Mono)
        }
        NeonSlider(value = value, onChange = onChange, centered = centered)
    }
}

@Composable
private fun SavePresetDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Jx.CardHi,
        titleContentColor = Jx.Ink,
        title = { Text("Save this sound") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(24) },
                singleLine = true,
                label = { Text("Preset name") },
            )
        },
        confirmButton = { TextButton(onClick = { onSave(name) }) { Text("Save", color = Jx.Pink) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Jx.Muted) } },
    )
}

private fun hzLabel(hz: Float): String =
    if (hz >= 1000f) {
        val k = hz / 1000f
        if (k >= 10f) "${k.roundToInt()}k" else "%.1fk".format(k)
    } else {
        "${hz.roundToInt()}"
    }

/**
 * The equalizer: drag any column up or down. The glowing curve springs
 * into place when a preset is picked.
 */
@Composable
private fun EqGraph(
    levels: List<Float>,
    rangeDb: Float,
    centersHz: List<Float>,
    onChange: (Int, Float) -> Unit,
) {
    val n = levels.size
    val change by rememberUpdatedState(onChange)

    key(n) {
        val animated = levels.map { target ->
            animateFloatAsState(
                targetValue = target,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = 900f),
                label = "band",
            ).value
        }

        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth()) {
                levels.forEach { db ->
                    val r = (db * 2f).roundToInt() / 2f
                    Text(
                        if (r > 0f) "+%.1f".format(r) else "%.1f".format(r),
                        color = if (r == 0f) Jx.Muted else Jx.Pink,
                        fontSize = 10.sp,
                        fontFamily = Jx.Mono,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .pointerInput(n, rangeDb) {
                        val pad = 16.dp.toPx()
                        fun levelAt(y: Float): Float {
                            val f = 1f - ((y - pad) / (size.height - 2f * pad)).coerceIn(0f, 1f)
                            val db = (f * 2f - 1f) * rangeDb
                            return (db * 2f).roundToInt() / 2f
                        }
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val band = (down.position.x / (size.width / n.toFloat())).toInt().coerceIn(0, n - 1)
                            down.consume()
                            change(band, levelAt(down.position.y))
                            drag(down.id) { c ->
                                c.consume()
                                change(band, levelAt(c.position.y))
                            }
                        }
                    }
            ) {
                val pad = 16.dp.toPx()
                val colW = size.width / n
                val top = pad
                val bottom = size.height - pad
                val mid = (top + bottom) / 2f

                fun x(i: Int) = colW * (i + 0.5f)
                fun y(db: Float) = mid - (db / rangeDb).coerceIn(-1.2f, 1.2f) * (bottom - top) / 2f

                // Column tracks and the 0 dB line
                for (i in 0 until n) {
                    drawLine(
                        Jx.Muted.copy(alpha = 0.22f),
                        Offset(x(i), top),
                        Offset(x(i), bottom),
                        3.dp.toPx(),
                        StrokeCap.Round,
                    )
                }
                drawLine(
                    Jx.Muted.copy(alpha = 0.45f),
                    Offset(0f, mid),
                    Offset(size.width, mid),
                    1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 10f)),
                )

                // Smooth curve through every band
                fun trace(p: Path) {
                    p.moveTo(0f, y(animated[0]))
                    p.lineTo(x(0), y(animated[0]))
                    for (i in 1 until n) {
                        val x0 = x(i - 1)
                        val x1 = x(i)
                        val cx = (x0 + x1) / 2f
                        p.cubicTo(cx, y(animated[i - 1]), cx, y(animated[i]), x1, y(animated[i]))
                    }
                    p.lineTo(size.width, y(animated[n - 1]))
                }

                val curve = Path()
                trace(curve)
                val fill = Path()
                trace(fill)
                fill.lineTo(size.width, size.height)
                fill.lineTo(0f, size.height)
                fill.close()
                drawPath(
                    fill,
                    Brush.verticalGradient(
                        listOf(Jx.Violet.copy(alpha = 0.45f), Jx.Pink.copy(alpha = 0.12f), Color.Transparent)
                    ),
                )
                val line = Brush.horizontalGradient(listOf(Jx.Violet, Jx.Pink, Jx.Cyan))
                drawPath(
                    curve,
                    line,
                    alpha = 0.3f,
                    style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                drawPath(
                    curve,
                    line,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )

                // Handles
                for (i in 0 until n) {
                    val c = Offset(x(i), y(animated[i]))
                    val glow = 15.dp.toPx()
                    drawCircle(
                        Brush.radialGradient(
                            listOf(Jx.Pink.copy(alpha = 0.65f), Color.Transparent),
                            center = c,
                            radius = glow,
                        ),
                        radius = glow,
                        center = c,
                    )
                    drawCircle(Color.White, radius = 6.dp.toPx(), center = c)
                }
            }
            Row(Modifier.fillMaxWidth()) {
                centersHz.take(n).forEach { hz ->
                    Text(
                        hzLabel(hz),
                        color = Jx.Muted,
                        fontSize = 10.sp,
                        fontFamily = Jx.Mono,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
