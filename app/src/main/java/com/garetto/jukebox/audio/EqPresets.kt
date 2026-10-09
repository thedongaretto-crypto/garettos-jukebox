package com.garetto.jukebox.audio

import kotlin.math.ln

/**
 * Built-in equalizer presets. Each is a gain curve (dB) at five reference
 * frequencies; it is stretched to fit however many bands the phone has.
 */
object EqPresets {
    private val refHz = floatArrayOf(60f, 230f, 910f, 3600f, 14000f)

    private val curves: LinkedHashMap<String, FloatArray> = linkedMapOf(
        "Flat" to floatArrayOf(0f, 0f, 0f, 0f, 0f),
        "999 Signature" to floatArrayOf(5f, 2f, -1f, 2f, 4f),
        "808 Heavy" to floatArrayOf(7f, 4f, 0f, -1f, 1f),
        "Melodic Trap" to floatArrayOf(4f, 1f, -1f, 3f, 4f),
        "Lucid Vocals" to floatArrayOf(-1f, 0f, 3f, 4f, 2f),
        "Emo Guitar" to floatArrayOf(2f, 3f, 1f, 3f, 2f),
        "Late Night" to floatArrayOf(3f, 2f, 0f, -2f, -3f),
        "Car Stereo" to floatArrayOf(6f, 3f, -1f, 1f, 3f),
        "Earbuds" to floatArrayOf(4f, 1f, 0f, 2f, 5f),
        "Bright" to floatArrayOf(-2f, -1f, 0f, 3f, 6f),
    )

    val names: List<String> get() = curves.keys.toList()

    fun isBuiltIn(name: String) = curves.containsKey(name)

    /** Gains in dB for the given band centre frequencies (Hz). */
    fun curve(name: String, centersHz: List<Float>, rangeDb: Float): List<Float> {
        val c = curves[name] ?: return centersHz.map { 0f }
        return centersHz.map { hz -> interpolate(c, hz).coerceIn(-rangeDb, rangeDb) }
    }

    private fun interpolate(c: FloatArray, hz: Float): Float {
        if (hz <= refHz.first()) return c.first()
        if (hz >= refHz.last()) return c.last()
        for (i in 0 until refHz.size - 1) {
            val a = refHz[i]
            val b = refHz[i + 1]
            if (hz in a..b) {
                val t = (ln(hz) - ln(a)) / (ln(b) - ln(a))
                return c[i] + (c[i + 1] - c[i]) * t
            }
        }
        return 0f
    }

    /** Fit a saved list of gains to the phone's band count. */
    fun fit(bands: List<Float>, count: Int): List<Float> =
        if (bands.size == count) bands else List(count) { bands.getOrElse(it) { 0f } }
}
