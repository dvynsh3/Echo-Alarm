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
    const val CH_RING_QUIET = "ring_quiet"
    const val CH_INFO = "info"
    const val CH_REPEAT = "repeat"
    const val CH_UPCOMING = "upcoming"
    const val CH_BEDTIME = "bedtime"
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
        // Used while the full-screen pop-up is showing, so no second heads-up pill appears on top of it.
        nm.createNotificationChannel(
            NotificationChannel(CH_RING_QUIET, "Ringing alarm (pop-up)", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        nm.createNotificationChannel(NotificationChannel(CH_INFO, "Alarm notices", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_REPEAT, "Repeating alarms", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_UPCOMING, "Upcoming alarms", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_BEDTIME, "Bedtime reminder", NotificationManager.IMPORTANCE_DEFAULT))
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

    /** Only for a stale ring that is about to be dismissed; the service must still show something. */
    fun placeholder(ctx: Context): Notification =
        Notification.Builder(ctx, CH_RING_QUIET)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Alarm")
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .build()

    /**
     * The ringing notification (the "pill" when it is not a pop-up). [loud] = heads-up and lock-screen takeover;
     * quiet is used while the full-screen pop-up is showing.
     */
    fun ringing(ctx: Context, a: Alarm, loud: Boolean = true): Notification {
        val screen = screenIntent(ctx)
        val (t, ap) = timeParts(a.hour, a.minute, DateFormat.is24HourFormat(ctx))
        val time = if (ap.isEmpty()) t else "$t $ap"
        val detail = when {
            a.autoRepeatEnabled -> "Rings again in ${a.autoRepeatMinutes} min after Silence."
            a.snoozeEnabled -> "Snooze rings again in ${a.snoozeMinutes} min."
            else -> "Delay rings again in 5 min."
        }
        val b = Notification.Builder(ctx, if (loud) CH_RING else CH_RING_QUIET)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("${title(a)} · $time")
            .setContentText(detail)
            .setStyle(Notification.BigTextStyle().bigText(detail))
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(screen)
        if (loud) b.setFullScreenIntent(screen, true)
        val first = if (a.autoRepeatEnabled) "Silence" to AlarmService.ACTION_SILENCE else "Turn off" to AlarmService.ACTION_TURN_OFF
        val delay = if (a.snoozeEnabled) "Snooze ${a.snoozeMinutes} min" else "Delay 5 min"
        b.addAction(Notification.Action.Builder(null, first.first, svc(ctx, first.second, a.id)).build())
        b.addAction(Notification.Action.Builder(null, delay, svc(ctx, AlarmService.ACTION_SNOOZE, a.id)).build())
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

    /** Heading shows name and time; the sentence underneath only appears when the notification is expanded. */
    fun upcoming(ctx: Context, a: Alarm, triggerAt: Long, now: Long) {
        val mins = ((triggerAt - now + 59_999) / 60_000).toInt().coerceAtLeast(1)
        val name = title(a)
        val dismiss = PendingIntent.getBroadcast(
            ctx, 400000 + a.id,
            Intent(ctx, AlarmReceiver::class.java).setAction(AlarmEngine.ACTION_UPCOMING_DISMISS)
                .putExtra(AlarmEngine.EXTRA_ID, a.id),
            immutable()
        )
        val n = Notification.Builder(ctx, CH_UPCOMING)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("$name · ${timeText(ctx, triggerAt)}")
            .setSubText("Upcoming alarm")
            .setContentText("")
            .setStyle(Notification.BigTextStyle().bigText("$name rings in $mins ${if (mins == 1) "min" else "mins"}"))
            .setCategory(Notification.CATEGORY_STATUS)
            .setWhen(triggerAt).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(ctx))
            .setDeleteIntent(dismiss)
            .addAction(Notification.Action.Builder(null, "Turn off", noticeAction(ctx, AlarmEngine.ACTION_UPCOMING_OFF, a.id, 410000)).build())
            .addAction(Notification.Action.Builder(null, "Snooze 5 mins", noticeAction(ctx, AlarmEngine.ACTION_UPCOMING_SNOOZE, a.id, 420000)).build())
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(50000 + a.id, n)
    }

    private fun noticeAction(ctx: Context, action: String, id: Int, base: Int) = PendingIntent.getBroadcast(
        ctx, base + id,
        Intent(ctx, AlarmReceiver::class.java).setAction(action).putExtra(AlarmEngine.EXTRA_ID, id),
        immutable()
    )

    fun cancelUpcoming(ctx: Context, id: Int) {
        ctx.getSystemService(NotificationManager::class.java).cancel(50000 + id)
    }

    fun bedtime(ctx: Context, heading: String, expanded: String?) {
        val b = Notification.Builder(ctx, CH_BEDTIME)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(heading)
            .setSubText("Tomorrow morning")
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
        if (expanded != null) b.setContentText("").setStyle(Notification.BigTextStyle().bigText(expanded))
        ctx.getSystemService(NotificationManager::class.java).notify(60001, b.build())
    }
}
