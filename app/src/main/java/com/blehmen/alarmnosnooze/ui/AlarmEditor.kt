package com.blehmen.alarmnosnooze.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.IntentCompat
import com.blehmen.alarmnosnooze.data.Alarm
import com.blehmen.alarmnosnooze.engine.nextOccurrence
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private enum class EditorDialog { Snooze, Repeat, EndTime }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditorScreen(
    initial: Alarm,
    isNew: Boolean,
    onSave: (Alarm) -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val ctx = LocalContext.current
    val is24 = remember { DateFormat.is24HourFormat(ctx) }
    var a by remember { mutableStateOf(initial) }
    var dialog by remember { mutableStateOf<EditorDialog?>(null) }

    val soundName = remember(a.soundUri) {
        val uri = a.soundUri?.let { Uri.parse(it) } ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        try { RingtoneManager.getRingtone(ctx, uri)?.getTitle(ctx) ?: "Default" } catch (_: Exception) { "Default" }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val uri = r.data?.let { IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java) }
            a = a.copy(soundUri = uri?.toString())
        }
    }

    val dateText = if (a.repeatDays == 0) {
        val d = nextOccurrence(a.hour, a.minute, 0).toLocalDate()
        (if (d == LocalDate.now()) "Today" else "Tomorrow") + " - " + d.format(DateTimeFormatter.ofPattern("EEE, d MMM"))
    } else com.blehmen.alarmnosnooze.data.Days.summary(a.repeatDays)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(24.dp))
            TimeWheels(a.hour, a.minute, is24) { h, m -> a = a.copy(hour = h, minute = m) }
            Spacer(Modifier.height(16.dp))

            Surface(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 24.dp, bottom = 120.dp)
                ) {
                    Text(dateText, fontSize = 15.sp)
                    Spacer(Modifier.height(20.dp))
                    DayChips(a.repeatDays) { a = a.copy(repeatDays = it) }
                    Spacer(Modifier.height(16.dp))

                    TextField(
                        value = a.label,
                        onValueChange = { a = a.copy(label = it.take(40)) },
                        placeholder = { Text("Alarm name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        ),
                    )
                    Spacer(Modifier.height(8.dp))

                    SettingRow("Sound", soundName) {
                        picker.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            a.soundUri?.let { putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it)) }
                        })
                    }

                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text("Volume", fontSize = 18.sp)
                        Slider(value = a.volume / 100f, onValueChange = { a = a.copy(volume = (it * 100).toInt().coerceAtLeast(5)) })
                    }

                    ToggleRow("Vibration", null, a.vibrate, { a = a.copy(vibrate = it) })

                    ToggleRow("Gradual volume", if (a.rampEnabled) "Ramps up over ${a.rampSeconds} seconds" else null,
                        a.rampEnabled, { a = a.copy(rampEnabled = it) })
                    if (a.rampEnabled) {
                        Slider(
                            value = a.rampSeconds.toFloat(),
                            onValueChange = { a = a.copy(rampSeconds = it.toInt()) },
                            valueRange = 5f..120f,
                        )
                    }

                    ToggleRow(
                        "Snooze", if (a.snoozeEnabled) "${a.snoozeMinutes} minutes" else "Off",
                        a.snoozeEnabled,
                        { on -> a = a.copy(snoozeEnabled = on, autoRepeatEnabled = if (on) false else a.autoRepeatEnabled) },
                        onClick = { dialog = EditorDialog.Snooze },
                    )
                    ToggleRow(
                        "Auto repeat",
                        if (a.autoRepeatEnabled) "Every ${a.autoRepeatMinutes} minutes. Turn off in app to stop" else "Off",
                        a.autoRepeatEnabled,
                        { on -> a = a.copy(autoRepeatEnabled = on, snoozeEnabled = if (on) false else a.snoozeEnabled) },
                        onClick = { dialog = EditorDialog.Repeat },
                    )
                    if (a.autoRepeatEnabled) {
                        val sub = a.endMinutes?.let { "%02d:%02d".format(it / 60, it % 60) } ?: "Not set (uses max repeats)"
                        SettingRow("End time", sub) { dialog = EditorDialog.EndTime }
                    }

                    if (!isNew) {
                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = onDelete) { Text("Delete alarm", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCancel, modifier = Modifier.width(120.dp)) { Text("Cancel", fontWeight = FontWeight.Bold) }
                Box(Modifier.width(1.dp).height(28.dp).background(MaterialTheme.colorScheme.outline))
                TextButton(onClick = { onSave(a) }, modifier = Modifier.width(120.dp)) { Text("Save", fontWeight = FontWeight.Bold) }
            }
        }
    }

    when (dialog) {
        EditorDialog.Snooze -> DurationDialog("Snooze duration", a.snoozeMinutes, { dialog = null }) {
            a = a.copy(snoozeMinutes = it, snoozeEnabled = true, autoRepeatEnabled = false); dialog = null
        }
        EditorDialog.Repeat -> DurationDialog("Repeat every", a.autoRepeatMinutes, { dialog = null }) {
            a = a.copy(autoRepeatMinutes = it, autoRepeatEnabled = true, snoozeEnabled = false); dialog = null
        }
        EditorDialog.EndTime -> {
            val start = a.endMinutes ?: 8 * 60
            val st = rememberTimePickerState(start / 60, start % 60, is24)
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("End time") },
                text = { TimePicker(state = st) },
                confirmButton = { TextButton(onClick = { a = a.copy(endMinutes = st.hour * 60 + st.minute); dialog = null }) { Text("Set") } },
                dismissButton = { TextButton(onClick = { a = a.copy(endMinutes = null); dialog = null }) { Text("Clear") } },
            )
        }
        null -> {}
    }
}

@Composable
private fun DayChips(mask: Int, onChange: (Int) -> Unit) {
    val letters = listOf("M", "T", "W", "T", "F", "S", "S")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        letters.forEachIndexed { i, l ->
            val on = (mask and (1 shl i)) != 0
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onChange(mask xor (1 shl i)) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    l, fontSize = 16.sp,
                    color = when {
                        on -> MaterialTheme.colorScheme.onPrimary
                        i == 6 -> Color(0xFFE5472E)
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp)) {
        Text(title, fontSize = 18.sp)
        if (subtitle != null) Text(subtitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier.weight(1f).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 8.dp)
        ) {
            Text(title, fontSize = 18.sp)
            if (subtitle != null) Text(subtitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onClick != null) {
            Box(Modifier.padding(horizontal = 12.dp).width(1.dp).height(32.dp).background(MaterialTheme.colorScheme.outlineVariant))
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun DurationDialog(title: String, current: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val presets = listOf(2, 5, 10, 30)
    var custom by remember { mutableStateOf(current !in presets) }
    var text by remember { mutableStateOf(current.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                presets.forEach { m ->
                    RadioLine("$m minutes", !custom && current == m) { onPick(m) }
                }
                RadioLine("Custom", custom) { custom = true }
                if (custom) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.filter(Char::isDigit).take(3) },
                        suffix = { Text("min") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }
        },
        confirmButton = {
            if (custom) TextButton(onClick = { text.toIntOrNull()?.takeIf { it in 1..720 }?.let(onPick) }) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RadioLine(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}
