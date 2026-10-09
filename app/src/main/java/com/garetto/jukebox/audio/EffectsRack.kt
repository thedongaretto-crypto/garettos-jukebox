package com.garetto.jukebox.audio

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The phone's built-in audio effects, attached to this app's audio session.
 * Every effect is optional: if a phone does not support one it is skipped.
 */
class EffectsRack(sessionId: Int) {
    private val eq: Equalizer? = make { Equalizer(0, sessionId) }
    private val bass: BassBoost? = make { BassBoost(0, sessionId) }
    private val virtualizer: Virtualizer? = make { Virtualizer(0, sessionId) }
    // Reverb is a "send" effect: it lives on the output mix and each player
    // feeds some of its sound into it (see PlayerEngine).
    private val reverb: PresetReverb? = make { PresetReverb(0, 0) }
    private val loudness: LoudnessEnhancer? = make { LoudnessEnhancer(sessionId) }

    val hasEq: Boolean get() = eq != null
    val hasBass: Boolean get() = bass != null
    val hasSurround: Boolean get() = virtualizer != null
    val hasReverb: Boolean get() = reverb != null
    val hasLoudness: Boolean get() = loudness != null

    /** Id players attach to in order to send sound into the reverb; 0 if unavailable. */
    val reverbId: Int
        get() = try {
            reverb?.id ?: 0
        } catch (t: Throwable) {
            0
        }

    val bandCount: Int
    /** Centre frequency of each band in Hz. */
    val centersHz: List<Float>
    /** Bands can move between -rangeDb and +rangeDb. */
    val rangeDb: Float

    private var minMb = -1500
    private var maxMb = 1500
    private val lastBands: IntArray
    private var lastBass = -1
    private var lastSurround = -1
    private var lastReverb = -1
    private var lastLoudness = -1

    init {
        var count = 0
        var centers: List<Float> = emptyList()
        try {
            eq?.let { e ->
                count = e.numberOfBands.toInt()
                centers = List(count) { e.getCenterFreq(it.toShort()) / 1000f }
                val r = e.bandLevelRange
                minMb = r[0].toInt()
                maxMb = r[1].toInt()
            }
        } catch (t: Throwable) {
            count = 0
            centers = emptyList()
        }
        bandCount = count
        centersHz = centers
        rangeDb = (min(abs(minMb), abs(maxMb)) / 100f).coerceAtLeast(1f)
        lastBands = IntArray(count) { Int.MIN_VALUE }
    }

    fun apply(c: AudioConfig) {
        val on = c.effectsOn

        safe {
            eq?.let { e ->
                if (on) {
                    for (i in 0 until bandCount) {
                        val db = c.bandsDb.getOrElse(i) { 0f }
                        val mb = (db * 100f).roundToInt().coerceIn(minMb, maxMb)
                        if (lastBands[i] != mb) {
                            e.setBandLevel(i.toShort(), mb.toShort())
                            lastBands[i] = mb
                        }
                    }
                }
                if (e.enabled != on) e.setEnabled(on)
            }
        }

        safe {
            bass?.let { b ->
                val want = on && c.bass > 0
                if (want && lastBass != c.bass) {
                    b.setStrength(c.bass.coerceIn(0, 1000).toShort())
                    lastBass = c.bass
                }
                if (b.enabled != want) b.setEnabled(want)
            }
        }

        safe {
            virtualizer?.let { v ->
                val want = on && c.surround > 0
                if (want && lastSurround != c.surround) {
                    v.setStrength(c.surround.coerceIn(0, 1000).toShort())
                    lastSurround = c.surround
                }
                if (v.enabled != want) v.setEnabled(want)
            }
        }

        safe {
            reverb?.let { r ->
                val want = on && c.reverb > 0
                if (want && lastReverb != c.reverb) {
                    r.preset = c.reverb.coerceIn(1, 6).toShort()
                    lastReverb = c.reverb
                }
                if (r.enabled != want) r.setEnabled(want)
            }
        }

        safe {
            loudness?.let { l ->
                val mb = (c.loudnessDb * 100f).roundToInt().coerceIn(0, 1200)
                val want = on && mb > 0
                if (want && lastLoudness != mb) {
                    l.setTargetGain(mb)
                    lastLoudness = mb
                }
                if (l.enabled != want) l.setEnabled(want)
            }
        }
    }

    private inline fun safe(block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            // An effect the phone refuses is simply left alone.
        }
    }

    private inline fun <T> make(block: () -> T): T? = try {
        block()
    } catch (t: Throwable) {
        null
    }
}
