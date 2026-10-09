package com.garetto.jukebox.audio

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import com.garetto.jukebox.data.Song
import com.garetto.jukebox.data.UserData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

enum class LoopMode { OFF, ALL, ONE }

data class PlayerState(
    val queue: List<Song> = emptyList(),
    val index: Int = -1,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val loop: LoopMode = LoopMode.OFF,
    /** SystemClock.elapsedRealtime() at which the sleep timer pauses playback; 0 = off. */
    val sleepAt: Long = 0L,
    /** Bumped whenever the position jumps (seek, new track) so listeners can resync. */
    val stamp: Int = 0,
) {
    val song: Song? get() = queue.getOrNull(index)
}

/**
 * The player. Uses two MediaPlayers so one song can fade into the next
 * (crossfade) or hand over with no silence in between (gapless).
 * Everything here runs on the main thread.
 */
@SuppressLint("StaticFieldLeak")
object PlayerEngine {
    private lateinit var app: Context
    private lateinit var audioManager: AudioManager
    private var initialised = false

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(PlayerState())
    val state = _state.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position = _position.asStateFlow()

    var rack: EffectsRack? = null
        private set

    private var sessionId = 0
    private var source: List<Song> = emptyList()

    private var active: MediaPlayer? = null
    private var activePrepared = false
    private var incoming: MediaPlayer? = null
    private var incomingPrepared = false
    private var incomingIndex = -1
    private var outgoing: MediaPlayer? = null
    private var fadeStart = 0L
    private var fadeMs = 1L
    private var gaplessArmed = false

    private var wantPlay = false
    private var resumeOnFocus = false
    private var hasFocus = false
    private var errorStreak = 0
    private var tickerRunning = false
    private var counted = false

    private val attrs: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnFocus = false
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (_state.value.isPlaying) {
                    pause(keepFocus = true)
                    resumeOnFocus = true
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (resumeOnFocus) {
                    resumeOnFocus = false
                    resume()
                }
            }
        }
    }

    private val focusRequest: AudioFocusRequest by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener(focusListener, main)
            .build()
    }

    /** Pauses when headphones are unplugged so music does not blast from the speaker. */
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pause()
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (!tickerRunning) return
            tick()
            main.postDelayed(this, 100L)
        }
    }

    fun init(context: Context) {
        if (initialised) return
        initialised = true
        app = context.applicationContext
        audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        AudioSettings.init(app)
        UserData.init(app)
        sessionId = audioManager.generateAudioSessionId()
        rack = EffectsRack(sessionId)

        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= 33) {
            app.registerReceiver(noisyReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            app.registerReceiver(noisyReceiver, filter)
        }

        scope.launch {
            AudioSettings.config.collect { cfg ->
                rack?.apply(cfg)
                applyVolumes()
                armTransition(force = false)
            }
        }
    }

    // ---------------------------------------------------------------- controls

    /** Start playing [songs] from [startIndex]. */
    fun play(songs: List<Song>, startIndex: Int, shuffle: Boolean? = null) {
        if (songs.isEmpty()) return
        val start = startIndex.coerceIn(0, songs.size - 1)
        val sh = shuffle ?: _state.value.shuffle
        source = songs
        val queue: List<Song>
        val index: Int
        if (sh) {
            val first = songs[start]
            val rest = songs.toMutableList().also { it.removeAt(start) }.shuffled()
            queue = listOf(first) + rest
            index = 0
        } else {
            queue = songs
            index = start
        }
        errorStreak = 0
        _state.update { it.copy(queue = queue, index = index, shuffle = sh) }
        startCurrent(playWhenReady = true)
    }

    fun shuffleAll(songs: List<Song>) {
        if (songs.isEmpty()) return
        play(songs, songs.indices.random(), shuffle = true)
    }

    fun toggle() {
        if (_state.value.isPlaying) pause() else resume()
    }

    fun resume() {
        if (_state.value.song == null) return
        wantPlay = true
        val a = active
        if (a == null) {
            startCurrent(playWhenReady = true)
        } else if (activePrepared) {
            reallyStart()
        }
    }

    fun pause(keepFocus: Boolean = false) {
        wantPlay = false
        endFade()
        val a = active
        if (a != null && activePrepared) {
            try {
                if (a.isPlaying) a.pause()
            } catch (e: Exception) {
            }
        }
        stopTicker()
        if (!keepFocus) abandonFocus()
        _state.update { it.copy(isPlaying = false, stamp = it.stamp + 1) }
    }

    fun next() {
        val s = _state.value
        if (s.queue.isEmpty()) return
        val ni = if (s.index + 1 < s.queue.size) s.index + 1 else 0
        errorStreak = 0
        _state.update { it.copy(index = ni) }
        startCurrent(playWhenReady = true)
    }

    fun prev() {
        val s = _state.value
        if (s.queue.isEmpty()) return
        if (_position.value > 3000L || s.queue.size == 1) {
            seekTo(0L)
            return
        }
        val pi = if (s.index - 1 >= 0) s.index - 1 else s.queue.size - 1
        errorStreak = 0
        _state.update { it.copy(index = pi) }
        startCurrent(playWhenReady = true)
    }

    fun seekTo(ms: Long) {
        val a = active ?: return
        if (!activePrepared) return
        endFade()
        try {
            a.seekTo(ms.coerceAtLeast(0L).toInt())
        } catch (e: Exception) {
        }
        _position.value = ms.coerceAtLeast(0L)
        _state.update { it.copy(stamp = it.stamp + 1) }
    }

    fun toggleShuffle() {
        val s = _state.value
        val cur = s.song
        val on = !s.shuffle
        if (cur == null) {
            _state.update { it.copy(shuffle = on) }
            return
        }
        if (on) {
            val rest = s.queue.filter { it.id != cur.id }.shuffled()
            _state.update { it.copy(shuffle = true, queue = listOf(cur) + rest, index = 0) }
        } else {
            val list = if (source.isNotEmpty()) source else s.queue
            val i = list.indexOfFirst { it.id == cur.id }.coerceAtLeast(0)
            _state.update { it.copy(shuffle = false, queue = list, index = i) }
        }
        preloadNext()
    }

    fun cycleLoop() {
        _state.update {
            it.copy(
                loop = when (it.loop) {
                    LoopMode.OFF -> LoopMode.ALL
                    LoopMode.ALL -> LoopMode.ONE
                    LoopMode.ONE -> LoopMode.OFF
                }
            )
        }
        preloadNext()
    }

    /** Pause automatically after [minutes]; 0 turns the timer off. */
    fun setSleepTimer(minutes: Int) {
        val at = if (minutes <= 0) 0L else SystemClock.elapsedRealtime() + minutes * 60_000L
        _state.update { it.copy(sleepAt = at) }
    }

    // ---------------------------------------------------------------- internals

    private fun newPlayer(song: Song): MediaPlayer? {
        val mp = MediaPlayer()
        return try {
            mp.audioSessionId = sessionId
            mp.setAudioAttributes(attrs)
            mp.setWakeMode(app, PowerManager.PARTIAL_WAKE_LOCK)
            mp.setDataSource(app, song.uri)
            attachReverb(mp)
            mp
        } catch (e: Exception) {
            try {
                mp.release()
            } catch (e2: Exception) {
            }
            null
        }
    }

    private fun startCurrent(playWhenReady: Boolean) {
        releasePlayers()
        counted = false
        val song = _state.value.song ?: return
        wantPlay = playWhenReady
        _position.value = 0L
        _state.update {
            it.copy(isPlaying = playWhenReady, durationMs = song.durationMs, stamp = it.stamp + 1)
        }
        val mp = newPlayer(song)
        if (mp == null) {
            main.post { handleEnd(error = true) }
            return
        }
        active = mp
        activePrepared = false
        mp.setOnPreparedListener { p ->
            if (p === active) {
                activePrepared = true
                errorStreak = 0
                val d = try {
                    p.duration.toLong()
                } catch (e: Exception) {
                    0L
                }
                if (d > 0L) _state.update { it.copy(durationMs = d) }
                applyVolumes()
                if (wantPlay) reallyStart()
                preloadNext()
            }
        }
        mp.setOnCompletionListener { p -> onCompletion(p) }
        mp.setOnErrorListener { p, _, _ ->
            if (p === active) main.post { handleEnd(error = true) }
            true
        }
        try {
            mp.prepareAsync()
        } catch (e: Exception) {
            main.post { handleEnd(error = true) }
        }
    }

    private fun reallyStart() {
        val a = active ?: return
        if (!requestFocus()) {
            wantPlay = false
            _state.update { it.copy(isPlaying = false) }
            return
        }
        try {
            a.start()
        } catch (e: Exception) {
            return
        }
        _state.update { it.copy(isPlaying = true, stamp = it.stamp + 1) }
        startTicker()
        ensureService()
    }

    /** The song that follows automatically, or -1 when the queue is finished. */
    private fun nextIndexAuto(): Int {
        val s = _state.value
        if (s.queue.isEmpty() || s.index < 0) return -1
        return when {
            s.loop == LoopMode.ONE -> s.index
            s.index + 1 < s.queue.size -> s.index + 1
            s.loop == LoopMode.ALL -> 0
            else -> -1
        }
    }

    /** Get the next song loaded and ready in the second player. */
    private fun preloadNext() {
        gaplessArmed = false
        val a = active
        if (a != null && activePrepared) {
            try {
                a.setNextMediaPlayer(null)
            } catch (e: Exception) {
            }
        }
        incoming?.let { release(it) }
        incoming = null
        incomingPrepared = false
        incomingIndex = -1

        val ni = nextIndexAuto()
        if (ni < 0) return
        val song = _state.value.queue.getOrNull(ni) ?: return
        val mp = newPlayer(song) ?: return
        incoming = mp
        incomingIndex = ni
        mp.setOnPreparedListener { p ->
            if (p === incoming) {
                incomingPrepared = true
                applyVolumes()
                armTransition(force = true)
            }
        }
        mp.setOnCompletionListener { p -> onCompletion(p) }
        mp.setOnErrorListener { p, _, _ ->
            main.post {
                if (p === incoming) {
                    gaplessArmed = false
                    val cur = active
                    if (cur != null && activePrepared) {
                        try {
                            cur.setNextMediaPlayer(null)
                        } catch (e: Exception) {
                        }
                    }
                    release(p)
                    incoming = null
                    incomingPrepared = false
                } else if (p === active) {
                    handleEnd(error = true)
                }
            }
            true
        }
        try {
            mp.prepareAsync()
        } catch (e: Exception) {
            release(mp)
            incoming = null
        }
    }

    /** Tell Android to chain the next player onto the current one when gapless is wanted. */
    private fun armTransition(force: Boolean) {
        val a = active ?: return
        if (!activePrepared) return
        val cfg = AudioSettings.config.value
        val inc = incoming
        val want = cfg.gapless && cfg.crossfadeSec <= 0f && inc != null && incomingPrepared
        if (!force && want == gaplessArmed) return
        try {
            a.setNextMediaPlayer(if (want) inc else null)
            gaplessArmed = want
        } catch (e: Exception) {
            gaplessArmed = false
        }
    }

    private fun onCompletion(p: MediaPlayer) {
        if (p !== active) return
        if (gaplessArmed && incoming != null && incomingPrepared) {
            promoteIncoming(alreadyStarted = true, fade = false)
        } else {
            handleEnd(error = false)
        }
    }

    /** The preloaded player becomes the current one. */
    private fun promoteIncoming(alreadyStarted: Boolean, fade: Boolean) {
        val inc = incoming ?: return
        val old = active
        val newIndex = incomingIndex
        active = inc
        activePrepared = true
        incoming = null
        incomingPrepared = false
        incomingIndex = -1
        gaplessArmed = false
        counted = false

        if (fade && old != null) {
            outgoing?.let { release(it) }
            outgoing = old
            fadeStart = SystemClock.elapsedRealtime()
            try {
                inc.setVolume(0f, 0f)
                inc.start()
            } catch (e: Exception) {
            }
        } else {
            old?.let { release(it) }
            if (!alreadyStarted) {
                try {
                    inc.start()
                } catch (e: Exception) {
                }
            }
        }
        val d = try {
            inc.duration.toLong()
        } catch (e: Exception) {
            0L
        }
        _position.value = 0L
        _state.update {
            it.copy(index = newIndex, durationMs = d, isPlaying = true, stamp = it.stamp + 1)
        }
        applyVolumes()
        preloadNext()
    }

    private fun handleEnd(error: Boolean) {
        val s = _state.value
        if (error) errorStreak++
        val ni = nextIndexAuto()
        val giveUp = error && (errorStreak > 5 || errorStreak >= s.queue.size || ni == s.index)
        if (ni < 0 || giveUp) {
            wantPlay = false
            endFade()
            stopTicker()
            abandonFocus()
            val a = active
            if (a != null && activePrepared && !error) {
                try {
                    a.seekTo(0)
                } catch (e: Exception) {
                }
            }
            _position.value = 0L
            _state.update { it.copy(isPlaying = false, stamp = it.stamp + 1) }
            return
        }
        _state.update { it.copy(index = ni) }
        startCurrent(playWhenReady = true)
    }

    private fun tick() {
        val now = SystemClock.elapsedRealtime()
        val a = active
        if (a != null && activePrepared) {
            val pos: Long
            val dur: Long
            try {
                pos = a.currentPosition.toLong()
                dur = a.duration.toLong()
            } catch (e: Exception) {
                return
            }
            _position.value = pos

            // Count a listen once a decent chunk of the song has played.
            if (!counted && dur > 0L && pos >= min(30_000L, dur / 2L)) {
                counted = true
                _state.value.song?.let { UserData.bumpPlay(it.id) }
            }

            val cfg = AudioSettings.config.value
            if (cfg.crossfadeSec > 0f && outgoing == null && incoming != null && incomingPrepared && dur > 0L) {
                val xf = min((cfg.crossfadeSec * 1000f).toLong(), dur / 3L)
                if (xf > 200L && dur - pos <= xf) {
                    fadeMs = xf
                    promoteIncoming(alreadyStarted = false, fade = true)
                }
            }
        }

        if (outgoing != null) {
            if (now - fadeStart >= fadeMs) endFade() else applyVolumes()
        }

        val sleepAt = _state.value.sleepAt
        if (sleepAt > 0L && now >= sleepAt) {
            _state.update { it.copy(sleepAt = 0L) }
            pause()
        }
    }

    private fun endFade() {
        val o = outgoing ?: return
        outgoing = null
        release(o)
        applyVolumes()
    }

    /** Sets left/right volume from the balance setting and any crossfade in progress. */
    private fun applyVolumes() {
        val b = AudioSettings.config.value.balance.coerceIn(-1f, 1f)
        val left = if (b > 0f) 1f - b else 1f
        val right = if (b < 0f) 1f + b else 1f
        val o = outgoing
        var inGain = 1f
        var outGain = 0f
        if (o != null) {
            val t = ((SystemClock.elapsedRealtime() - fadeStart).toFloat() / fadeMs.toFloat()).coerceIn(0f, 1f)
            inGain = sin(t * PI.toFloat() / 2f)
            outGain = cos(t * PI.toFloat() / 2f)
        }
        try {
            active?.setVolume(left * inGain, right * inGain)
        } catch (e: Exception) {
        }
        try {
            o?.setVolume(left * outGain, right * outGain)
        } catch (e: Exception) {
        }
        val send = reverbSend()
        for (p in listOf(active, incoming, o)) {
            try {
                p?.setAuxEffectSendLevel(send)
            } catch (e: Exception) {
            }
        }
    }

    private fun reverbSend(): Float {
        val cfg = AudioSettings.config.value
        return if (cfg.effectsOn && cfg.reverb > 0) cfg.reverbAmount.coerceIn(0f, 1f) else 0f
    }

    /** Route part of this player's sound into the shared reverb. */
    private fun attachReverb(p: MediaPlayer) {
        val id = rack?.reverbId ?: 0
        if (id == 0) return
        try {
            p.attachAuxEffect(id)
            p.setAuxEffectSendLevel(reverbSend())
        } catch (e: Exception) {
        }
    }

    private fun startTicker() {
        if (tickerRunning) return
        tickerRunning = true
        main.post(ticker)
    }

    private fun stopTicker() {
        tickerRunning = false
        main.removeCallbacks(ticker)
    }

    private fun requestFocus(): Boolean {
        if (hasFocus) return true
        hasFocus = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasFocus
    }

    private fun abandonFocus() {
        if (!hasFocus) return
        hasFocus = false
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    private fun ensureService() {
        if (PlaybackService.running) return
        try {
            app.startForegroundService(Intent(app, PlaybackService::class.java))
        } catch (e: Exception) {
            // Android refuses this while the app is in the background; music still plays.
        }
    }

    private fun release(p: MediaPlayer) {
        try {
            p.setOnPreparedListener(null)
            p.setOnCompletionListener(null)
            p.setOnErrorListener(null)
            p.release()
        } catch (e: Exception) {
        }
    }

    private fun releasePlayers() {
        stopTicker()
        active?.let { release(it) }
        incoming?.let { release(it) }
        outgoing?.let { release(it) }
        active = null
        incoming = null
        outgoing = null
        activePrepared = false
        incomingPrepared = false
        incomingIndex = -1
        gaplessArmed = false
    }
}
