package com.garetto.jukebox.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.RuntimeShader
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlin.math.max

data class BackdropState(
    /** true when the user picked their own photo. */
    val custom: Boolean = false,
    /** Bumped whenever the photo changes so the screen reloads it. */
    val version: Int = 0,
    val smoke: Boolean = true,
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
            smoke = p.getBoolean("smoke", true),
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

    fun setSmoke(on: Boolean) {
        prefs?.edit()?.putBoolean("smoke", on)?.apply()
        _state.update { it.copy(smoke = on) }
    }

    suspend fun load(context: Context): Bitmap? = withContext(Dispatchers.IO) {
        try {
            BitmapFactory.decodeFile(File(context.applicationContext.filesDir, FILE).absolutePath)
        } catch (e: Exception) {
            null
        }
    }
}

/** Photo + drifting smoke + a dark wash so the controls stay readable. */
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
        if (st.smoke && Build.VERSION.SDK_INT >= 33) {
            SmokeLayer()
        }
    }
}

/**
 * Smoke drawn by the phone's graphics chip. It is rendered at one third of
 * the screen size and stretched, which keeps it soft and light on battery.
 */
@RequiresApi(33)
@Composable
private fun SmokeLayer() {
    val shader = remember {
        try {
            RuntimeShader(SMOKE_SHADER)
        } catch (e: Exception) {
            null
        }
    } ?: return
    val brush = remember(shader) { ShaderBrush(shader) }
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) time.floatValue = (time.floatValue + (now - last) / 1_000_000_000f) % 3600f
                last = now
            }
        }
    }
    Canvas(
        Modifier
            .fillMaxSize(1f / 3f)
            .graphicsLayer {
                scaleX = 3f
                scaleY = 3f
                transformOrigin = TransformOrigin(0f, 0f)
                compositingStrategy = CompositingStrategy.Offscreen
            }
    ) {
        shader.setFloatUniform("uRes", size.width, size.height)
        shader.setFloatUniform("uTime", time.floatValue)
        drawRect(brush)
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
            if (Build.VERSION.SDK_INT >= 33) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Drifting smoke", color = Jx.Ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Switch(
                        checked = st.smoke,
                        onCheckedChange = { Backdrop.setSmoke(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Jx.Pink,
                            uncheckedThumbColor = Jx.Muted,
                            uncheckedTrackColor = Jx.Card,
                            uncheckedBorderColor = Jx.Muted.copy(alpha = 0.5f),
                        ),
                    )
                }
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

/**
 * Thin plumes of smoke rising from street level plus a low mist.
 * Output is the smoke colour with transparency, drawn over the photo.
 */
private const val SMOKE_SHADER = """
uniform float2 uRes;
uniform float uTime;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}
float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}
float fbm(vec2 p) {
    float a = 0.5;
    float s = 0.0;
    for (int i = 0; i < 4; i++) {
        s += a * noise(p);
        p = p * 2.03 + vec2(17.1, 9.2);
        a *= 0.5;
    }
    return s;
}
vec4 smoke(vec2 uv, float asp, float t) {
    float up = 1.0 - uv.y;
    float a = 0.0;
    for (int i = 0; i < 4; i++) {
        float fi = float(i);
        vec2 src = vec2(0.14, 0.27);
        if (i == 1) { src = vec2(0.47, 0.34); }
        if (i == 2) { src = vec2(0.90, 0.21); }
        if (i == 3) { src = vec2(0.66, 0.30); }
        float h = up - src.y;
        if (h > 0.0) {
            float dx = (uv.x - src.x) * asp;
            float sway = (fbm(vec2(h * 3.0 - t * 0.10, fi * 5.3)) - 0.5) * 0.45 * h;
            float width = 0.008 + h * 0.12;
            float e = (dx - sway) / width;
            float env = exp(-e * e) * smoothstep(0.0, 0.10, h) * exp(-h * 2.0);
            vec2 w = vec2((dx - sway) / (0.2 + h), h) * vec2(5.0, 3.4);
            w.y -= t * 0.20;
            w += 1.6 * vec2(fbm(w * 1.2 + vec2(fi * 3.0, t * 0.05)), fbm(w * 1.2 + vec2(7.0, -t * 0.04)));
            float n = fbm(w + fi * 11.0);
            float strand = 1.0 - abs(2.0 * n - 1.0);
            strand = strand * strand;
            strand = strand * strand;
            a += env * (0.16 * smoothstep(0.35, 0.75, n) + 1.15 * strand);
        }
    }
    float mist = fbm(vec2(uv.x * asp * 4.0 + t * 0.03, up * 6.0 - t * 0.04));
    a += mist * mist * smoothstep(0.40, 0.02, up) * 0.5;
    a = clamp(a, 0.0, 0.6);
    vec3 c = mix(vec3(0.62, 0.58, 0.72), vec3(1.0, 0.84, 0.70), smoothstep(0.5, 0.0, up));
    return vec4(c * a, a);
}
half4 main(float2 fragCoord) {
    vec2 uv = fragCoord / uRes;
    return half4(smoke(uv, uRes.x / uRes.y, uTime));
}
"""
