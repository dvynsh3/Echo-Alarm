package com.blehmen.alarmnosnooze.engine

import com.blehmen.alarmnosnooze.data.Alarm
import com.blehmen.alarmnosnooze.data.Reminder
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

fun nextOccurrence(
    hour: Int, minute: Int, days: Int,
    from: ZonedDateTime = ZonedDateTime.now(),
): ZonedDateTime {
    val base = from.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
    for (i in 0..7) {
        val c = base.plusDays(i.toLong())
        if (c.isAfter(from) && (days == 0 || (days and (1 shl (c.dayOfWeek.value - 1))) != 0)) return c
    }
    return base.plusDays(1)
}

/** When the current ringing session must stop repeating, or null if no end time is set. */
fun endAtMillis(a: Alarm): Long? {
    val end = a.endMinutes ?: return null
    val start = Instant.ofEpochMilli(a.sessionStart).atZone(ZoneId.systemDefault())
    var e = start.toLocalDate().atStartOfDay(start.zone).plusMinutes(end.toLong())
    if (!e.isAfter(start)) e = e.plusDays(1)
    return e.toInstant().toEpochMilli()
}

fun timeParts(h: Int, m: Int, is24: Boolean): Pair<String, String> =
    if (is24) "%02d:%02d".format(h, m) to ""
    else "${(h + 11) % 12 + 1}:${"%02d".format(m)}" to (if (h < 12) "am" else "pm")

/** Effective fire time of a reminder (uses the default time when none was set). */
fun reminderTriggerAt(r: Reminder, defaultMinutes: Int): Long {
    val h = if (r.hasTime) r.hour else defaultMinutes / 60
    val m = if (r.hasTime) r.minute else defaultMinutes % 60
    return LocalDate.ofEpochDay(r.epochDay).atTime(h, m).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

/** Countdown text, rounding up so it never shows 00:00 while time remains. */
fun fmtTimer(ms: Long): String {
    val total = (ms.coerceAtLeast(0) + 999) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

fun fmtStopwatch(ms: Long): String {
    val v = ms.coerceAtLeast(0)
    val cs = (v / 10) % 100
    val s = (v / 1000) % 60
    val m = (v / 60_000) % 60
    val h = v / 3_600_000
    return if (h > 0) "%d:%02d:%02d.%02d".format(h, m, s, cs) else "%02d:%02d.%02d".format(m, s, cs)
}
