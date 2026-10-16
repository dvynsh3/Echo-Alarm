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
import com.blehmen.alarmnosnooze.data.StopwatchState

class StopwatchWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = context.app.timerRepo.stopwatchNow()
        val accent = Color(context.app.settings.current().accent)
        provideContent { StopwatchWidgetContent(state, accent) }
    }
}

class StopwatchWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StopwatchWidget()
}

private fun plain(ms: Long): String {
    val total = ms.coerceAtLeast(0) / 1000
    val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

@Composable
private fun StopwatchWidgetContent(s: StopwatchState, accent: Color) {
    val ctx = LocalContext.current
    val now = System.currentTimeMillis()
    val elapsed = s.elapsed(now)
    Column(
        GlanceModifier.fillMaxSize().background(cp(WidgetBg)).cornerRadius(24.dp).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (s.laps.isEmpty()) "Stopwatch" else "Stopwatch · ${s.laps.size} laps",
            modifier = GlanceModifier.clickable(openApp(ctx, 1)),
            style = TextStyle(color = cp(WidgetMuted), fontSize = 12.sp),
        )
        if (s.running) {
            val rv = RemoteViews(ctx.packageName, R.layout.widget_chronometer).apply {
                setChronometer(R.id.chrono, SystemClock.elapsedRealtime() - elapsed, null, true)
                setChronometerCountDown(R.id.chrono, false)
            }
            AndroidRemoteViews(rv, GlanceModifier.defaultWeight())
        } else {
            Box(GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                Text(
                    plain(elapsed),
                    style = TextStyle(color = cp(if (elapsed > 0) Color.White else WidgetMuted), fontSize = 34.sp, fontWeight = FontWeight.Medium),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                s.running -> Pill("Lap", null, actionRunCallback<StopwatchLapCallback>())
                elapsed > 0 -> Pill("Reset", null, actionRunCallback<StopwatchResetCallback>())
            }
            if (s.running || elapsed > 0) Spacer(GlanceModifier.width(8.dp))
            Pill(
                when { s.running -> "Stop"; elapsed > 0 -> "Resume"; else -> "Start" },
                accent, actionRunCallback<StopwatchToggleCallback>(),
            )
        }
    }
}
