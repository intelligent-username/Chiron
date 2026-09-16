package com.chiron.feature.timer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.chiron.core.common.UserSettingsRepository
import com.chiron.core.ui.R
import com.chiron.feature.timer.metronome.ExternalMediaManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class MetronomeService : Service() {

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var notificationManager: NotificationManager
    private lateinit var userSettingsRepository: UserSettingsRepository
    private var tickJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentTickAsset: String? = null
    private var isControlsEnabled: Boolean = false
    private var isCustomizeSingleTap: Boolean = false
    private var isCustomizeDoubleTap: Boolean = false
    private var isCustomizeTripleTap: Boolean = false

    // MediaPlayer must be created/used on a thread with a Looper (its callbacks are
    // delivered there). Building it on the main thread every beat is what caused the
    // notification-shade jank, so the whole tick loop runs on a dedicated background thread.
    private val tickThread = HandlerThread("MetronomeTicks").apply { start() }
    private val tickScope = CoroutineScope(SupervisorJob() + Handler(tickThread.looper).asCoroutineDispatcher())

    // Only used for UI/notification work.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        userSettingsRepository = UserSettingsRepository(applicationContext)

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Metronome",
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)

        mediaSession = MediaSessionCompat(this, "MetronomeSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    if (isCustomizeSingleTap) {
                        MetronomeController.start()
                    } else {
                        ExternalMediaManager.togglePlayPause(this@MetronomeService)
                    }
                }
                override fun onPause() {
                    if (isCustomizeSingleTap) {
                        MetronomeController.pause()
                    } else {
                        ExternalMediaManager.togglePlayPause(this@MetronomeService)
                    }
                }
                override fun onStop() {
                    MetronomeController.stop()
                }
                override fun onSkipToNext() {
                    if (isCustomizeDoubleTap) {
                        ExternalMediaManager.skipToNext(this@MetronomeService)
                    }
                }
                override fun onSkipToPrevious() {
                    if (isCustomizeTripleTap) {
                        ExternalMediaManager.skipToPrevious(this@MetronomeService)
                    }
                }
                override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
                    val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                    }
                    if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                        when (keyEvent.keyCode) {
                            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                                if (isCustomizeDoubleTap) {
                                    ExternalMediaManager.skipToNext(this@MetronomeService)
                                }
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                                if (isCustomizeTripleTap) {
                                    ExternalMediaManager.skipToPrevious(this@MetronomeService)
                                }
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> {
                                if (isCustomizeSingleTap) {
                                    MetronomeController.toggle()
                                } else {
                                    ExternalMediaManager.togglePlayPause(this@MetronomeService)
                                }
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                                if (isCustomizeSingleTap) {
                                    MetronomeController.start()
                                } else {
                                    ExternalMediaManager.togglePlayPause(this@MetronomeService)
                                }
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                if (isCustomizeSingleTap) {
                                    MetronomeController.pause()
                                } else {
                                    ExternalMediaManager.togglePlayPause(this@MetronomeService)
                                }
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_STOP -> {
                                MetronomeController.stop()
                                return true
                            }
                        }
                    }
                    return super.onMediaButtonEvent(mediaButtonEvent)
                }
            })
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
        }

        val settingsFlow = combine(
            userSettingsRepository.metronomeControlsEnabledFlow,
            userSettingsRepository.metronomeCustomizeSingleTapFlow,
            userSettingsRepository.metronomeCustomizeDoubleTapFlow,
            userSettingsRepository.metronomeCustomizeTripleTapFlow
        ) { controlsEnabled, customSingle, customDouble, customTriple ->
            SettingsConfig(controlsEnabled, customSingle, customDouble, customTriple)
        }

        serviceScope.launch {
            combine(
                MetronomeController.isRunning,
                MetronomeController.bpm,
                settingsFlow
            ) { running, bpm, settings ->
                MetronomeConfigState(
                    running = running,
                    bpm = bpm,
                    controlsEnabled = settings.controlsEnabled,
                    customSingle = settings.customSingle,
                    customDouble = settings.customDouble,
                    customTriple = settings.customTriple
                )
            }
                .distinctUntilChanged()
                .collect { config ->
                    isControlsEnabled = config.controlsEnabled
                    isCustomizeSingleTap = config.customSingle
                    isCustomizeDoubleTap = config.customDouble
                    isCustomizeTripleTap = config.customTriple

                    if (config.running) startTickLoop() else { tickJob?.cancel(); tickJob = null }
                    updateSessionAndNotification(config.running, config.bpm, config.controlsEnabled)
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

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        MetronomeController.stop()
        stopSelf()
    }

    private fun startTickLoop() {
        tickJob?.cancel()
        tickJob = tickScope.launch {
            while (MetronomeController.isRunning.value) {
                playTick(MetronomeController.tickAsset.value)
                delay(60_000L / MetronomeController.bpm.value.coerceAtLeast(1))
            }
        }
    }

    private fun playTick(fileName: String) {
        try {
            // Reuse a single MediaPlayer: only (re)build it when the tick asset changes,
            // then just rewind and replay. Creating a new MediaPlayer every beat is what
            // caused the notification-shade jank.
            if (mediaPlayer == null || currentTickAsset != fileName) {
                mediaPlayer?.release()
                val afd = assets.openFd("audio/$fileName")
                val player = MediaPlayer()
                player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                player.prepare()
                mediaPlayer = player
                currentTickAsset = fileName
            }
            mediaPlayer?.apply {
                seekTo(0)
                start()
            }
        } catch (_: Exception) {
        }
    }

    private fun updateSessionAndNotification(running: Boolean, bpm: Int, controlsEnabled: Boolean = isControlsEnabled) {
        if (controlsEnabled) {
            mediaSession.isActive = true
            mediaSession.setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(
                        if (running) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                        0,
                        1f
                    )
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_PLAY_PAUSE or
                            PlaybackStateCompat.ACTION_STOP or
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                    )
                    .build()
            )
            mediaSession.setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Metronome")
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "BPM $bpm")
                    .build()
            )
        } else {
            mediaSession.isActive = false
        }
        notificationManager.notify(NOTIFICATION_ID, buildNotification(controlsEnabled))
    }

    private fun buildNotification(controlsEnabled: Boolean = isControlsEnabled): android.app.Notification {
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

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_metronome)
            .setColor(0xFF4F46E5.toInt())
            .setContentTitle("Metronome")
            .setContentText("BPM ${MetronomeController.bpm.value}")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(icon, "Play/Pause", pi)

        if (controlsEnabled) {
            builder.setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0)
            )
        }

        return builder.build()
    }

    override fun onDestroy() {
        tickJob?.cancel()
        serviceScope.cancel()
        tickScope.cancel()
        tickThread.quitSafely()
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

private data class MetronomeConfigState(
    val running: Boolean,
    val bpm: Int,
    val controlsEnabled: Boolean,
    val customSingle: Boolean,
    val customDouble: Boolean,
    val customTriple: Boolean
)

private data class SettingsConfig(
    val controlsEnabled: Boolean,
    val customSingle: Boolean,
    val customDouble: Boolean,
    val customTriple: Boolean
)