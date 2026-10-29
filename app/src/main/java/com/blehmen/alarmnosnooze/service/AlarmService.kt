package com.blehmen.alarmnosnooze.service

import android.app.KeyguardManager
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.blehmen.alarmnosnooze.RingingActivity
import com.blehmen.alarmnosnooze.app
import com.blehmen.alarmnosnooze.data.Alarm
import com.blehmen.alarmnosnooze.engine.AlarmEngine
import com.blehmen.alarmnosnooze.engine.Notifier
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AlarmService : Service() {

    companion object {
        const val ACTION_RING = "com.blehmen.alarmnosnooze.RING"
        const val ACTION_TURN_OFF = "com.blehmen.alarmnosnooze.TURN_OFF"
        const val ACTION_SNOOZE = "com.blehmen.alarmnosnooze.SNOOZE"
        const val ACTION_SILENCE = "com.blehmen.alarmnosnooze.SILENCE"
        const val ACTION_FORCE_STOP = "com.blehmen.alarmnosnooze.FORCE_STOP"
        const val EXTRA_ID = "alarm_id"
        const val EXTRA_KIND = "kind"
        private const val RING_TIMEOUT_MS = 10 * 60_000L

        private val _state = MutableStateFlow<Alarm?>(null)
        /** The alarm currently ringing, or null. Observed by the ring screen and the pop-up. */
        val state: StateFlow<Alarm?> = _state

        fun send(ctx: Context, action: String, id: Int) {
            try {
                ctx.startService(Intent(ctx, AlarmService::class.java).setAction(action).putExtra(EXTRA_ID, id))
            } catch (_: Exception) { /* background start not allowed; nothing is ringing anyway */ }
        }
    }

    /** How this ring is presented. */
    private enum class Mode { OVERLAY, BUSY, PILL, LOCKED }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val engine get() = applicationContext.app.engine
    private val overlay by lazy { RingOverlay(this) }
    private val busy by lazy { BusyDetector(this) }

    private var current: Alarm? = null
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private var rampJob: Job? = null
    private var timeoutJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var screenLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getIntExtra(EXTRA_ID, -1) ?: -1
        when (intent?.action) {
            ACTION_RING -> {
                grabLocks()
                val kind = intent.getIntExtra(EXTRA_KIND, AlarmEngine.REGULAR)
                scope.launch { ring(id, kind) }
            }
            ACTION_TURN_OFF -> scope.launch {
                if (!isCurrent(id)) return@launch
                stopPlayback(); engine.endSession(id); finishRing()
            }
            ACTION_SNOOZE -> scope.launch {
                if (!isCurrent(id)) return@launch
                stopPlayback(); engine.snooze(id); finishRing()
            }
            ACTION_SILENCE -> scope.launch {
                if (!isCurrent(id)) return@launch
                stopPlayback(); engine.silenceAndRepeat(id); finishRing()
            }
            ACTION_FORCE_STOP -> scope.launch {
                if (current?.id == id) finishRing() else if (current == null) stopSelf()
            }
            else -> if (current == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    /** True if this alarm is ringing. Otherwise shuts the service down when idle. */
    private fun isCurrent(id: Int): Boolean {
        if (current?.id == id) return true
        if (current == null) finishRing()
        return false
    }

    private fun foreground(n: Notification) {
        ServiceCompat.startForeground(
            this, Notifier.RING_ID, n,
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        )
    }

    private fun grabLocks() {
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "alarmnosnooze:ring")
                .apply { acquire(RING_TIMEOUT_MS + 60_000L) }
        }
        wakeScreen()
    }

    /** Turns the display on even if the full-screen notification is suppressed. Releases itself after 15 s. */
    @Suppress("DEPRECATION")
    private fun wakeScreen() {
        screenLock = getSystemService(PowerManager::class.java)
            .newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "alarmnosnooze:screen"
            )
            .apply { acquire(15_000L) }
    }

    // ---------------------------------------------------------------- presenting the ring

    private suspend fun ring(id: Int, kind: Int) {
        val prev = current
        stopPlayback()
        hideOverlay()
        if (prev != null && prev.id != id) engine.handleMissed(prev.id)
        Notifier.cancelRepeating(this, id)

        val a = engine.onFire(id, kind)
        if (a == null) { foreground(Notifier.placeholder(this)); finishRing(); return }

        // A ring between midnight and 6 am is "the first alarm in the morning": clear the bedtime reminder.
        // (It also removes itself at 6 am; an alarm the evening before, like 11 pm, leaves it alone.)
        if (java.time.LocalTime.now().hour < 6) Notifier.cancelBedtime(this)

        val mode = chooseMode()
        if (mode != Mode.OVERLAY) busy.release()
        current = a
        _state.value = a
        // Quiet notification when the pop-up covers the screen. Loud (heads-up pill, lock-screen takeover) otherwise.
        foreground(Notifier.ringing(this, a, loud = mode != Mode.OVERLAY))
        startPlayback(a)

        when (mode) {
            Mode.OVERLAY -> {
                if (!showPopup()) foreground(Notifier.ringing(this, a, loud = true))
                busy.release()
            }
            Mode.BUSY -> {} // stays a pill; tapping it opens the full-screen ring screen
            Mode.LOCKED -> try {
                startActivity(Intent(this, RingingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
            } catch (_: Exception) { /* blocked; the full-screen notification handles it */ }
            Mode.PILL -> {}
        }

        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(RING_TIMEOUT_MS)
            current?.let { c -> stopPlayback(); engine.handleMissed(c.id) }
            finishRing()
        }
    }

    private suspend fun chooseMode(): Mode {
        val pm = getSystemService(PowerManager::class.java)
        val km = getSystemService(KeyguardManager::class.java)
        if (!pm.isInteractive || km.isKeyguardLocked) return Mode.LOCKED
        val s = applicationContext.app.settings.current()
        val pillPossible = NotificationManagerCompat.from(this).areNotificationsEnabled()
        if (!s.overlayEnabled || !Settings.canDrawOverlays(this)) {
            // No pop-up allowed. With notifications blocked there is no pill either, so open the ring screen itself.
            return if (pillPossible) Mode.PILL else Mode.LOCKED
        }
        if (s.pillWhenBusy && pillPossible && busy.reason() != null) return Mode.BUSY
        return Mode.OVERLAY
    }

    /**
     * The pop-up. First choice is the real ring screen (an activity), so Home and Back work like in any app and the
     * alarm keeps ringing. Android only allows that from the background while this app has a visible overlay window,
     * so a tiny one is held open for the moment. If the screen does not come up, draw it as an overlay window instead.
     */
    private suspend fun showPopup(): Boolean {
        busy.holdProbe()
        try {
            startActivity(Intent(this, RingingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        } catch (_: Exception) {}
        repeat(8) {
            if (RingingActivity.visible) return true
            delay(100)
        }
        return showOverlay()
    }

    private suspend fun showOverlay(): Boolean {
        val s = applicationContext.app.settings.current()
        return overlay.show(s.themeMode, s.accent) { hideOverlay() }
    }

    private fun hideOverlay() {
        overlay.hide()
        busy.release()
    }

    // ---------------------------------------------------------------- playback

    private fun startPlayback(a: Alarm) {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val audio = getSystemService(AudioManager::class.java)
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(attrs).build()
        audio.requestAudioFocus(focus!!)

        fun make(u: Uri) = MediaPlayer().apply {
            setAudioAttributes(attrs)
            setDataSource(this@AlarmService, u)
            isLooping = true
            prepare()
        }
        val wanted = a.soundUri?.let { Uri.parse(it) } ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        player = try { make(wanted) } catch (e: Exception) {
            try { make(Settings.System.DEFAULT_ALARM_ALERT_URI) } catch (e2: Exception) { null }
        }

        player?.let { p ->
            val target = (a.volume / 100f).coerceIn(0.05f, 1f)
            if (a.rampEnabled) {
                p.setVolume(0.02f, 0.02f)
                rampJob = scope.launch {
                    val steps = (a.rampSeconds * 4).coerceAtLeast(1)
                    for (i in 1..steps) {
                        delay(250)
                        val v = 0.02f + (target - 0.02f) * i / steps
                        player?.setVolume(v, v)
                    }
                }
            } else {
                p.setVolume(target, target)
            }
            p.start()
        }

        if (a.vibrate) {
            @Suppress("DEPRECATION")
            vibrator()?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 800), 0), attrs)
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) getSystemService(VibratorManager::class.java).defaultVibrator
        else getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    private fun stopPlayback() {
        rampJob?.cancel(); rampJob = null
        player?.let { try { it.stop() } catch (_: Exception) {}; it.release() }
        player = null
        focus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focus = null
        vibrator()?.cancel()
    }

    private fun finishRing() {
        stopPlayback()
        hideOverlay()
        timeoutJob?.cancel(); timeoutJob = null
        current = null
        _state.value = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        screenLock?.let { if (it.isHeld) it.release() }
        screenLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopPlayback()
        hideOverlay()
        scope.cancel()
        _state.value = null
        super.onDestroy()
    }
}
