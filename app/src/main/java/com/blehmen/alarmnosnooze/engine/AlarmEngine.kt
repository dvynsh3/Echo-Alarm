package com.blehmen.alarmnosnooze.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.blehmen.alarmnosnooze.MainActivity
import com.blehmen.alarmnosnooze.data.Alarm
import com.blehmen.alarmnosnooze.data.AlarmDao
import com.blehmen.alarmnosnooze.data.SettingsRepo
import com.blehmen.alarmnosnooze.service.AlarmService

/**
 * All alarm state transitions live here.
 *
 * Two kinds of pending intent per alarm:
 *  REGULAR - the scheduled time (once, or next matching weekday)
 *  AGAIN   - snooze or auto-repeat re-ring within a ringing session
 */
class AlarmEngine(
    private val ctx: Context,
    private val dao: AlarmDao,
    private val settings: SettingsRepo,
) {
    companion object {
        const val REGULAR = 0
        const val AGAIN = 1
        const val UPCOMING = 2
        const val UPCOMING_LEAD_MS = 15 * 60_000L
        const val ACTION_UPCOMING_DISMISS = "com.blehmen.alarmnosnooze.UPCOMING_DISMISS"
        const val ACTION_FIRE = "com.blehmen.alarmnosnooze.FIRE"
        const val EXTRA_ID = "alarm_id"
        const val EXTRA_KIND = "kind"
    }

    private val am = ctx.getSystemService(AlarmManager::class.java)
    private fun now() = System.currentTimeMillis()
    private fun name(a: Alarm) = if (a.label.isBlank()) "Alarm" else "\"${a.label}\""

    private fun pi(id: Int, kind: Int): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java)
            .setAction(ACTION_FIRE)
            .setData(Uri.parse("alarmnosnooze://alarm/$id/$kind"))
            .putExtra(EXTRA_ID, id)
            .putExtra(EXTRA_KIND, kind)
        return PendingIntent.getBroadcast(ctx, id * 3 + kind, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun showIntent() = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    // setAlarmClock is exempt from Doze and exact-alarm permission rules.
    private fun setClock(id: Int, kind: Int, at: Long) {
        am.setAlarmClock(AlarmManager.AlarmClockInfo(at, showIntent()), pi(id, kind))
    }

    private fun cancel(id: Int, kind: Int) = am.cancel(pi(id, kind))

    // ---------------------------------------------------------------- scheduling

    suspend fun scheduleRegular(a: Alarm): Alarm {
        if (!a.enabled) { cancel(a.id, REGULAR); cancelUpcoming(a.id); return a }
        val next = nextOccurrence(a.hour, a.minute, a.repeatDays).toInstant().toEpochMilli()
        setClock(a.id, REGULAR, next)
        val u = a.copy(nextTriggerAt = next)
        dao.update(u)
        scheduleUpcoming(u)
        return u
    }

    /** Create or update from the editor. Saving always (re)enables the alarm. */
    suspend fun save(alarm: Alarm): Int {
        if (alarm.id != 0) {
            cancel(alarm.id, AGAIN)
            cancelUpcoming(alarm.id)
            Notifier.cancelRepeating(ctx, alarm.id)
            AlarmService.send(ctx, AlarmService.ACTION_FORCE_STOP, alarm.id)
        }
        val clean = alarm.copy(enabled = true, sessionActive = false, repeatCount = 0, nextAgainAt = 0)
        val id = if (alarm.id == 0) dao.insert(clean.copy(sortOrder = dao.maxOrder() + 1)).toInt()
        else { dao.update(clean); alarm.id }
        dao.get(id)?.let { scheduleRegular(it) }
        return id
    }

    suspend fun setEnabled(id: Int, on: Boolean) {
        val a = dao.get(id) ?: return
        cancel(id, REGULAR); cancel(id, AGAIN); cancelUpcoming(id)
        Notifier.cancelRepeating(ctx, id)
        if (!on) AlarmService.send(ctx, AlarmService.ACTION_FORCE_STOP, id)
        val u = a.copy(enabled = on, sessionActive = false, repeatCount = 0, nextAgainAt = 0, nextTriggerAt = 0)
        dao.update(u)
        if (on) scheduleRegular(u)
    }

    suspend fun delete(id: Int) {
        cancel(id, REGULAR); cancel(id, AGAIN); cancelUpcoming(id)
        Notifier.cancelRepeating(ctx, id)
        AlarmService.send(ctx, AlarmService.ACTION_FORCE_STOP, id)
        dao.deleteById(id)
    }

    suspend fun saveOrder(ids: List<Int>) {
        val byId = dao.getAll().associateBy { it.id }
        dao.updateAll(ids.mapIndexedNotNull { i, id -> byId[id]?.copy(sortOrder = i) })
    }

    suspend fun setPinned(id: Int, pinned: Boolean) {
        val a = dao.get(id) ?: return
        dao.update(a.copy(pinned = pinned))
    }

    // ---------------------------------------------------------------- ringing lifecycle

    /** Called by the service when a pending intent fires. Returns the alarm to ring, or null if stale. */
    suspend fun onFire(id: Int, kind: Int): Alarm? {
        val a = dao.get(id) ?: return null
        if (!a.enabled) return null
        if (kind == REGULAR) {
            cancel(id, AGAIN)
            cancelUpcoming(id)
            var u = a.copy(sessionActive = true, sessionStart = now(), repeatCount = 0, nextAgainAt = 0, nextTriggerAt = 0)
            if (a.repeatDays != 0) {
                val next = nextOccurrence(a.hour, a.minute, a.repeatDays).toInstant().toEpochMilli()
                setClock(id, REGULAR, next)
                u = u.copy(nextTriggerAt = next)
            }
            dao.update(u)
            if (a.repeatDays != 0) scheduleUpcoming(u) // notice for the next occurrence
            return u
        }
        if (!a.sessionActive) return null
        val u = a.copy(repeatCount = a.repeatCount + 1, nextAgainAt = 0)
        dao.update(u)
        return u
    }

    /** Turn off: ends the session. One-time alarms switch themselves off. */
    suspend fun endSession(id: Int) {
        val a = dao.get(id) ?: return
        cancel(id, AGAIN)
        Notifier.cancelRepeating(ctx, id)
        dao.update(
            if (a.repeatDays == 0) a.copy(enabled = false, sessionActive = false, repeatCount = 0, nextAgainAt = 0, nextTriggerAt = 0)
            else a.copy(sessionActive = false, repeatCount = 0, nextAgainAt = 0)
        )
    }

    suspend fun snooze(id: Int) {
        val a = dao.get(id) ?: return
        val at = now() + a.snoozeMinutes * 60_000L
        setClock(id, AGAIN, at)
        dao.update(a.copy(nextAgainAt = at))
    }

    /** Auto-repeat "Silence": stop sound, ring again later, unless the cap or end time is reached. */
    suspend fun silenceAndRepeat(id: Int) {
        val a = dao.get(id) ?: return
        val at = now() + a.autoRepeatMinutes * 60_000L
        val endAt = endAtMillis(a)
        val max = settings.current().maxRepeats
        val reason = when {
            a.repeatCount >= max -> "reached the maximum of $max repeats"
            endAt != null && at > endAt -> "reached its end time"
            else -> null
        }
        if (reason != null) {
            endSession(id)
            Notifier.info(ctx, id, "Alarm stopped repeating", "${name(a)} $reason.")
            return
        }
        setClock(id, AGAIN, at)
        val u = a.copy(nextAgainAt = at)
        dao.update(u)
        Notifier.repeating(ctx, u, at)
    }

    /** Ring was ignored (timeout) or pre-empted by another alarm. */
    suspend fun handleMissed(id: Int) {
        val a = dao.get(id) ?: return
        when {
            a.autoRepeatEnabled -> silenceAndRepeat(id)
            a.snoozeEnabled -> snooze(id)
            else -> endSession(id)
        }
    }

    // ---------------------------------------------------------------- duplicate

    /** Copy with every setting kept. Only the name changes: "Gym" -> "Gym (2)" -> "Gym (3)". Sits right after the original. */
    suspend fun duplicate(id: Int) {
        val all = dao.getAll()
        val a = all.firstOrNull { it.id == id } ?: return
        val label = nextCopyLabel(a.label, all.map { it.label })
        val newId = dao.insert(
            a.copy(id = 0, label = label, sessionActive = false, repeatCount = 0, nextAgainAt = 0, nextTriggerAt = 0)
        ).toInt()
        val ordered = dao.getAll().sortedWith(compareByDescending<Alarm> { it.pinned }.thenBy { it.sortOrder }.thenBy { it.id })
        val ids = ordered.map { it.id }.toMutableList()
        ids.remove(newId)
        ids.add(ids.indexOf(id) + 1, newId)
        saveOrder(ids)
        dao.get(newId)?.let { if (it.enabled) scheduleRegular(it) }
    }

    // ---------------------------------------------------------------- "upcoming alarm" notice

    private fun armExact(id: Int, kind: Int, at: Long) {
        if (android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms())
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(id, kind))
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(id, kind))
    }

    fun cancelUpcoming(id: Int) {
        cancel(id, UPCOMING)
        Notifier.cancelUpcoming(ctx, id)
    }

    /** Stops the per-minute refresh after the user swipes the notice away. */
    fun dismissUpcoming(id: Int) = cancel(id, UPCOMING)

    /** Every enabled alarm gets a notice 15 minutes before it rings, whatever its snooze or repeat mode. */
    private suspend fun scheduleUpcoming(a: Alarm) {
        cancel(a.id, UPCOMING)
        val wanted = a.enabled && a.nextTriggerAt > now() && settings.current().upcomingNotice
        if (!wanted) { Notifier.cancelUpcoming(ctx, a.id); return }
        val leadAt = a.nextTriggerAt - UPCOMING_LEAD_MS
        if (leadAt > now()) {
            Notifier.cancelUpcoming(ctx, a.id)
            armExact(a.id, UPCOMING, leadAt)
        } else {
            postUpcoming(a) // already inside the 15 minute window
        }
    }

    private fun postUpcoming(a: Alarm) {
        val remaining = a.nextTriggerAt - now()
        if (remaining <= 0) { Notifier.cancelUpcoming(ctx, a.id); return }
        Notifier.upcoming(ctx, a, a.nextTriggerAt, now())
        // Refresh the "in N mins" wording each minute. The countdown clock in the notification is live regardless.
        val nextTick = now() + 60_000 - (now() % 60_000)
        if (nextTick < a.nextTriggerAt) armExact(a.id, UPCOMING, nextTick)
    }

    suspend fun onUpcoming(id: Int) {
        val a = dao.get(id) ?: return
        if (!a.enabled || !settings.current().upcomingNotice) { Notifier.cancelUpcoming(ctx, id); return }
        postUpcoming(a)
    }

    // ---------------------------------------------------------------- system events

    /** Time / timezone changed or app updated: re-register everything, keep sessions. */
    suspend fun rescheduleAll() {
        val t = now()
        for (a in dao.getAll()) {
            if (!a.enabled) continue
            if (a.sessionActive && a.nextAgainAt > t) setClock(a.id, AGAIN, a.nextAgainAt)
            if (!(a.repeatDays == 0 && a.sessionActive)) scheduleRegular(a)
        }
    }

    suspend fun onBoot() {
        val resume = settings.current().resumeAfterReboot
        val t = now()
        for (a in dao.getAll()) {
            if (!a.enabled) continue
            if (a.sessionActive) {
                if (resume && (a.autoRepeatEnabled || a.snoozeEnabled)) {
                    val at = maxOf(a.nextAgainAt, t + 15_000)
                    val cur = a.copy(nextAgainAt = at)
                    dao.update(cur)
                    setClock(a.id, AGAIN, at)
                    if (a.autoRepeatEnabled) Notifier.repeating(ctx, cur, at)
                    if (a.repeatDays != 0) scheduleRegular(cur)
                } else {
                    endSession(a.id)
                    Notifier.info(
                        ctx, a.id, "Alarm turned off",
                        "${name(a)} was ringing or repeating when the phone restarted, so it was turned off."
                    )
                    if (a.repeatDays != 0) dao.get(a.id)?.let { scheduleRegular(it) }
                }
            } else if (a.repeatDays == 0 && a.nextTriggerAt in 1 until t) {
                dao.update(a.copy(enabled = false, nextTriggerAt = 0))
                Notifier.info(ctx, a.id, "Missed alarm", "${name(a)} was due while the phone was off.")
            } else {
                scheduleRegular(a)
            }
        }
    }

    /** Restore: wipe everything, insert the given alarms fresh (runtime state cleared), re-arm enabled ones. */
    suspend fun replaceAll(list: List<Alarm>) {
        for (a in dao.getAll()) delete(a.id)
        for (a in list) {
            val id = dao.insert(
                a.copy(id = 0, sessionActive = false, repeatCount = 0, nextAgainAt = 0, nextTriggerAt = 0)
            ).toInt()
            dao.get(id)?.let { if (it.enabled) scheduleRegular(it) }
        }
    }
}

private val COPY_SUFFIX = Regex("^(.*) \\((\\d{1,3})\\)$")

/** "Gym" -> "Gym (2)"; with "Gym (2)" already present -> "Gym (3)". A blank name copies as "Alarm (2)". */
fun nextCopyLabel(original: String, existing: List<String>): String {
    val base = COPY_SUFFIX.matchEntire(original)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
        ?: original.ifBlank { "Alarm" }
    var highest = 1
    for (l in existing) {
        val m = COPY_SUFFIX.matchEntire(l) ?: continue
        if (m.groupValues[1] == base) highest = maxOf(highest, m.groupValues[2].toInt())
    }
    val suffix = " (${highest + 1})"
    return base.take(40 - suffix.length) + suffix
}
