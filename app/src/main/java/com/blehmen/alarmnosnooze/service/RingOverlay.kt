package com.blehmen.alarmnosnooze.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.blehmen.alarmnosnooze.RingingScreen
import com.blehmen.alarmnosnooze.ui.AppTheme

/**
 * Fallback pop-up: the ring screen drawn as an overlay window, used only if the real ring screen could not be opened.
 * Back and Home hide it without stopping the alarm (it keeps ringing; the notification and the app can turn it off).
 */
class RingOverlay(private val ctx: Context) {
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private var root: FrameLayout? = null
    private var owner: Owner? = null
    private var homeReceiver: BroadcastReceiver? = null

    val isShowing get() = root != null

    /** False if Android refused the window (permission taken away): the notification is the fallback. */
    fun show(themeMode: Int, accent: Int, onFinish: () -> Unit): Boolean {
        if (root != null) return true
        val o = Owner().also { it.create() }
        val frame = KeyFrame(ctx) { hide() }.apply {
            setViewTreeLifecycleOwner(o)
            setViewTreeSavedStateRegistryOwner(o)
            setViewTreeViewModelStoreOwner(o)
            addView(ComposeView(ctx).apply {
                setContent { AppTheme(themeMode, accent) { RingingScreen(Color(accent), onFinish) } }
            })
        }
        // Focusable on purpose, so it can hear the Back button.
        val lp = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT,
        ).apply { layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS }
        return try {
            wm.addView(frame, lp)
            frame.requestFocus()
            o.resume()
            root = frame
            owner = o
            listenForHome()
            true
        } catch (_: Exception) {
            o.destroy()
            false
        }
    }

    /** Home and Recents are announced with this broadcast on most phones. Best effort. */
    @Suppress("DEPRECATION")
    private fun listenForHome() {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val reason = i.getStringExtra("reason")
                if (reason == "homekey" || reason == "recentapps") hide()
            }
        }
        try {
            ContextCompat.registerReceiver(ctx, r, IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS), ContextCompat.RECEIVER_NOT_EXPORTED)
            homeReceiver = r
        } catch (_: Exception) {}
    }

    fun hide() {
        homeReceiver?.let { try { ctx.unregisterReceiver(it) } catch (_: Exception) {} }
        homeReceiver = null
        root?.let { try { wm.removeViewImmediate(it) } catch (_: Exception) {} }
        owner?.destroy()
        root = null
        owner = null
    }

    private class KeyFrame(ctx: Context, private val onBack: () -> Unit) : FrameLayout(ctx) {
        init { isFocusable = true; isFocusableInTouchMode = true }
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) onBack()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
    }

    /** Compose needs lifecycle, saved-state and view-model owners; a service window has none, so supply them. */
    private class Owner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
        private val registry = LifecycleRegistry(this)
        private val saved = SavedStateRegistryController.create(this)
        private val store = ViewModelStore()
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
        override val viewModelStore: ViewModelStore get() = store
        fun create() { saved.performRestore(null); registry.currentState = Lifecycle.State.CREATED }
        fun resume() { registry.currentState = Lifecycle.State.RESUMED }
        fun destroy() { registry.currentState = Lifecycle.State.DESTROYED; store.clear() }
    }
}
