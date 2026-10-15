package com.blehmen.alarmnosnooze.widget

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.Switch
import androidx.glance.appwidget.SwitchDefaults
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.blehmen.alarmnosnooze.app
import com.blehmen.alarmnosnooze.data.Alarm
import com.blehmen.alarmnosnooze.data.Days
import com.blehmen.alarmnosnooze.engine.timeParts
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AlarmWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.app
        val alarms = app.db.alarmDao().getAll()
            .sortedWith(compareByDescending<Alarm> { it.pinned }.thenBy { it.sortOrder }.thenBy { it.id })
        val accent = Color(app.settings.current().accent)
        val is24 = DateFormat.is24HourFormat(context)
        provideContent { AlarmWidgetContent(alarms, accent, is24) }
    }
}

class AlarmWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AlarmWidget()
}

private fun timeText(h: Int, m: Int, is24: Boolean): String {
    val (t, ap) = timeParts(h, m, is24)
    return if (ap.isEmpty()) t else "$t $ap"
}

private fun whenText(a: Alarm): String {
    if (a.repeatDays != 0) return Days.summary(a.repeatDays)
    val d: LocalDate = Instant.ofEpochMilli(a.nextTriggerAt).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when (d) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> d.format(DateTimeFormatter.ofPattern("EEE, d MMM"))
    }
}

@Composable
private fun AlarmWidgetContent(alarms: List<Alarm>, accent: Color, is24: Boolean) {
    val ctx = LocalContext.current
    val now = System.currentTimeMillis()
    val next = alarms.filter { it.enabled && it.nextTriggerAt > now }.minByOrNull { it.nextTriggerAt }
    val white = cp(Color.White)

    Column(GlanceModifier.fillMaxSize().background(cp(WidgetBg)).cornerRadius(24.dp).padding(14.dp)) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight().clickable(openApp(ctx, 0))) {
                if (next == null) {
                    Text("All alarms are off", style = TextStyle(color = white, fontSize = 18.sp, fontWeight = FontWeight.Medium))
                } else {
                    Text(timeText(next.hour, next.minute, is24), style = TextStyle(color = white, fontSize = 30.sp, fontWeight = FontWeight.Medium))
                    Text(
                        (if (next.label.isBlank()) "" else next.label + " · ") + whenText(next),
                        style = TextStyle(color = cp(WidgetMuted), fontSize = 13.sp),
                    )
                }
            }
            Box(
                GlanceModifier.size(44.dp).background(cp(accent)).cornerRadius(22.dp).clickable(openApp(ctx, 0, quick = true)),
                contentAlignment = Alignment.Center,
            ) {
                Text("+", style = TextStyle(color = cp(onColorFor(accent)), fontSize = 26.sp, fontWeight = FontWeight.Bold))
            }
        }
        Spacer(GlanceModifier.height(8.dp))
        Box(GlanceModifier.defaultWeight().fillMaxWidth()) {
            if (alarms.isEmpty()) {
                Text("No alarms yet. Tap + to set a quick one.", style = TextStyle(color = cp(WidgetMuted), fontSize = 13.sp))
            } else {
                LazyColumn(GlanceModifier.fillMaxSize()) {
                    items(alarms, itemId = { it.id.toLong() }) { a ->
                        Row(GlanceModifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(GlanceModifier.defaultWeight().clickable(openApp(ctx, 0))) {
                                Text(
                                    timeText(a.hour, a.minute, is24),
                                    style = TextStyle(
                                        color = cp(if (a.enabled) Color.White else WidgetMuted),
                                        fontSize = 20.sp, fontWeight = FontWeight.Medium,
                                    ),
                                )
                                val sub = (if (a.label.isBlank()) "" else a.label + " · ") +
                                    (if (a.repeatDays != 0) Days.summary(a.repeatDays) else "Once")
                                Text(sub, style = TextStyle(color = cp(WidgetMuted), fontSize = 12.sp))
                            }
                            Switch(
                                checked = a.enabled,
                                onCheckedChange = actionRunCallback<ToggleAlarmCallback>(actionParametersOf(WidgetKeys.AlarmId to a.id)),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = cp(Color.White),
                                    checkedTrackColor = cp(accent),
                                    uncheckedThumbColor = cp(Color(0xFFBDBDBD)),
                                    uncheckedTrackColor = cp(Color(0xFF4A4A4A)),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
