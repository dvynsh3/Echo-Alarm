package com.blehmen.alarmnosnooze.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import com.blehmen.alarmnosnooze.data.Reminder
import com.blehmen.alarmnosnooze.data.ReminderDao
import com.blehmen.alarmnosnooze.data.SettingsRepo
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ReminderEngine(
    private val ctx: Context,
    private val dao: ReminderDao,
    private val settings: SettingsRepo,
) {
    companion object {
        const val SCHEDULED = 0
        const val ACTIVE = 1
        const val DONE = 2
        const val ACTION_FIRE = "com.blehmen.alarmnosnooze.REMINDER_FIRE"
        const val ACTION_DONE = "com.blehmen.alarmnosnooze.REMINDER_DONE"
        const val EXTRA_ID = "reminder_id"
    }

    private val am = ctx.getSystemService(AlarmManager::class.java)
    private fun now() = System.currentTimeMillis()

    private fun pi(id: Int) = PendingIntent.getBroadcast(
        ctx, 100000 + id,
        Intent(ctx, ReminderReceiver::class.java)
            .setAction(ACTION_FIRE)
            .setData(Uri.parse("alarmnosnooze://reminder/$id"))
            .putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // Not setAlarmClock: reminders shouldn't show the status-bar alarm icon.
    private fun schedule(id: Int, at: Long) {
        val canExact = android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(id))
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(id))
    }

    private fun cancel(id: Int) = am.cancel(pi(id))

    private fun whenText(r: Reminder, defaultMinutes: Int): String {
        val date = LocalDate.ofEpochDay(r.epochDay).format(DateTimeFormatter.ofPattern("EEE, d MMM"))
        val h = if (r.hasTime) r.hour else defaultMinutes / 60
        val m = if (r.hasTime) r.minute else defaultMinutes % 60
        val (t, ap) = timeParts(h, m, DateFormat.is24HourFormat(ctx))
        return "$date · ${if (ap.isEmpty()) t else "$t $ap"}"
    }

    private suspend fun post(r: Reminder) {
        Notifier.reminder(ctx, r, whenText(r, settings.current().reminderDefaultMinutes))
    }

    /** Create or update. Saving always re-arms the reminder. */
    suspend fun save(r: Reminder): Int {
        if (r.id != 0) { cancel(r.id); Notifier.cancelReminder(ctx, r.id) }
        val def = settings.current().reminderDefaultMinutes
        val at = reminderTriggerAt(r, def)
        val clean = r.copy(state = SCHEDULED, triggerAt = at, title = r.title.trim().ifBlank { "Reminder" })
        val id = if (r.id == 0) dao.insert(clean).toInt() else { dao.update(clean); r.id }
        schedule(id, at)
        return id
    }

    suspend fun delete(id: Int) {
        cancel(id)
        Notifier.cancelReminder(ctx, id)
        dao.deleteById(id)
    }

    suspend fun fire(id: Int) {
        val r = dao.get(id) ?: return
        if (r.state == DONE) return
        val u = r.copy(state = ACTIVE)
        dao.update(u)
        post(u)
    }

    /** Notification cleared or Turn off tapped: stays in the list, greyed out. */
    suspend fun done(id: Int) {
        val r = dao.get(id) ?: return
        cancel(id)
        Notifier.cancelReminder(ctx, id)
        if (r.state != DONE) dao.update(r.copy(state = DONE))
    }

    /** Time/timezone/default-time change: recompute pending reminders. */
    suspend fun rescheduleAll() {
        val def = settings.current().reminderDefaultMinutes
        for (r in dao.getAll()) {
            if (r.state != SCHEDULED) continue
            val at = reminderTriggerAt(r, def)
            dao.update(r.copy(triggerAt = at))
            if (at <= now()) fire(r.id) else schedule(r.id, at)
        }
    }

    /** After reboot: re-arm pending ones, fire overdue ones late, re-post active notifications. */
    suspend fun onBoot() {
        rescheduleAll()
        for (r in dao.getAll()) if (r.state == ACTIVE) post(r)
    }

    /** Restore: reminders already past (or turned off) come back greyed out; future ones are re-armed. */
    suspend fun replaceAll(list: List<Reminder>) {
        for (r in dao.getAll()) delete(r.id)
        val def = settings.current().reminderDefaultMinutes
        for (r in list) {
            val at = reminderTriggerAt(r, def)
            val done = r.state == DONE || at <= now()
            val id = dao.insert(r.copy(id = 0, triggerAt = at, state = if (done) DONE else SCHEDULED)).toInt()
            if (!done) schedule(id, at)
        }
    }
}
