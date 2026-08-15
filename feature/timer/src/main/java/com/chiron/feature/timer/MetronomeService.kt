package com.chiron.feature.timer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.chiron.core.ui.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MetronomeService : Service() {

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var notificationManager: NotificationManager
    private var tickJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Metronome",
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)

        mediaSession = MediaSessionCompat(this, "MetronomeSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { MetronomeController.start() }
                override fun onPause() { MetronomeController.pause() }
                override fun onStop() { MetronomeController.stop() }
            })
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
        }

        serviceScope.launch {
            combine(MetronomeController.isRunning, MetronomeController.bpm) { running, bpm -> running to bpm }
                .collect { (running, bpm) ->
                    if (running) startTickLoop() else { tickJob?.cancel(); tickJob = null }
                    updateSessionAndNotification(running, bpm)
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            MetronomeController.ACTION_START -> MetronomeController.start()
            MetronomeController.ACTION_PAUSE -> MetronomeController.pause()
            MetronomeController.ACTION_STOP -> MetronomeController.stop()
            MetronomeController.ACTION_TOGGLE -> MetronomeController.toggle()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }

        if (MetronomeController.isRunning.value) startTickLoop() else { tickJob?.cancel(); tickJob = null }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startTickLoop() {
        tickJob?.cancel()
        tickJob = serviceScope.launch {
            while (MetronomeController.isRunning.value) {
                playTick(MetronomeController.tickAsset.value)
                delay(60_000L / MetronomeController.bpm.value.coerceAtLeast(1))
            }
        }
    }

    private fun playTick(fileName: String) {
        try {
            // Release any previous in-flight player so ticks never overlap or leak.
            mediaPlayer?.release()
            val afd = assets.openFd("audio/$fileName")
            val player = MediaPlayer()
            player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            player.setOnPreparedListener { it.start() }
            player.setOnCompletionListener { it.release() }
            player.prepareAsync()
            mediaPlayer = player
        } catch (_: Exception) {
        }
    }

    private fun updateSessionAndNotification(running: Boolean, bpm: Int) {
        mediaSession.isActive = true
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(
                    if (running) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    0,
                    1f
                )
                .build()
        )
        mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Metronome")
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "BPM $bpm")
                .build()
        )
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): android.app.Notification {
        val intent = Intent(this, MetronomeService::class.java)
            .setAction(MetronomeController.ACTION_TOGGLE)
        val pi = PendingIntent.getService(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val icon = if (MetronomeController.isRunning.value) {
            R.drawable.ic_media_pause
        } else {
            R.drawable.ic_media_play
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Metronome")
            .setContentText("BPM ${MetronomeController.bpm.value}")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0)
            )
            .addAction(icon, "Play/Pause", pi)
            .build()
    }

    override fun onDestroy() {
        tickJob?.cancel()
        serviceScope.cancel()
        mediaPlayer?.release()
        mediaSession.release()
        notificationManager.cancel(NOTIFICATION_ID)
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "metronome"
    }
}