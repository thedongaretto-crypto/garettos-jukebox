package com.garetto.jukebox.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.garetto.jukebox.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

data class BackdropState(
    /** true when the user picked their own photo. */
    val custom: Boolean = false,
    /** Bumped whenever the photo changes so the screen reloads it. */
    val version: Int = 0,
    val rain: Boolean = true,
)

/** The picture behind the full player screen. */
object Backdrop {
    private const val FILE = "player_backdrop.jpg"
    private var prefs: SharedPreferences? = null

    private val _state = MutableStateFlow(BackdropState())
    val state = _state.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val app = context.applicationContext
        val p = app.getSharedPreferences("jukebox_backdrop", Context.MODE_PRIVATE)
        prefs = p
        _state.value = BackdropState(
            custom = p.getBoolean("custom", false) && File(app.filesDir, FILE).exists(),
            rain = p.getBoolean("rain", true),
        )
    }

    /** Copy the chosen photo into the app (shrunk to a sensible size) and use it. */
    suspend fun setFromUri(context: Context, uri: Uri): Boolean {
        val app = context.applicationContext
        val ok = withContext(Dispatchers.IO) {
            try {
                val source = ImageDecoder.createSource(app.contentResolver, uri)
                val bmp = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val longest = max(info.size.width, info.size.height)
                    if (longest > 2400) {
                        val k = 2400f / longest
                        decoder.setTargetSize(
                            max(1, (info.size.width * k).toInt()),
                            max(1, (info.size.height * k).toInt()),
                        )
                    }
                }
                File(app.filesDir, FILE).outputStream().use { out ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                true
            } catch (e: Exception) {
                false
            }
        }
        if (ok) {
            prefs?.edit()?.putBoolean("custom", true)?.apply()
            _state.update { it.copy(custom = true, version = it.version + 1) }
        }
        return ok
    }

    fun useDefault() {
        prefs?.edit()?.putBoolean("custom", false)?.apply()
        _state.update { it.copy(custom = false, version = it.version + 1) }
    }

    fun setRain(on: Boolean) {
        prefs?.edit()?.putBoolean("rain", on)?.apply()
        _state.update { it.copy(rain = on) }
    }

    suspend fun load(context: Context): Bitmap? = withContext(Dispatchers.IO) {
        try {
            BitmapFactory.decodeFile(File(context.applicationContext.filesDir, FILE).absolutePath)
        } catch (e: Exception) {
            null
        }
    }
}

/** Photo + a dark wash so the controls stay readable + falling rain. */
@Composable
fun PlayerBackdrop() {
    val st by Backdrop.state.collectAsState()
    val context = LocalContext.current.applicationContext
    val custom by produceState<ImageBitmap?>(initialValue = null, st.custom, st.version) {
        value = if (st.custom) Backdrop.load(context)?.asImageBitmap() else null
    }
    Box(Modifier.fillMaxSize()) {
        val photo = custom
        if (st.custom && photo != null) {
            Image(
                bitmap = photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Image(
                painter = painterResource(R.drawable.street_bg),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.60f),
                        0.22f to Color.Black.copy(alpha = 0.22f),
                        0.55f to Color.Black.copy(alpha = 0.30f),
                        0.78f to Color.Black.copy(alpha = 0.72f),
                        1f to Color.Black.copy(alpha = 0.88f),
                    )
                )
        )
        if (st.rain) {
            RainLayer()
        }
    }
}

private class Drop(val x: Float, val phase: Float, val speed: Float, val length: Float, val width: Float, val alpha: Float)

private val drops: List<Drop> = Random(999).let { r ->
    List(150) { i ->
        // Three depths: far rain is thin, slow and faint; near rain is long, fast and bright.
        val depth = when {
            i < 80 -> 0.35f + r.nextFloat() * 0.2f
            i < 130 -> 0.6f + r.nextFloat() * 0.2f
            else -> 0.9f + r.nextFloat() * 0.3f
        }
        Drop(
            x = r.nextFloat(),
            phase = r.nextFloat(),
            speed = 0.55f + depth * 0.9f,
            length = 0.025f + depth * 0.07f,
            width = 0.5f + depth * 1.1f,
            alpha = 0.10f + depth * 0.22f,
        )
    }
}

private fun rnd(a: Int, b: Int): Float {
    val v = sin(a * 12.9898 + b * 78.233) * 43758.5453
    return (v - floor(v)).toFloat()
}

/** Falling rain with ripples on the ground. Plain lines, so it stays smooth. */
@Composable
private fun RainLayer() {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) time.floatValue = (time.floatValue + (now - last) / 1_000_000_000f) % 1000f
                last = now
            }
        }
    }
    val rainColor = Color(0xFFD6DCFF)
    Canvas(Modifier.fillMaxSize()) {
        val t = time.floatValue
        val w = size.width
        val h = size.height
        val lean = 0.16f // how far the wind pushes the rain sideways
        val dp = density

        for (d in drops) {
            val len = d.length * h
            val u = (t * d.speed + d.phase) % 1f
            val y = u * (h + len) - len
            val x = d.x * (w + lean * h) - lean * h + (y + len) * lean
            drawLine(
                color = rainColor.copy(alpha = d.alpha),
                start = Offset(x, y),
                end = Offset(x + len * lean, y + len),
                strokeWidth = d.width * dp,
                cap = StrokeCap.Round,
            )
        }

        // Ripples where drops land on the street (lower part of the picture)
        for (i in 0 until 18) {
            val period = 1.1f + rnd(i, 1) * 1.2f
            val cycle = t / period + rnd(i, 2)
            val k = floor(cycle.toDouble()).toInt()
            val u = cycle - k
            val yf = 0.66f + rnd(i, k * 3 + 5) * 0.32f
            val cx = rnd(i, k * 7 + 11) * w
            val cy = yf * h
            val grow = (0.35f + (yf - 0.66f) * 2.2f) * 26f * dp
            val rx = (0.15f + u) * grow
            val fade = (1f - u) * (1f - u)
            drawOval(
                color = rainColor.copy(alpha = 0.30f * fade),
                topLeft = Offset(cx - rx, cy - rx * 0.28f),
                size = Size(rx * 2f, rx * 0.56f),
                style = Stroke(width = 1.2f * dp),
            )
        }
    }
}

/** Pop-up for changing the picture behind the player. */
@Composable
fun BackdropDialog(onDismiss: () -> Unit) {
    val st by Backdrop.state.collectAsState()
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { Backdrop.setFromUri(context, uri) }
    }
    val shape = RoundedCornerShape(26.dp)
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
                .padding(16.dp)
        ) {
            Text(
                "Player background",
                color = Jx.Ink,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
            )
            MenuRow(Glyph.Plus, "Choose a photo from this phone") {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            MenuRow(
                if (st.custom) Glyph.Moon else Glyph.Check,
                "Use the Chicago street",
                tint = if (st.custom) Jx.Ink else Jx.Pink,
            ) { Backdrop.useDefault() }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Falling rain", color = Jx.Ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Switch(
                    checked = st.rain,
                    onCheckedChange = { Backdrop.setRain(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Jx.Pink,
                        uncheckedThumbColor = Jx.Muted,
                        uncheckedTrackColor = Jx.Card,
                        uncheckedBorderColor = Jx.Muted.copy(alpha = 0.5f),
                    ),
                )
            }
            Spacer(Modifier.height(6.dp))
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
}
