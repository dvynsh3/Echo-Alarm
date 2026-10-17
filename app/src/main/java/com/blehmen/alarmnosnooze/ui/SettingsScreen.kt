package com.blehmen.alarmnosnooze.ui

import android.app.Activity
import android.app.AlarmManager
import android.media.RingtoneManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.IntentCompat
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val s by vm.settings.collectAsState()

    // Re-check permission state whenever we return from a system settings page.
    var refresh by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    var maxDialog by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }

    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val uri = r.data?.let { IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java) }
            vm.setDefaultSound(uri?.toString())
        }
    }
    val toast: (String) -> Unit = { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportBackup(uri, toast)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingImport = uri
    }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        )
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp)) {

            Header("Appearance")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("System", "Light", "Dark").forEachIndexed { i, label ->
                    FilterChip(selected = s.themeMode == i, onClick = { vm.setTheme(i) }, label = { Text(label) })
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Accent colour", fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AccentPresets.forEach { c ->
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(Color(c)).clickable { vm.setAccent(c) },
                        contentAlignment = Alignment.Center,
                    ) { if (s.accent == c) Icon(Icons.Filled.Check, null, tint = Color.White) }
                }
            }
            var hex by remember(s.accent) { mutableStateOf("%06X".format(s.accent and 0xFFFFFF)) }
            OutlinedTextField(
                value = hex,
                onValueChange = { v ->
                    hex = v.filter { it.isDigit() || it.uppercaseChar() in 'A'..'F' }.take(6).uppercase()
                    if (hex.length == 6) vm.setAccent(0xFF000000.toInt() or hex.toInt(16))
                },
                prefix = { Text("#") },
                label = { Text("Custom hex") },
                singleLine = true,
                modifier = Modifier.padding(top = 12.dp),
            )

            Header("Alarm behaviour")
            Row(
                Modifier.fillMaxWidth().clickable { maxDialog = true }.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Max repeats", fontSize = 16.sp)
                    Text("Applies to auto-repeat alarms with no end time: ${s.maxRepeats}",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Resume repeating after reboot", fontSize = 16.sp)
                    Text("Off: ringing or repeating alarms are turned off after a restart, with a notice.",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = s.resumeAfterReboot, onCheckedChange = vm::setResume)
            }

            SwitchRow("Warning before deletion", "Ask before a trash button deletes an alarm", s.confirmDelete, vm::setConfirmDelete)
            SwitchRow("Bedtime reminder", "10:30 pm each night: what rings tomorrow morning, or that nothing does", s.bedtimeReminder, vm::setBedtimeReminder)
            SwitchRow("Upcoming alarm notice", "A notification with a live countdown 15 minutes before every alarm", s.upcomingNotice, vm::setUpcomingNotice)

            Header("Defaults for new alarms")
            val soundName = remember(s.defaultSoundUri) {
                val u = s.defaultSoundUri?.let { Uri.parse(it) } ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                try { RingtoneManager.getRingtone(ctx, u)?.getTitle(ctx) ?: "Default" } catch (_: Exception) { "Default" }
            }
            Column(Modifier.fillMaxWidth().clickable {
                soundPicker.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    s.defaultSoundUri?.let { putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it)) }
                })
            }.padding(vertical = 12.dp)) {
                Text("Sound", fontSize = 16.sp)
                Text(soundName, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            var defVol by remember(s.defaultVolume) { mutableIntStateOf(s.defaultVolume) }
            Text("Volume", fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
            Slider(
                value = defVol / 100f,
                onValueChange = { defVol = (it * 100).toInt().coerceAtLeast(5) },
                onValueChangeFinished = { vm.setDefaultVolume(defVol) },
            )
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Gradual volume", fontSize = 16.sp)
                    if (s.defaultRampEnabled) Text("Ramps up over ${s.defaultRampSeconds} seconds",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = s.defaultRampEnabled, onCheckedChange = vm::setDefaultRamp)
            }
            if (s.defaultRampEnabled) {
                var defRamp by remember(s.defaultRampSeconds) { mutableIntStateOf(s.defaultRampSeconds) }
                Slider(
                    value = defRamp.toFloat(),
                    onValueChange = { defRamp = it.toInt() },
                    onValueChangeFinished = { vm.setDefaultRampSeconds(defRamp) },
                    valueRange = 5f..120f,
                )
            }

            Header("Backup")
            Column(Modifier.fillMaxWidth().clickable { exportLauncher.launch("alarm-no-snooze-backup.json") }.padding(vertical = 12.dp)) {
                Text("Back up to file", fontSize = 16.sp)
                Text("Alarms, timer presets and all settings as JSON", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.fillMaxWidth().clickable { importLauncher.launch(arrayOf("*/*")) }.padding(vertical = 12.dp)) {
                Text("Restore from file", fontSize = 16.sp)
                Text("Replaces everything currently in the app", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Header("Reliability checklist")
            key(refresh) {
                val nm = ctx.getSystemService(NotificationManager::class.java)
                val am = ctx.getSystemService(AlarmManager::class.java)
                val pm = ctx.getSystemService(PowerManager::class.java)

                val notif = Build.VERSION.SDK_INT < 33 ||
                    ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) == 0
                val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
                val fsi = Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent()
                val battery = pm.isIgnoringBatteryOptimizations(ctx.packageName)

                CheckRow("Notifications", "Needed to show the ringing screen and notices", notif) {
                    ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                }
                CheckRow("Exact alarms", "Lets alarms ring on the exact minute", exact) {
                    if (Build.VERSION.SDK_INT >= 31) ctx.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
                }
                CheckRow("Full-screen alerts", "Shows the ringing screen over the lock screen", fsi) {
                    if (Build.VERSION.SDK_INT >= 34) ctx.startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${ctx.packageName}")))
                }
                CheckRow("Battery optimisation", "Exempt this app so Android doesn't restrict it", battery) {
                    try {
                        ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${ctx.packageName}")))
                    } catch (_: Exception) { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                }
                CheckRow("Auto-start / background activity", "Some brands (Xiaomi, Oppo, Vivo, Huawei, Samsung) kill background apps", null) {
                    openOemAutoStart(ctx)
                }
            }
        }
    }

    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Restore backup?") },
            text = { Text("This replaces all current alarms and settings with the contents of the file.") },
            confirmButton = {
                TextButton(onClick = { pendingImport = null; vm.importBackup(uri, toast) }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } },
        )
    }

    if (maxDialog) {
        var text by remember { mutableStateOf(s.maxRepeats.toString()) }
        AlertDialog(
            onDismissRequest = { maxDialog = false },
            title = { Text("Max repeats") },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it.filter(Char::isDigit).take(4) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(onClick = { text.toIntOrNull()?.takeIf { it >= 1 }?.let { vm.setMaxRepeats(it); maxDialog = false } }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { maxDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Header(text: String) {
    Text(text, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 28.dp, bottom = 10.dp))
}

/** ok == null means the state can't be detected; the row is just a shortcut. */
@Composable
private fun CheckRow(title: String, subtitle: String, ok: Boolean?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp)
            Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when (ok) {
            true -> Text("OK", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
            false -> Text("Fix", color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
            null -> Text("Open", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
    }
}

private fun openOemAutoStart(ctx: Context) {
    val candidates = listOf(
        "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
        "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        "com.samsung.android.lool" to "com.samsung.android.sm.ui.battery.BatteryActivity",
    )
    for ((pkg, cls) in candidates) {
        try {
            ctx.startActivity(Intent().setComponent(ComponentName(pkg, cls)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: Exception) { /* try next */ }
    }
    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = 16.sp)
            Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
