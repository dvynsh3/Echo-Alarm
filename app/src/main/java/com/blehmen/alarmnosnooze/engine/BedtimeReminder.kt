package com.blehmen.alarmnosnooze.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import com.blehmen.alarmnosnooze.app
import com.blehmen.alarmnosnooze.data.Alarm
import java.time.LocalDate

/** 10:30 pm every night: says what rings tomorrow morning (4:00 am to 11:00 am), or that nothing does. */
object BedtimeReminder {
    const val ACTION = "com.blehmen.alarmnosnooze.BEDTIME"
    private const val HOUR = 22
    private const val MINUTE = 30
    private const val WINDOW_START = 4 * 60
    private const val WINDOW_END = 11 * 60

    private fun pi(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 8801, Intent(ctx, BedtimeReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** Safe to call any time: it just re-registers the next 10:30 pm. */
    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val at = nextOccurrence(HOUR, MINUTE, 0).toInstant().toEpochMilli()
        if (android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms())
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(ctx))
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(ctx))
    }

    suspend fun fire(ctx: Context) {
        schedule(ctx) // next night first, so a crash below can't lose it
        if (!ctx.app.settings.current().bedtimeReminder) return

        val tomorrow = LocalDate.now().plusDays(1)
        val dayBit = 1 shl (tomorrow.dayOfWeek.value - 1)
        val is24 = DateFormat.is24HourFormat(ctx)
        fun timeOf(a: Alarm): String {
            val (t, ap) = timeParts(a.hour, a.minute, is24)
            return if (ap.isEmpty()) t else "$t $ap"
        }
        fun nameOf(a: Alarm) = a.label.ifBlank { "Alarm" }

        val mornings = ctx.app.db.alarmDao().getAll()
            .filter { a ->
                val mins = a.hour * 60 + a.minute
                a.enabled && mins in WINDOW_START..WINDOW_END &&
                    if (a.repeatDays != 0) (a.repeatDays and dayBit) != 0 &&
                        a.skipUntil < tomorrow.atTime(a.hour, a.minute).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                    else nextOccurrence(a.hour, a.minute, 0).toLocalDate() == tomorrow
            }
            .sortedBy { it.hour * 60 + it.minute }

        if (mornings.isEmpty()) {
            Notifier.bedtime(ctx, "No alarm for tomorrow morning", null)
            return
        }
        val first = mornings.first()
        var text = "${nameOf(first)} rings tomorrow morning at ${timeOf(first)}"
        if (mornings.size > 1) {
            text += "\nThen: " + mornings.drop(1).joinToString(", ") { "${nameOf(it)} at ${timeOf(it)}" }
        }
        Notifier.bedtime(ctx, "${nameOf(first)} · ${timeOf(first)}", text)
    }
}
