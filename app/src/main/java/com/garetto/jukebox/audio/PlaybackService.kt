package com.garetto.jukebox.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import android.util.Size
import com.garetto.jukebox.MainActivity
import com.garetto.jukebox.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Keeps music playing when the app is in the background, and provides the
 * lock-screen / notification controls and headset button support.
 */
class PlaybackService : Service() {

    companion object {
        @Volatile
        var running = false

        private const val CHANNEL = "playback"
        private const val NOTIFICATION_ID = 999
        private const val ACTION_TOGGLE = "com.garetto.jukebox.TOGGLE"
        private const val ACTION_NEXT = "com.garetto.jukebox.NEXT"
        private const val ACTION_PREV = "com.garetto.jukebox.PREV"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var session: MediaSession
    private lateinit var notifications: NotificationManager
    private var art: Bitmap? = null
    private var artFor = -1L
    private var inForeground = false

    override fun onCreate() {
        super.onCreate()
        running = true
        PlayerEngine.init(this)

        notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, "Playback", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Now playing controls"
                setShowBadge(false)
            }
        )

        session = MediaSession(this, "GarettosJukebox")
        session.setCallback(object : MediaSession.Callback() {
            override fun onPlay() = PlayerEngine.resume()
            override fun onPause() = PlayerEngine.pause()
            override fun onSkipToNext() = PlayerEngine.next()
            override fun onSkipToPrevious() = PlayerEngine.prev()
            override fun onSeekTo(pos: Long) = PlayerEngine.seekTo(pos)
            override fun onStop() = PlayerEngine.pause()
        })
        session.isActive = true

        scope.launch {
            PlayerEngine.state.collect { s -> onState(s) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> PlayerEngine.toggle()
            ACTION_NEXT -> PlayerEngine.next()
            ACTION_PREV -> PlayerEngine.prev()
            else -> publish(PlayerEngine.state.value, forceForeground = true)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!PlayerEngine.state.value.isPlaying) stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        session.isActive = false
        session.release()
        notifications.cancel(NOTIFICATION_ID)
        super.onDestroy()
    }

    private fun onState(s: PlayerState) {
        val song = s.song
        if (song != null && song.id != artFor) {
            artFor = song.id
            art = null
            scope.launch {
                val bmp = withContext(Dispatchers.IO) {
                    try {
                        contentResolver.loadThumbnail(song.uri, Size(512, 512), null)
                    } catch (e: Exception) {
                        null
                    }
                }
                if (artFor == song.id && bmp != null) {
                    art = bmp
                    publish(PlayerEngine.state.value, forceForeground = false)
                }
            }
        }
        publish(s, forceForeground = false)
    }

    private fun publish(s: PlayerState, forceForeground: Boolean) {
        val song = s.song

        val meta = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, song?.title ?: getString(R.string.app_name))
            .putString(MediaMetadata.METADATA_KEY_ARTIST, song?.artist ?: "")
            .putString(MediaMetadata.METADATA_KEY_ALBUM, song?.album ?: "")
            .putLong(MediaMetadata.METADATA_KEY_DURATION, s.durationMs)
        art?.let { meta.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) }
        session.setMetadata(meta.build())

        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SEEK_TO or
                        PlaybackState.ACTION_STOP
                )
                .setState(
                    if (s.isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    PlayerEngine.position.value,
                    if (s.isPlaying) 1f else 0f,
                )
                .build()
        )

        val notification = buildNotification(s)
        if (s.isPlaying || forceForeground) {
            try {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                )
                inForeground = true
            } catch (e: Exception) {
                notifications.notify(NOTIFICATION_ID, notification)
            }
        } else {
            if (inForeground) {
                stopForeground(STOP_FOREGROUND_DETACH)
                inForeground = false
            }
            notifications.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun action(icon: Int, title: String, action: String, code: Int): Notification.Action {
        val pi = PendingIntent.getService(
            this,
            code,
            Intent(this, PlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(Icon.createWithResource(this, icon), title, pi).build()
    }

    private fun buildNotification(s: PlayerState): Notification {
        val song = s.song
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_note)
            .setContentTitle(song?.title ?: getString(R.string.app_name))
            .setContentText(song?.artist ?: "")
            .setContentIntent(open)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(s.isPlaying)
            .setShowWhen(false)
            .addAction(action(R.drawable.ic_prev, "Previous", ACTION_PREV, 1))
            .addAction(
                if (s.isPlaying) action(R.drawable.ic_pause, "Pause", ACTION_TOGGLE, 2)
                else action(R.drawable.ic_play, "Play", ACTION_TOGGLE, 2)
            )
            .addAction(action(R.drawable.ic_next, "Next", ACTION_NEXT, 3))
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
        art?.let { builder.setLargeIcon(it) }
        return builder.build()
    }
}
