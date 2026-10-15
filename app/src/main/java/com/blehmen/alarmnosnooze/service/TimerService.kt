package com.blehmen.alarmnosnooze.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.*
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.core.app.ServiceCompat
import com.blehmen.alarmnosnooze.app
import com.blehmen.alarmnosnooze.engine.TimerNotifier
import kotlinx.coroutines.*

/** Plays the alarm sound and vibrates while a finished timer is ringing. */
class TimerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this, TimerNotifier.ID_RING, TimerNotifier.ringing(this),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        )
        if (!started) {
            started = true
            startRinging()
            scope.launch {
                delay(5 * 60_000L)
                applicationContext.app.timerEngine.stopRinging()
            }
        }
        return START_NOT_STICKY
    }

    private fun startRinging() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val audio = getSystemService(AudioManager::class.java)
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE).setAudioAttributes(attrs).build()
        audio.requestAudioFocus(focus!!)

        fun make(u: android.net.Uri) = MediaPlayer().apply {
            setAudioAttributes(attrs)
            setDataSource(this@TimerService, u)
            isLooping = true
            prepare()
        }
        player = try { make(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)) } catch (e: Exception) {
            try { make(Settings.System.DEFAULT_ALARM_ALERT_URI) } catch (e2: Exception) { null }
        }
        player?.start()
        @Suppress("DEPRECATION")
        vibrator()?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 600), 0), attrs)
    }

    @Suppress("DEPRECATION")
    private fun vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) getSystemService(VibratorManager::class.java).defaultVibrator
        else getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    override fun onDestroy() {
        scope.cancel()
        player?.let { try { it.stop() } catch (_: Exception) {}; it.release() }
        player = null
        focus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        vibrator()?.cancel()
        super.onDestroy()
    }
}
