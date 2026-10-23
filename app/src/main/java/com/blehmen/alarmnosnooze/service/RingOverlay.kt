package com.blehmen.alarmnosnooze.service

import android.content.Context
import android.graphics.PixelFormat
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.lifecycle.ViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.ViewTreeSavedStateRegistryOwner
import com.blehmen.alarmnosnooze.RingingScreen
import com.blehmen.alarmnosnooze.ui.AppTheme

/** The full-screen alarm pop-up: the ring screen drawn as an overlay window, shown over whatever app is open. */
class RingOverlay(private val ctx: Context) {
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private var view: ComposeView? = null
    private var owner: Owner? = null

    val isShowing get() = view != null

    /** False if Android refused the window (permission taken away): the notification is the fallback. */
    fun show(themeMode: Int, accent: Int, onFinish: () -> Unit): Boolean {
        if (view != null) return true
        val o = Owner().also { it.create() }
        val v = ComposeView(ctx).apply {
            ViewTreeLifecycleOwner.set(this, o)
            ViewTreeSavedStateRegistryOwner.set(this, o)
            ViewTreeViewModelStoreOwner.set(this, o)
            setContent { AppTheme(themeMode, accent) { RingingScreen(Color(accent), onFinish) } }
        }
        val lp = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT,
        ).apply { layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS }
        return try {
            wm.addView(v, lp)
            o.resume()
            view = v
            owner = o
            true
        } catch (_: Exception) {
            o.destroy()
            false
        }
    }

    fun hide() {
        view?.let { try { wm.removeViewImmediate(it) } catch (_: Exception) {} }
        owner?.destroy()
        view = null
        owner = null
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
