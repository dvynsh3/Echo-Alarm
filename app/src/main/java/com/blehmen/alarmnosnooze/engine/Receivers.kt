package com.blehmen.alarmnosnooze.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.blehmen.alarmnosnooze.app
import com.blehmen.alarmnosnooze.service.AlarmService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmEngine.ACTION_FIRE) return
        val start = Intent(context, AlarmService::class.java)
            .setAction(AlarmService.ACTION_RING)
            .putExtra(AlarmService.EXTRA_ID, intent.getIntExtra(AlarmEngine.EXTRA_ID, -1))
            .putExtra(AlarmService.EXTRA_KIND, intent.getIntExtra(AlarmEngine.EXTRA_KIND, AlarmEngine.REGULAR))
        ContextCompat.startForegroundService(context, start)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(ReminderEngine.EXTRA_ID, -1)
        val engine = context.app.reminders
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ReminderEngine.ACTION_FIRE -> engine.fire(id)
                    ReminderEngine.ACTION_DONE -> engine.done(id)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.app
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    TimerActions.FIRE -> app.timerEngine.onFire()
                    TimerActions.PAUSE -> app.timerEngine.pause()
                    TimerActions.RESUME -> app.timerEngine.resume()
                    TimerActions.CANCEL -> app.timerEngine.cancel()
                    TimerActions.ADD_MINUTE -> app.timerEngine.addMinute()
                    TimerActions.STOP -> app.timerEngine.stopRinging()
                    TimerActions.SW_TOGGLE -> app.stopwatchEngine.toggle()
                    TimerActions.SW_LAP -> app.stopwatchEngine.lap()
                    TimerActions.SW_RESET -> app.stopwatchEngine.reset()
                }
            } finally {
                pending.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarms = context.app.engine
        val reminders = context.app.reminders
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    Intent.ACTION_BOOT_COMPLETED -> {
                        alarms.onBoot(); reminders.onBoot()
                        context.app.timerEngine.onBoot(); context.app.stopwatchEngine.onBoot()
                    }
                    else -> { alarms.rescheduleAll(); reminders.rescheduleAll() }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
