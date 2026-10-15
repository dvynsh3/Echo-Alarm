package com.blehmen.alarmnosnooze

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.blehmen.alarmnosnooze.data.Alarm
import com.blehmen.alarmnosnooze.data.Phase
import com.blehmen.alarmnosnooze.data.Reminder
import com.blehmen.alarmnosnooze.ui.*

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_TAB = "tab"
        const val EXTRA_QUICK = "quick"
        const val TAB_STOPWATCH = 2
        const val TAB_TIMER = 3
    }

    private val vm: MainViewModel by viewModels()
    private var inPip by mutableStateOf(false)

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)

        handleTabIntent(intent)

        setContent {
            Box(Modifier.fillMaxSize()) {
                // Keep AppRoot composed in PiP so navigation state survives; the mini countdown covers it.
                AppRoot(vm, onEnterPip = ::enterPip)
                if (inPip) {
                    val settings by vm.settings.collectAsState()
                    val timer by vm.timer.collectAsState()
                    AppTheme(settings.themeMode, settings.accent) { MiniTimer(timer) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleTabIntent(intent)
    }

    private fun handleTabIntent(i: Intent?) {
        val t = i?.getIntExtra(EXTRA_TAB, -1) ?: -1
        if (t >= 0) vm.tab = t
        if (i?.getBooleanExtra(EXTRA_QUICK, false) == true) { vm.tab = 0; vm.quickRequest = true }
    }

    private fun enterPip() {
        try {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
        } catch (_: Exception) { /* PiP unavailable on this device */ }
    }

    /** Leaving the app from the Timer tab while it is counting down shrinks it into a mini countdown. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (vm.tab == TAB_TIMER && vm.timer.value.phase == Phase.RUNNING) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
    }
}

private const val SCREEN_MAIN = 0
private const val SCREEN_EDITOR = 1
private const val SCREEN_SETTINGS = 2
private const val SCREEN_REMINDER_EDITOR = 3

@Composable
fun AppRoot(vm: MainViewModel, onEnterPip: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val alarms by vm.alarms.collectAsState()
    val reminders by vm.reminders.collectAsState()
    val timer by vm.timer.collectAsState()
    val stopwatch by vm.stopwatch.collectAsState()

    AppTheme(settings.themeMode, settings.accent) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            var screen by rememberSaveable { mutableStateOf(SCREEN_MAIN) }
            var editId by rememberSaveable { mutableStateOf(-1) }
            var reminderId by rememberSaveable { mutableStateOf(-1) }
            BackHandler(enabled = screen != SCREEN_MAIN) { screen = SCREEN_MAIN }

            when (screen) {
                SCREEN_EDITOR -> {
                    val alarm by produceState<Alarm?>(null, editId) {
                        value = if (editId == -1) vm.blank() else vm.load(editId)
                    }
                    alarm?.let { a ->
                        key(a.id) {
                            AlarmEditorScreen(
                                initial = a,
                                isNew = editId == -1,
                                onSave = { vm.save(it); screen = SCREEN_MAIN },
                                onCancel = { screen = SCREEN_MAIN },
                                onDelete = { vm.delete(a); screen = SCREEN_MAIN },
                            )
                        }
                    }
                }
                SCREEN_REMINDER_EDITOR -> {
                    val reminder by produceState<Reminder?>(null, reminderId) {
                        value = if (reminderId == -1) vm.blankReminder() else vm.loadReminder(reminderId)
                    }
                    reminder?.let { r ->
                        key(r.id) {
                            ReminderEditorScreen(
                                initial = r,
                                isNew = reminderId == -1,
                                defaultMinutes = settings.reminderDefaultMinutes,
                                onSave = { vm.saveReminder(it); screen = SCREEN_MAIN },
                                onCancel = { screen = SCREEN_MAIN },
                                onDelete = { vm.deleteReminder(r); screen = SCREEN_MAIN },
                            )
                        }
                    }
                }
                SCREEN_SETTINGS -> SettingsScreen(vm) { screen = SCREEN_MAIN }
                else -> Box(Modifier.fillMaxSize()) {
                    when (vm.tab) {
                        0 -> AlarmsScreen(
                            alarms = alarms,
                            onToggle = vm::toggle,
                            onOpen = { editId = it.id; screen = SCREEN_EDITOR },
                            onAdd = { editId = -1; screen = SCREEN_EDITOR },
                            onQuick = vm::quick,
                            onPin = vm::togglePin,
                            onDelete = vm::delete,
                            onReorder = vm::saveOrder,
                            onSettings = { screen = SCREEN_SETTINGS },
                            quickRequest = vm.quickRequest,
                            onQuickConsumed = { vm.quickRequest = false },
                        )
                        1 -> RemindersScreen(
                            reminders = reminders,
                            defaultMinutes = settings.reminderDefaultMinutes,
                            onOpen = { reminderId = it.id; screen = SCREEN_REMINDER_EDITOR },
                            onAdd = { reminderId = -1; screen = SCREEN_REMINDER_EDITOR },
                            onDelete = vm::deleteReminder,
                            onTurnOff = vm::turnOffReminder,
                            onSettings = { screen = SCREEN_SETTINGS },
                        )
                        2 -> StopwatchScreen(stopwatch, vm::swToggle, vm::swLap, vm::swReset)
                        else -> TimerScreen(
                            state = timer,
                            onStart = vm::timerStart,
                            onPause = vm::timerPause,
                            onResume = vm::timerResume,
                            onCancel = vm::timerCancel,
                            onAddMinute = vm::timerAddMinute,
                            onSavePresets = vm::savePresets,
                            onEnterPip = onEnterPip,
                        )
                    }
                    BottomPill(vm.tab, { vm.tab = it }, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }
}

@Composable
private fun BottomPill(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
    val icons: List<ImageVector> = listOf(Icons.Outlined.Alarm, Icons.Outlined.Event, Icons.Outlined.Timer, Icons.Outlined.HourglassBottom)
    Surface(
        modifier = modifier.navigationBarsPadding().padding(bottom = 16.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
    ) {
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            icons.forEachIndexed { i, icon ->
                Box(
                    Modifier.clip(CircleShape)
                        .background(if (i == selected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
                        .clickable { onSelect(i) }
                        .padding(horizontal = 22.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, tint = if (i == selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
