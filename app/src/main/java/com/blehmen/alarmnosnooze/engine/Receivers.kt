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
        val id = intent.getIntExtra(AlarmEngine.EXTRA_ID, -1)
        when (intent.action) {
            AlarmEngine.ACTION_UPCOMING_DISMISS -> context.app.engine.dismissUpcoming(id)
            AlarmEngine.ACTION_UPCOMING_OFF, AlarmEngine.ACTION_UPCOMING_SNOOZE -> {
                val action = intent.action
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        if (action == AlarmEngine.ACTION_UPCOMING_OFF) {
                            context.app.engine.skipUpcoming(id)?.let { toastText(context, it) }
                        } else context.app.engine.delayInstance(id, AlarmEngine.UPCOMING_SNOOZE_MINUTES)
                    } finally { pending.finish() }
                }
            }
            AlarmEngine.ACTION_FIRE -> {
                val kind = intent.getIntExtra(AlarmEngine.EXTRA_KIND, AlarmEngine.REGULAR)
                if (kind == AlarmEngine.UPCOMING) {
                    val pending = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try { context.app.engine.onUpcoming(id) } finally { pending.finish() }
                    }
                    return
                }
                val start = Intent(context, AlarmService::class.java)
                    .setAction(AlarmService.ACTION_RING)
                    .putExtra(AlarmService.EXTRA_ID, id)
                    .putExtra(AlarmService.EXTRA_KIND, kind)
                ContextCompat.startForegroundService(context, start)
            }
        }
    }
}

class BedtimeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { BedtimeReminder.fire(context) } finally { pending.finish() }
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
        val app = context.app
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                BedtimeReminder.schedule(context)
                when (intent.action) {
                    Intent.ACTION_BOOT_COMPLETED -> {
                        app.engine.onBoot()
                        app.timerEngine.onBoot()
                        app.stopwatchEngine.onBoot()
                    }
                    else -> app.engine.rescheduleAll()
                }
            } finally {
                pending.finish()
            }
        }
    }
}
