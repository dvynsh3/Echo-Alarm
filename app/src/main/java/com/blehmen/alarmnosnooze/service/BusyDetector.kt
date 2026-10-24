package com.blehmen.alarmnosnooze.service

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.delay

/**
 * Decides whether the phone is "busy", so a ringing alarm should be a small notification instead of a full-screen
 * pop-up. Calls, meetings and the microphone are reliable. Full-screen and keyboard are read from a 1-pixel
 * invisible overlay and are best effort: when the phone gives no answer, the result is "not busy".
 */
class BusyDetector(private val ctx: Context) {
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private var probe: View? = null

    /** The reason the phone is busy, or null when the pop-up is fine. */
    suspend fun reason(): String? {
        audio()?.let { return it }
        display()?.let { return it }
        car()?.let { return it }
        return insets()
    }

    private fun audio(): String? {
        val am = ctx.getSystemService(AudioManager::class.java)
        when (am.mode) {
            AudioManager.MODE_IN_CALL, AudioManager.MODE_RINGTONE, AudioManager.MODE_CALL_SCREENING,
            AudioManager.MODE_IN_COMMUNICATION -> return "call or meeting"   // phone calls and VoIP: Teams, Zoom, Meet, WhatsApp...
        }
        if (am.activeRecordingConfigurations.isNotEmpty()) return "microphone in use" // recorder, dictation, meeting audio
        return null
    }

    /** Casting, an external monitor or Samsung DeX shows up as an extra display that is switched on. */
    private fun display(): String? {
        val dm = ctx.getSystemService(DisplayManager::class.java)
        val extra = dm.displays.any { it.displayId != Display.DEFAULT_DISPLAY && it.state == Display.STATE_ON }
        return if (extra) "external display or casting" else null
    }

    private fun car(): String? {
        val ui = ctx.getSystemService(UiModeManager::class.java)
        return if (ui.currentModeType == Configuration.UI_MODE_TYPE_CAR) "car mode" else null
    }

    private suspend fun insets(): String? {
        if (probe == null) addProbe()
        val v = probe ?: return null
        val i = ViewCompat.getRootWindowInsets(v) ?: return null
        // A status bar exists but is hidden: some app is full-screen or immersive.
        val barHeight = i.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars()).top
        if (barHeight > 0 && !i.isVisible(WindowInsetsCompat.Type.statusBars())) return "full-screen app"
        if (i.isVisible(WindowInsetsCompat.Type.ime())) return "keyboard open"
        return null
    }

    private suspend fun addProbe() {
        if (!Settings.canDrawOverlays(ctx)) return
        val v = View(ctx).apply { setBackgroundColor(0x01000000) } // nearly invisible, but a real drawn window
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, 1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        try { wm.addView(v, lp); probe = v } catch (_: Exception) { return }
        delay(150) // let the first insets arrive
    }

    /** Makes sure the 1-pixel overlay window exists. While it does, Android lets this app open its ring screen. */
    suspend fun holdProbe() { if (probe == null) addProbe() }

    fun release() {
        probe?.let { try { wm.removeViewImmediate(it) } catch (_: Exception) {} }
        probe = null
    }
}
