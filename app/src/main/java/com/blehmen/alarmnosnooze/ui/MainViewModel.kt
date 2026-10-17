package com.blehmen.alarmnosnooze.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blehmen.alarmnosnooze.AlarmApp
import com.blehmen.alarmnosnooze.data.Alarm
import com.blehmen.alarmnosnooze.data.AppSettings
import com.blehmen.alarmnosnooze.data.Backup
import com.blehmen.alarmnosnooze.data.Phase
import com.blehmen.alarmnosnooze.data.StopwatchState
import com.blehmen.alarmnosnooze.data.TimerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AlarmApp

    val alarms: StateFlow<List<Alarm>> =
        app.db.alarmDao().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val timer: StateFlow<TimerState> =
        app.timerRepo.timer.stateIn(viewModelScope, SharingStarted.Eagerly, TimerState())
    val stopwatch: StateFlow<StopwatchState> =
        app.timerRepo.stopwatch.stateIn(viewModelScope, SharingStarted.Eagerly, StopwatchState())
    val settings: StateFlow<AppSettings> =
        app.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    /** Selected bottom tab: 0 alarms, 1 stopwatch, 2 timer. Lives here so MainActivity can read it for PiP. */
    var tab by mutableIntStateOf(0)

    /** Set by the alarm widget's + button; the app opens the new-alarm editor and clears it. */
    var newAlarmRequest by mutableStateOf(false)

    // ---- alarms
    fun toggle(a: Alarm, on: Boolean) = viewModelScope.launch { app.engine.setEnabled(a.id, on) }
    fun delete(a: Alarm) = viewModelScope.launch { app.engine.delete(a.id) }
    fun togglePin(a: Alarm) = viewModelScope.launch { app.engine.setPinned(a.id, !a.pinned) }
    fun saveOrder(list: List<Alarm>) = viewModelScope.launch { app.engine.saveOrder(list.map { it.id }) }
    fun save(a: Alarm) = viewModelScope.launch { app.engine.save(a) }
    fun duplicate(a: Alarm) = viewModelScope.launch { app.engine.duplicate(a.id) }

    /** New alarm with the user's default sound, volume and ramp from Settings. */
    private fun newAlarm(h: Int, m: Int): Alarm {
        val d = settings.value
        return Alarm(
            hour = h, minute = m, soundUri = d.defaultSoundUri, volume = d.defaultVolume,
            rampEnabled = d.defaultRampEnabled, rampSeconds = d.defaultRampSeconds,
        )
    }

    fun blank(): Alarm { val t = LocalTime.now(); return newAlarm(t.hour, t.minute) }

    // ---- settings
    fun setTheme(v: Int) = viewModelScope.launch { app.settings.setTheme(v) }
    fun setAccent(v: Int) = viewModelScope.launch { app.settings.setAccent(v) }
    fun setMaxRepeats(v: Int) = viewModelScope.launch { app.settings.setMaxRepeats(v) }
    fun setResume(v: Boolean) = viewModelScope.launch { app.settings.setResume(v) }
    fun setConfirmDelete(v: Boolean) = viewModelScope.launch { app.settings.setConfirmDelete(v) }
    fun setBedtimeReminder(v: Boolean) = viewModelScope.launch { app.settings.setBedtimeReminder(v) }
    fun setUpcomingNotice(v: Boolean) = viewModelScope.launch {
        app.settings.setUpcomingNotice(v)
        app.engine.rescheduleAll() // arms or clears the 15-minute notices
    }
    fun setDefaultSound(v: String?) = viewModelScope.launch { app.settings.setDefaultSound(v) }
    fun setDefaultVolume(v: Int) = viewModelScope.launch { app.settings.setDefaultVolume(v) }
    fun setDefaultRamp(v: Boolean) = viewModelScope.launch { app.settings.setDefaultRamp(v) }
    fun setDefaultRampSeconds(v: Int) = viewModelScope.launch { app.settings.setDefaultRampSeconds(v) }

    // ---- timer
    fun timerStart(ms: Long, seconds: Int) = viewModelScope.launch {
        app.timerRepo.setLast(seconds)
        app.timerEngine.start(ms)
    }
    fun timerPause() = viewModelScope.launch { app.timerEngine.pause() }
    fun timerResume() = viewModelScope.launch { app.timerEngine.resume() }
    /** Cancel while running or paused; Stop while ringing. */
    fun timerCancel() = viewModelScope.launch {
        if (timer.value.phase == Phase.RINGING) app.timerEngine.stopRinging() else app.timerEngine.cancel()
    }
    fun timerAddMinute() = viewModelScope.launch { app.timerEngine.addMinute() }
    fun savePresets(list: List<Int>) = viewModelScope.launch { app.timerRepo.setPresets(list) }

    // ---- stopwatch
    fun swToggle() = viewModelScope.launch { app.stopwatchEngine.toggle() }
    fun swLap() = viewModelScope.launch { app.stopwatchEngine.lap() }
    fun swReset() = viewModelScope.launch { app.stopwatchEngine.reset() }

    // ---- backup / restore
    fun exportBackup(uri: Uri, onDone: (String) -> Unit) = viewModelScope.launch {
        val msg = try {
            val json = Backup.export(app)
            withContext(Dispatchers.IO) {
                app.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            }
            "Backup saved"
        } catch (e: Exception) { "Backup failed: ${e.message}" }
        onDone(msg)
    }

    fun importBackup(uri: Uri, onDone: (String) -> Unit) = viewModelScope.launch {
        val msg = try {
            val text = withContext(Dispatchers.IO) {
                app.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
            }
            val r = Backup.restore(app, text)
            "Restored ${r.alarms} alarms"
        } catch (e: Exception) { "Restore failed: ${e.message}" }
        onDone(msg)
    }
}
