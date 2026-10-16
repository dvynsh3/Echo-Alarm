package com.blehmen.alarmnosnooze.engine

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.blehmen.alarmnosnooze.MainActivity
import com.blehmen.alarmnosnooze.R
import com.blehmen.alarmnosnooze.data.Phase
import com.blehmen.alarmnosnooze.data.StopwatchState
import com.blehmen.alarmnosnooze.data.TimerState

object TimerActions {
    const val FIRE = "com.blehmen.alarmnosnooze.T_FIRE"
    const val PAUSE = "com.blehmen.alarmnosnooze.T_PAUSE"
    const val RESUME = "com.blehmen.alarmnosnooze.T_RESUME"
    const val CANCEL = "com.blehmen.alarmnosnooze.T_CANCEL"
    const val ADD_MINUTE = "com.blehmen.alarmnosnooze.T_ADD"
    const val STOP = "com.blehmen.alarmnosnooze.T_STOP"
    const val SW_TOGGLE = "com.blehmen.alarmnosnooze.S_TOGGLE"
    const val SW_LAP = "com.blehmen.alarmnosnooze.S_LAP"
    const val SW_RESET = "com.blehmen.alarmnosnooze.S_RESET"
}

/**
 * Running timer and stopwatch are ongoing notifications with a native chronometer, so they tick
 * without being re-posted. On Android 16+ they ask to be promoted to the status bar pill via the
 * documented extras; older versions just show a normal ongoing notification.
 */
object TimerNotifier {
    const val CH_TIMER = "timer"
    const val CH_TIMER_RING = "timer_ring"
    const val ID_TIMER = 40001
    const val ID_SW = 40002
    const val ID_RING = 40003

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_TIMER, "Timer and stopwatch", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(
            NotificationChannel(CH_TIMER_RING, "Timer finished", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    private fun flags() = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    private fun open(ctx: Context, tab: Int) = PendingIntent.getActivity(
        ctx, 10 + tab,
        Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, tab)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        flags()
    )

    fun openTimer(ctx: Context) = open(ctx, 2)

    private fun act(ctx: Context, action: String) = PendingIntent.getBroadcast(
        ctx, action.hashCode(), Intent(ctx, TimerReceiver::class.java).setAction(action), flags()
    )

    private fun Notification.Builder.action(ctx: Context, label: String, action: String) =
        addAction(Notification.Action.Builder(null, label, act(ctx, action)).build())

    private fun Notification.Builder.promote(critical: String?) = apply {
        addExtras(Bundle().apply {
            putBoolean("android.requestPromotedOngoing", true)
            if (critical != null) putString("android.shortCriticalText", critical)
        })
    }

    fun timer(ctx: Context, s: TimerState, now: Long): Notification {
        val b = Notification.Builder(ctx, CH_TIMER)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Timer")
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open(ctx, 2))
        if (s.phase == Phase.RUNNING) {
            b.setContentText("Counting down")
                .setWhen(s.endAt).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
                .action(ctx, "Pause", TimerActions.PAUSE)
                .action(ctx, "+1:00", TimerActions.ADD_MINUTE)
                .action(ctx, "Cancel", TimerActions.CANCEL)
                .promote(null)
        } else {
            val left = fmtTimer(s.remainingMs)
            b.setContentText("Paused · $left left").setShowWhen(false)
                .action(ctx, "Resume", TimerActions.RESUME)
                .action(ctx, "Cancel", TimerActions.CANCEL)
                .promote(left)
        }
        return b.build()
    }

    fun stopwatch(ctx: Context, s: StopwatchState, now: Long): Notification {
        val elapsed = s.elapsed(now)
        val b = Notification.Builder(ctx, CH_TIMER)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Stopwatch")
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open(ctx, 1))
        if (s.running) {
            b.setContentText("Running")
                .setWhen(now - elapsed).setShowWhen(true).setUsesChronometer(true)
                .action(ctx, "Pause", TimerActions.SW_TOGGLE)
                .action(ctx, "Lap", TimerActions.SW_LAP)
                .promote(null)
        } else {
            val t = fmtStopwatch(elapsed)
            b.setContentText("Paused · $t").setShowWhen(false)
                .action(ctx, "Resume", TimerActions.SW_TOGGLE)
                .action(ctx, "Reset", TimerActions.SW_RESET)
                .promote(fmtTimer(elapsed))
        }
        return b.build()
    }

    fun ringing(ctx: Context): Notification =
        Notification.Builder(ctx, CH_TIMER_RING)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Timer finished")
            .setContentText("Time's up")
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(open(ctx, 2))
            .action(ctx, "Stop", TimerActions.STOP)
            .action(ctx, "+1:00", TimerActions.ADD_MINUTE)
            .build()
}
