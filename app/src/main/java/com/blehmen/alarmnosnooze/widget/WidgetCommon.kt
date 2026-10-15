package com.blehmen.alarmnosnooze.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.blehmen.alarmnosnooze.MainActivity
import com.blehmen.alarmnosnooze.app

val WidgetBg = Color(0xFF1A1A1A)
val WidgetPill = Color(0xFF3A3A3A)
val WidgetMuted = Color(0xFF9E9E9E)

fun cp(c: Color) = ColorProvider(c)

fun onColorFor(c: Color): Color =
    if (0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue > 0.5f) Color.Black else Color.White

/** Opens the app on a tab. The action string keeps each widget PendingIntent distinct. */
fun openApp(ctx: Context, tab: Int, quick: Boolean = false): Action = actionStartActivity(
    Intent(ctx, MainActivity::class.java)
        .setAction("com.blehmen.alarmnosnooze.WIDGET_OPEN_${tab}_$quick")
        .putExtra(MainActivity.EXTRA_TAB, tab)
        .putExtra(MainActivity.EXTRA_QUICK, quick)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
)

@Composable
fun Pill(text: String, accent: Color?, onClick: Action, modifier: GlanceModifier = GlanceModifier) {
    val bg = accent ?: WidgetPill
    val fg = if (accent != null) onColorFor(accent) else Color.White
    Box(
        modifier.background(cp(bg)).cornerRadius(18.dp).padding(horizontal = 14.dp, vertical = 8.dp).clickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TextStyle(color = cp(fg), fontSize = 14.sp, fontWeight = FontWeight.Bold))
    }
}

// ------------------------------------------------------------------ callbacks (run in the app process)

class ToggleAlarmCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[WidgetKeys.AlarmId] ?: return
        val a = context.app.db.alarmDao().get(id) ?: return
        context.app.engine.setEnabled(id, !a.enabled)
    }
}

class StartPresetCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val sec = parameters[WidgetKeys.Seconds] ?: return
        context.app.timerRepo.setLast(sec)
        context.app.timerEngine.start(sec * 1000L)
    }
}

class TimerPauseCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        context.app.timerEngine.pause()
}

class TimerResumeCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        context.app.timerEngine.resume()
}

class TimerCancelCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        context.app.timerEngine.cancel()
}

class TimerStopCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        context.app.timerEngine.stopRinging()
}

class StopwatchToggleCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        context.app.stopwatchEngine.toggle()
}

class StopwatchLapCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        context.app.stopwatchEngine.lap()
}

class StopwatchResetCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        context.app.stopwatchEngine.reset()
}

object WidgetKeys {
    val AlarmId = ActionParameters.Key<Int>("alarm_id")
    val Seconds = ActionParameters.Key<Int>("seconds")
}
