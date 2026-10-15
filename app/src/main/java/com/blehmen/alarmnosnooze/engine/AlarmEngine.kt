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
        return PendingIntent.getBroadcast(ctx, id * 2 + kind, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
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
        if (!a.enabled) { cancel(a.id, REGULAR); return a }
        val next = nextOccurrence(a.hour, a.minute, a.repeatDays).toInstant().toEpochMilli()
        setClock(a.id, REGULAR, next)
        val u = a.copy(nextTriggerAt = next)
        dao.update(u)
        return u
    }

    /** Create or update from the editor. Saving always (re)enables the alarm. */
    suspend fun save(alarm: Alarm): Int {
        if (alarm.id != 0) {
            cancel(alarm.id, AGAIN)
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
        cancel(id, REGULAR); cancel(id, AGAIN)
        Notifier.cancelRepeating(ctx, id)
        if (!on) AlarmService.send(ctx, AlarmService.ACTION_FORCE_STOP, id)
        val u = a.copy(enabled = on, sessionActive = false, repeatCount = 0, nextAgainAt = 0, nextTriggerAt = 0)
        dao.update(u)
        if (on) scheduleRegular(u)
    }

    suspend fun delete(id: Int) {
        cancel(id, REGULAR); cancel(id, AGAIN)
        Notifier.cancelRepeating(ctx, id)
        AlarmService.send(ctx, AlarmService.ACTION_FORCE_STOP, id)
        dao.deleteById(id)
    }

    suspend fun saveOrder(ids: List<Int>) { ids.forEachIndexed { i, id -> dao.setOrder(id, i) } }

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
            var u = a.copy(sessionActive = true, sessionStart = now(), repeatCount = 0, nextAgainAt = 0, nextTriggerAt = 0)
            if (a.repeatDays != 0) {
                val next = nextOccurrence(a.hour, a.minute, a.repeatDays).toInstant().toEpochMilli()
                setClock(id, REGULAR, next)
                u = u.copy(nextTriggerAt = next)
            }
            dao.update(u)
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
