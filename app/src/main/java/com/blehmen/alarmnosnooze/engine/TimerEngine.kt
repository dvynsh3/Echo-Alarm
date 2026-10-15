package com.blehmen.alarmnosnooze.engine

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.blehmen.alarmnosnooze.MainActivity
import com.blehmen.alarmnosnooze.data.Phase
import com.blehmen.alarmnosnooze.data.StopwatchState
import com.blehmen.alarmnosnooze.data.TimerRepo
import com.blehmen.alarmnosnooze.service.TimerService

class TimerEngine(private val ctx: Context, private val repo: TimerRepo) {
    private val am = ctx.getSystemService(AlarmManager::class.java)
    private val nm = ctx.getSystemService(NotificationManager::class.java)
    private fun now() = System.currentTimeMillis()

    private fun firePi() = PendingIntent.getBroadcast(
        ctx, 7001,
        Intent(ctx, TimerReceiver::class.java).setAction(TimerActions.FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // setAlarmClock: exact and exempt from Doze throttling. Side effect: Android may list a running
    // timer as the "next alarm" on the lock screen.
    private fun arm(endAt: Long) {
        am.setAlarmClock(AlarmManager.AlarmClockInfo(endAt, TimerNotifier.openTimer(ctx)), firePi())
    }

    private fun disarm() = am.cancel(firePi())

    private suspend fun post() {
        val s = repo.timerNow()
        nm.notify(TimerNotifier.ID_TIMER, TimerNotifier.timer(ctx, s, now()))
    }

    suspend fun start(totalMs: Long) {
        if (totalMs <= 0) return
        val end = now() + totalMs
        repo.setTimer(Phase.RUNNING, totalMs, end, 0)
        arm(end)
        post()
    }

    suspend fun pause() {
        val s = repo.timerNow()
        if (s.phase != Phase.RUNNING) return
        disarm()
        repo.setTimer(Phase.PAUSED, s.totalMs, 0, (s.endAt - now()).coerceAtLeast(0))
        post()
    }

    suspend fun resume() {
        val s = repo.timerNow()
        if (s.phase != Phase.PAUSED) return
        val end = now() + s.remainingMs
        repo.setTimer(Phase.RUNNING, s.totalMs, end, 0)
        arm(end)
        post()
    }

    suspend fun cancel() {
        disarm()
        nm.cancel(TimerNotifier.ID_TIMER)
        ctx.stopService(Intent(ctx, TimerService::class.java))
        nm.cancel(TimerNotifier.ID_RING)
        repo.clearTimer()
    }

    suspend fun addMinute() {
        val s = repo.timerNow()
        when (s.phase) {
            Phase.RUNNING -> {
                val end = s.endAt + 60_000
                repo.setTimer(Phase.RUNNING, s.totalMs + 60_000, end, 0)
                arm(end); post()
            }
            Phase.PAUSED -> {
                repo.setTimer(Phase.PAUSED, s.totalMs + 60_000, 0, s.remainingMs + 60_000)
                post()
            }
            Phase.RINGING -> { stopRinging(); start(60_000) }
        }
    }

    suspend fun onFire() {
        val s = repo.timerNow()
        if (s.phase != Phase.RUNNING || s.endAt - now() > 5_000) return
        nm.cancel(TimerNotifier.ID_TIMER)
        repo.setTimer(Phase.RINGING, s.totalMs, 0, 0)
        ContextCompat.startForegroundService(ctx, Intent(ctx, TimerService::class.java))
    }

    suspend fun stopRinging() {
        ctx.stopService(Intent(ctx, TimerService::class.java))
        nm.cancel(TimerNotifier.ID_RING)
        repo.clearTimer()
    }

    suspend fun onBoot() {
        val s = repo.timerNow()
        when (s.phase) {
            Phase.RUNNING -> if (s.endAt <= now()) onFire() else { arm(s.endAt); post() }
            Phase.PAUSED -> post()
            Phase.RINGING -> repo.clearTimer()
        }
    }
}

class StopwatchEngine(private val ctx: Context, private val repo: TimerRepo) {
    private val nm = ctx.getSystemService(NotificationManager::class.java)
    private fun now() = System.currentTimeMillis()

    private suspend fun post(s: StopwatchState) {
        if (!s.running && s.accumMs == 0L) nm.cancel(TimerNotifier.ID_SW)
        else nm.notify(TimerNotifier.ID_SW, TimerNotifier.stopwatch(ctx, s, now()))
    }

    suspend fun toggle() {
        val s = repo.stopwatchNow()
        val u = if (s.running) s.copy(running = false, accumMs = s.elapsed(now()))
        else s.copy(running = true, startAt = now())
        repo.setStopwatch(u)
        post(u)
    }

    suspend fun lap() {
        val s = repo.stopwatchNow()
        if (!s.running) return
        repo.setStopwatch(s.copy(laps = s.laps + s.elapsed(now())))
    }

    /** Only while paused. */
    suspend fun reset() {
        val s = repo.stopwatchNow()
        if (s.running) return
        val u = StopwatchState()
        repo.setStopwatch(u)
        post(u)
    }

    suspend fun onBoot() {
        val s = repo.stopwatchNow()
        if (s.running || s.accumMs > 0) post(s)
    }
}
