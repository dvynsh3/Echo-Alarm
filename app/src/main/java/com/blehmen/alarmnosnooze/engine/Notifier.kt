package com.blehmen.alarmnosnooze.engine

import android.app.*
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import com.blehmen.alarmnosnooze.MainActivity
import com.blehmen.alarmnosnooze.R
import com.blehmen.alarmnosnooze.RingingActivity
import com.blehmen.alarmnosnooze.data.Alarm
import com.blehmen.alarmnosnooze.service.AlarmService
import java.util.Date

object Notifier {
    const val CH_RING = "ring"
    const val CH_INFO = "info"
    const val CH_REPEAT = "repeat"
    const val RING_ID = 1

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_RING, "Ringing alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        nm.createNotificationChannel(NotificationChannel(CH_INFO, "Alarm notices", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_REPEAT, "Repeating alarms", NotificationManager.IMPORTANCE_LOW))
        nm.deleteNotificationChannel("reminder") // channel from a removed feature
    }

    private fun immutable() = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    private fun openApp(ctx: Context) = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), immutable()
    )

    private fun svc(ctx: Context, action: String, id: Int) = PendingIntent.getService(
        ctx, id,
        Intent(ctx, AlarmService::class.java).setAction(action).putExtra(AlarmService.EXTRA_ID, id),
        immutable()
    )

    private fun timeText(ctx: Context, millis: Long) = DateFormat.getTimeFormat(ctx).format(Date(millis))
    private fun title(a: Alarm) = a.label.ifBlank { "Alarm" }

    private fun screenIntent(ctx: Context) = PendingIntent.getActivity(
        ctx, 1,
        Intent(ctx, RingingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        immutable()
    )

    /**
     * Shown the instant the service starts. It already carries the full-screen intent, otherwise the later
     * update would only refresh a notification that never asked to take over the lock screen.
     */
    fun placeholder(ctx: Context): Notification {
        val screen = screenIntent(ctx)
        return Notification.Builder(ctx, CH_RING)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Alarm")
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(screen)
            .setFullScreenIntent(screen, true)
            .build()
    }

    fun ringing(ctx: Context, a: Alarm): Notification {
        val screen = screenIntent(ctx)
        val b = Notification.Builder(ctx, CH_RING)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title(a))
            .setContentText(timeText(ctx, System.currentTimeMillis()))
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(screen)
            .setFullScreenIntent(screen, true)
        when {
            a.autoRepeatEnabled -> b.addAction(
                Notification.Action.Builder(null, "Silence", svc(ctx, AlarmService.ACTION_SILENCE, a.id)).build()
            )
            else -> {
                b.addAction(Notification.Action.Builder(null, "Turn off", svc(ctx, AlarmService.ACTION_TURN_OFF, a.id)).build())
                if (a.snoozeEnabled) b.addAction(
                    Notification.Action.Builder(null, "Snooze", svc(ctx, AlarmService.ACTION_SNOOZE, a.id)).build()
                )
            }
        }
        return b.build()
    }

    fun repeating(ctx: Context, a: Alarm, at: Long) {
        val n = Notification.Builder(ctx, CH_REPEAT)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("${title(a)} is repeating")
            .setContentText("Rings again at ${timeText(ctx, at)}. Turn it off in the app to stop.")
            .setOngoing(true)
            .setContentIntent(openApp(ctx))
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(10000 + a.id, n)
    }

    fun cancelRepeating(ctx: Context, id: Int) {
        ctx.getSystemService(NotificationManager::class.java).cancel(10000 + id)
    }

    fun info(ctx: Context, id: Int, title: String, text: String) {
        val n = Notification.Builder(ctx, CH_INFO)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(20000 + id, n)
    }
}
