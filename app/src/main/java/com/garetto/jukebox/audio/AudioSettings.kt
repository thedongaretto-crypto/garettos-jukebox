package com.garetto.jukebox.audio

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Everything adjustable in the Audio Lab. Saved automatically. */
data class AudioConfig(
    val effectsOn: Boolean = true,
    val preset: String = "Flat",
    /** Equalizer band gains in dB, one per band the phone offers. */
    val bandsDb: List<Float> = emptyList(),
    /** 0..1000 */
    val bass: Int = 0,
    /** 0..1000 */
    val surround: Int = 0,
    /** 0 = off, 1..6 = room presets */
    val reverb: Int = 0,
    /** How much reverb is mixed in, 0..1 */
    val reverbAmount: Float = 0.7f,
    /** Extra loudness in dB, 0..12 */
    val loudnessDb: Float = 0f,
    /** Seconds of overlap between songs, 0 = off */
    val crossfadeSec: Float = 0f,
    val gapless: Boolean = true,
    /** -1 = full left, 0 = centre, 1 = full right */
    val balance: Float = 0f,
    /** Presets the user saved: name -> band gains in dB */
    val custom: Map<String, List<Float>> = emptyMap(),
)

object AudioSettings {
    private const val KEY = "audio_config"
    private var prefs: SharedPreferences? = null

    private val _config = MutableStateFlow(AudioConfig())
    val config = _config.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("jukebox", Context.MODE_PRIVATE)
        prefs = p
        p.getString(KEY, null)?.let { raw ->
            try {
                _config.value = fromJson(raw)
            } catch (e: Exception) {
                // Corrupt settings: fall back to defaults.
            }
        }
    }

    fun update(change: (AudioConfig) -> AudioConfig) {
        val next = change(_config.value)
        if (next == _config.value) return
        _config.value = next
        prefs?.edit()?.putString(KEY, toJson(next))?.apply()
    }

    private fun floats(list: List<Float>) = JSONArray(list.map { it.toDouble() })

    private fun toJson(c: AudioConfig): String {
        val custom = JSONObject()
        c.custom.forEach { (name, bands) -> custom.put(name, floats(bands)) }
        return JSONObject()
            .put("effectsOn", c.effectsOn)
            .put("preset", c.preset)
            .put("bands", floats(c.bandsDb))
            .put("bass", c.bass)
            .put("surround", c.surround)
            .put("reverb", c.reverb)
            .put("reverbAmount", c.reverbAmount.toDouble())
            .put("loudness", c.loudnessDb.toDouble())
            .put("crossfade", c.crossfadeSec.toDouble())
            .put("gapless", c.gapless)
            .put("balance", c.balance.toDouble())
            .put("custom", custom)
            .toString()
    }

    private fun readFloats(a: JSONArray?): List<Float> {
        if (a == null) return emptyList()
        return List(a.length()) { a.optDouble(it, 0.0).toFloat() }
    }

    private fun fromJson(raw: String): AudioConfig {
        val o = JSONObject(raw)
        val custom = LinkedHashMap<String, List<Float>>()
        o.optJSONObject("custom")?.let { c ->
            val keys = c.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                custom[k] = readFloats(c.optJSONArray(k))
            }
        }
        return AudioConfig(
            effectsOn = o.optBoolean("effectsOn", true),
            preset = o.optString("preset", "Flat"),
            bandsDb = readFloats(o.optJSONArray("bands")),
            bass = o.optInt("bass", 0).coerceIn(0, 1000),
            surround = o.optInt("surround", 0).coerceIn(0, 1000),
            reverb = o.optInt("reverb", 0).coerceIn(0, 6),
            reverbAmount = o.optDouble("reverbAmount", 0.7).toFloat().coerceIn(0f, 1f),
            loudnessDb = o.optDouble("loudness", 0.0).toFloat().coerceIn(0f, 12f),
            crossfadeSec = o.optDouble("crossfade", 0.0).toFloat().coerceIn(0f, 12f),
            gapless = o.optBoolean("gapless", true),
            balance = o.optDouble("balance", 0.0).toFloat().coerceIn(-1f, 1f),
            custom = custom,
        )
    }
}
