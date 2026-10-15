package com.blehmen.alarmnosnooze.widget

import android.content.Context
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.blehmen.alarmnosnooze.R
import com.blehmen.alarmnosnooze.app
import com.blehmen.alarmnosnooze.data.Phase
import com.blehmen.alarmnosnooze.data.TimerState
import com.blehmen.alarmnosnooze.engine.fmtTimer

class TimerWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = context.app.timerRepo.timerNow()
        val accent = Color(context.app.settings.current().accent)
        provideContent { TimerWidgetContent(state, accent) }
    }
}

class TimerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TimerWidget()
}

private fun presetShort(sec: Int): String = when {
    sec % 3600 == 0 -> "${sec / 3600}h"
    sec % 60 == 0 -> "${sec / 60} min"
    else -> fmtTimer(sec * 1000L)
}

@Composable
private fun TimerWidgetContent(s: TimerState, accent: Color) {
    val ctx = LocalContext.current
    val white = cp(Color.White)
    Column(
        GlanceModifier.fillMaxSize().background(cp(WidgetBg)).cornerRadius(24.dp).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Timer", modifier = GlanceModifier.clickable(openApp(ctx, 3)),
            style = TextStyle(color = cp(WidgetMuted), fontSize = 12.sp))
        Spacer(GlanceModifier.height(4.dp))
        when (s.phase) {
            Phase.IDLE -> {
                s.presets.forEach { sec ->
                    Pill(
                        presetShort(sec), null,
                        actionRunCallback<StartPresetCallback>(actionParametersOf(WidgetKeys.Seconds to sec)),
                        GlanceModifier.fillMaxWidth().padding(vertical = 2.dp),
                    )
                }
            }
            Phase.RUNNING -> {
                // A real Chronometer keeps counting down with no widget updates.
                val rv = RemoteViews(ctx.packageName, R.layout.widget_chronometer).apply {
                    val base = SystemClock.elapsedRealtime() + (s.endAt - System.currentTimeMillis())
                    setChronometer(R.id.chrono, base, null, true)
                    setChronometerCountDown(R.id.chrono, true)
                }
                AndroidRemoteViews(rv, GlanceModifier.defaultWeight())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill("Pause", accent, actionRunCallback<TimerPauseCallback>())
                    Spacer(GlanceModifier.width(8.dp))
                    Pill("Cancel", null, actionRunCallback<TimerCancelCallback>())
                }
            }
            Phase.PAUSED -> {
                Box(GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                    Text(fmtTimer(s.remainingMs), style = TextStyle(color = cp(WidgetMuted), fontSize = 34.sp, fontWeight = FontWeight.Medium))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill("Resume", accent, actionRunCallback<TimerResumeCallback>())
                    Spacer(GlanceModifier.width(8.dp))
                    Pill("Cancel", null, actionRunCallback<TimerCancelCallback>())
                }
            }
            else -> {
                Box(GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                    Text("Time's up", style = TextStyle(color = white, fontSize = 26.sp, fontWeight = FontWeight.Bold))
                }
                Pill("Stop", accent, actionRunCallback<TimerStopCallback>())
            }
        }
    }
}
