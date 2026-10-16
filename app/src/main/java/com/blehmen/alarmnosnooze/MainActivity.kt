package com.blehmen.alarmnosnooze

import android.Manifest
import android.app.NotificationManager
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.blehmen.alarmnosnooze.data.Phase
import com.blehmen.alarmnosnooze.ui.*

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_TAB = "tab"
        const val EXTRA_NEW_ALARM = "new_alarm"
        const val TAB_ALARMS = 0
        const val TAB_STOPWATCH = 1
        const val TAB_TIMER = 2
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
        if (i?.getBooleanExtra(EXTRA_NEW_ALARM, false) == true) { vm.tab = TAB_ALARMS; vm.newAlarmRequest = true }
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

private sealed interface Screen {
    data object Main : Screen
    data class Editor(val id: Int) : Screen
    data object Settings : Screen
}

private const val KIND_MAIN = 0
private const val KIND_EDITOR = 1
private const val KIND_SETTINGS = 2

@Composable
fun AppRoot(vm: MainViewModel, onEnterPip: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val alarms by vm.alarms.collectAsState()
    val timer by vm.timer.collectAsState()
    val stopwatch by vm.stopwatch.collectAsState()

    AppTheme(settings.themeMode, settings.accent) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            var kind by rememberSaveable { mutableStateOf(KIND_MAIN) }
            var editId by rememberSaveable { mutableStateOf(-1) }
            val target: Screen = when (kind) {
                KIND_EDITOR -> Screen.Editor(editId)
                KIND_SETTINGS -> Screen.Settings
                else -> Screen.Main
            }
            BackHandler(enabled = target != Screen.Main) { kind = KIND_MAIN }

            // The alarm widget's + button asks for the normal new-alarm editor.
            LaunchedEffect(vm.newAlarmRequest) {
                if (vm.newAlarmRequest) {
                    vm.newAlarmRequest = false
                    editId = -1
                    kind = KIND_EDITOR
                }
            }

            // Alarm editor and Settings slide in from the right and slide back out the same way on exit.
            AnimatedContent(
                targetState = target,
                transitionSpec = {
                    val deeper = targetState != Screen.Main
                    val enter: EnterTransition
                    val exit: ExitTransition
                    if (deeper) {
                        enter = slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(200))
                        exit = slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { -it / 4 } + fadeOut(tween(320))
                    } else {
                        enter = slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { -it / 4 } + fadeIn(tween(320))
                        exit = slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(220))
                    }
                    (enter togetherWith exit).also { it.targetContentZIndex = if (deeper) 1f else -1f }
                },
                label = "screens",
            ) { s ->
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    when (s) {
                        is Screen.Editor -> {
                            val initial = remember(s.id) { if (s.id == -1) vm.blank() else alarms.firstOrNull { it.id == s.id } }
                            if (initial != null) {
                                AlarmEditorScreen(
                                    initial = initial,
                                    isNew = s.id == -1,
                                    onSave = { vm.save(it); kind = KIND_MAIN },
                                    onCancel = { kind = KIND_MAIN },
                                    onDelete = { vm.delete(initial); kind = KIND_MAIN },
                                )
                            }
                        }
                        Screen.Settings -> SettingsScreen(vm) { kind = KIND_MAIN }
                        Screen.Main -> {
                            Box(Modifier.fillMaxSize()) {
                                AnimatedContent(
                                    targetState = vm.tab,
                                    transitionSpec = {
                                        val dir = if (targetState > initialState) 1 else -1
                                        (slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it * dir } + fadeIn(tween(250))) togetherWith
                                            (slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it * dir / 3 } + fadeOut(tween(180)))
                                    },
                                    label = "tabs",
                                ) { tab ->
                                    when (tab) {
                                        MainActivity.TAB_ALARMS -> AlarmsScreen(
                                            alarms = alarms,
                                            onToggle = vm::toggle,
                                            onOpen = { editId = it.id; kind = KIND_EDITOR },
                                            onAdd = { editId = -1; kind = KIND_EDITOR },
                                            onPin = vm::togglePin,
                                            onDelete = vm::delete,
                                            onReorder = vm::saveOrder,
                                            onSettings = { kind = KIND_SETTINGS },
                                        )
                                        MainActivity.TAB_STOPWATCH -> StopwatchScreen(stopwatch, vm::swToggle, vm::swLap, vm::swReset)
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
                                }
                                BottomPill(vm.tab, { vm.tab = it }, Modifier.align(Alignment.BottomCenter))
                                FullScreenPrompt()
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Without the "full-screen notifications" permission Android keeps the lock screen asleep when an alarm
 * rings. Sideloaded apps often start with it off on Android 14+, so ask for it up front.
 */
@Composable
private fun FullScreenPrompt() {
    if (Build.VERSION.SDK_INT < 34) return
    val ctx = LocalContext.current
    var show by rememberSaveable {
        mutableStateOf(!ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent())
    }
    if (!show) return
    AlertDialog(
        onDismissRequest = { show = false },
        title = { Text("Let alarms wake your phone") },
        text = { Text("Turn on full-screen notifications for this app so the alarm screen can light up and appear over the lock screen.") },
        confirmButton = {
            TextButton(onClick = {
                show = false
                try {
                    ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${ctx.packageName}")))
                } catch (_: Exception) {
                    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
                }
            }) { Text("Open settings") }
        },
        dismissButton = { TextButton(onClick = { show = false }) { Text("Not now") } },
    )
}

@Composable
private fun BottomPill(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
    val icons: List<ImageVector> = listOf(Icons.Outlined.Alarm, Icons.Outlined.Timer, Icons.Outlined.HourglassBottom)
    Surface(
        modifier = modifier.navigationBarsPadding().padding(bottom = 16.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
    ) {
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            icons.forEachIndexed { i, icon ->
                val on = i == selected
                val bg by animateColorAsState(if (on) MaterialTheme.colorScheme.primary else Color.Transparent, tween(250), label = "navBg")
                val fg by animateColorAsState(
                    if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    tween(250), label = "navFg",
                )
                Box(
                    Modifier.clip(CircleShape).background(bg).clickable { onSelect(i) }
                        .padding(horizontal = 26.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, null, tint = fg) }
            }
        }
    }
}
